package com.jkapp.finance.investment

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.auth.AuthRepository
import com.jkapp.auth.FirebaseAuthRepository
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

// 투자종목 등록/수정 폼과 명의 탭이 함께 참조하는 고정 명의 목록 (DailyAsset의 ASSET_OWNERS와 달리 "공동"은 없다).
internal val INVESTMENT_OWNERS = listOf("전지훈", "권유경")

class DailyAssetInvestmentViewModel(
    private val repository: InvestmentFirestoreRepository = InvestmentFirestoreRepositoryImpl(),
    private val sheetRepository: InvestmentSheetRepository = InvestmentSheetRepository.NoOp,
    private val authRepository: AuthRepository = FirebaseAuthRepository(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyAssetInvestmentUiState>(DailyAssetInvestmentUiState.Loading)
    val uiState: StateFlow<DailyAssetInvestmentUiState> = _uiState.asStateFlow()

    // 구글시트에서 가져오기 흐름 상태(로딩/미리보기).
    private val _sheetImport = MutableStateFlow<InvestmentSheetImportState>(InvestmentSheetImportState.Idle)
    val sheetImport: StateFlow<InvestmentSheetImportState> = _sheetImport.asStateFlow()

    // 시트 접근 동의가 필요할 때 UI가 실행할 복구 인텐트. Drive/벤치마크 패턴과 동일하게 다룬다.
    private val _sheetAuthRecoveryIntent = MutableStateFlow<Intent?>(null)
    val sheetAuthRecoveryIntent: StateFlow<Intent?> = _sheetAuthRecoveryIntent.asStateFlow()

    // 저장/삭제 실패는 _uiState를 덮어쓰지 않는다(BenchmarkViewModel과 동일한 이유 — 이슈 #37 이전
    // DailyAssetViewModel처럼 uiState를 Error로 덮으면 Firestore 리스너가 재발행하기 전까지 목록이
    // 사라진 채 고착된다).
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _selectedOwner = MutableStateFlow(INVESTMENT_OWNERS.first())
    val selectedOwner: StateFlow<String> = _selectedOwner.asStateFlow()

    fun selectOwner(owner: String) {
        _selectedOwner.value = owner
    }

    private val _selectedDate = MutableStateFlow<String?>(null)
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    // 문서가 {date}_{owner} 단위로 분리되어 있으므로, 명의별로 데이터가 존재하는 날짜가 다를 수 있다.
    // 선택된 명의를 기준으로 필터링해, 날짜 네비게이터가 그 명의의 날짜만 보여주도록 한다.
    val availableDates: StateFlow<List<String>> = combine(uiState, selectedOwner) { state, owner ->
        (state as? DailyAssetInvestmentUiState.Success)?.investments
            ?.filter { it.owner == owner }
            ?.map { it.date }
            ?.sortedDescending()
            ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // selectedDate가 null이거나(초기 상태) 명의 전환으로 더 이상 유효하지 않게 되면(이슈 #37과 동일한
    // 패턴) 해당 명의의 가장 최신 날짜로 보정한다. 아직 데이터가 없는 새 날짜를 고른 경우는 그대로 둔다.
    val currentInvestment: StateFlow<DailyAssetInvestment?> = combine(
        uiState, selectedDate, selectedOwner,
    ) { state, date, owner ->
        if (state !is DailyAssetInvestmentUiState.Success) return@combine null
        val resolvedDate = date ?: state.investments.filter { it.owner == owner }.map { it.date }.maxOrNull()
        resolvedDate?.let { d -> state.investments.find { it.date == d && it.owner == owner } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 계좌(assetName)/카테고리 필터. 앱을 재시작하면 잊어도 되는 휘발성 필터라 SavedStateHandle 없는
    // 평범한 MutableStateFlow로 두며(DailyAssetViewModel의 _selectedOwners와 동일한 패턴), selectbox
    // 동작이라 각각 하나만 고를 수 있고 null은 "필터 없음(전체 표시)"을 의미한다.
    private val _selectedAssetNameFilter = MutableStateFlow<String?>(null)
    val selectedAssetNameFilter: StateFlow<String?> = _selectedAssetNameFilter.asStateFlow()

    fun selectAssetNameFilter(assetName: String?) {
        _selectedAssetNameFilter.value = assetName
    }

    private val _selectedCategoryFilter = MutableStateFlow<String?>(null)
    val selectedCategoryFilter: StateFlow<String?> = _selectedCategoryFilter.asStateFlow()

    fun selectCategoryFilter(category: String?) {
        _selectedCategoryFilter.value = category
    }

    // 필터 selectbox에 노출할 선택지는 필터링 전 currentInvestment 기준이라, 필터를 고른 뒤에도
    // 다른 선택지가 계속 보인다(선택지 목록 자체가 필터링되어 줄어들지 않음).
    val assetNameFilterOptions: StateFlow<List<String>> = currentInvestment
        .map { it?.investments.orEmpty().map { item -> item.assetName }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categoryFilterOptions: StateFlow<List<String>> = currentInvestment
        .map { it?.investments.orEmpty().map { item -> item.category }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 종목별 수익금(평가금액 - 매수금액)은 데이터가 실제로 바뀔 때만 계산되어 캐시된다. 컴포저블의
    // remember에 두면 탭을 오갈 때마다 컴포지션이 새로 생성되면서 매번 재계산되므로(이슈 #37과 동일한
    // 문제), 벤치마크 탭(rowMetrics)과 같은 방식으로 뷰모델 StateFlow로 옮긴다.
    val investmentRowMetrics: StateFlow<List<InvestmentItemMetrics>> = combine(
        currentInvestment, _selectedAssetNameFilter, _selectedCategoryFilter,
    ) { investment, assetName, category ->
        investment?.investments.orEmpty()
            .filter { (assetName == null || it.assetName == assetName) && (category == null || it.category == category) }
            .withProfitMetrics()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 종목이 많아지면 계좌(assetName) 단위로 묶어 봐야 해서, 화면에서 매번 다시 묶지 않도록
    // 여기서 한 번만 그룹화해 캐시한다(rowMetrics와 같은 이유). 헤더 순서가 매번 들쭉날쭉하지
    // 않도록 계좌 이름 기준으로 정렬한다.
    val groupedInvestmentRowMetrics: StateFlow<Map<String, List<InvestmentItemMetrics>>> = investmentRowMetrics
        .map { metrics -> metrics.groupBy { it.item.assetName }.toSortedMap() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // 명의와 상관없이 같은 날짜의 모든 투자 종목 평가금액 합계. Benchmark.currentAmount는 명의
    // 구분 없는 전체 포트폴리오 금액이라, 명의별로 필터링된 currentInvestment 대신 여기서 다시 계산한다.
    val selectedDateTotalValuationAmount: StateFlow<BigDecimal?> = combine(
        uiState, selectedDate,
    ) { state, date ->
        if (state !is DailyAssetInvestmentUiState.Success || date == null) return@combine null
        state.investments.filter { it.date == date }
            .flatMap { it.investments }
            .sumOf { it.valuationAmount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var dataJob: Job? = null
    private var sheetImportJob: Job? = null

    // add/update/delete 요청을 직렬화해, 서로 다른 요청이 같은 stale 스냅샷을 읽고
    // 상대방의 변경을 덮어쓰는 lost-update를 방지한다.
    private val investmentMutationMutex = Mutex()

    init {
        dataJob = viewModelScope.launch {
            repository.getDailyAssetInvestments()
                .map { DailyAssetInvestmentUiState.Success(it) as DailyAssetInvestmentUiState }
                .catch { e ->
                    emit(DailyAssetInvestmentUiState.Error("투자 종목 목록을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { state -> _uiState.value = state }
        }
        // 로그인된 구글 계정을 시트 저장소에 전달해, 이미 동의한 사용자는 계정 선택 없이 바로 읽는다.
        viewModelScope.launch {
            authRepository.observeCurrentUserEmail().collect { email ->
                email?.let { sheetRepository.setAccount(it) }
            }
        }
        // 사용자가 고른 날짜가 현재 명의의 목록에 없어졌으면(명의 전환, 데이터 삭제 등) 가장 최신
        // 날짜로 대체한다. availableDates가 바뀔 때만 반응해야, 아직 데이터가 없는 새 날짜를 고르는
        // 동작이 곧바로 최신 날짜로 되돌려지지 않는다.
        viewModelScope.launch {
            availableDates.collect { dates ->
                if (_selectedDate.value == null || _selectedDate.value !in dates) {
                    _selectedDate.value = dates.firstOrNull()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataJob?.cancel()
        sheetImportJob?.cancel()
    }

    // 개별 입력 다이얼로그는 날짜를 오늘로 기본값을 두되 자유롭게 고를 수 있게 했으므로, 저장 후
    // 방금 고른 날짜로 화면을 전환해 새로 추가한 항목이 바로 보이게 한다.
    fun addInvestment(date: String, owner: String, item: InvestmentItem) {
        mutateInvestments(date, owner, "투자 종목 저장에 실패했습니다") { items ->
            // (계좌, 카테고리, 투자종목)이 같은 항목이 이미 있으면 LazyColumn의 key가 중복되어
            // 크래시로 이어지므로(구글시트 붙여넣기의 duplicateKeys 검증과 동일한 이유), 여기서도 막는다.
            check(items.none { it.isSameInvestmentKey(item) }) { "이미 같은 계좌·카테고리·투자종목 조합이 존재합니다" }
            items + item
        }
        selectDate(date)
    }

    // target과 완전히 일치하는 항목을 찾아 교체한다(리스트 index 대신 항목 내용으로 식별).
    fun updateInvestment(date: String, owner: String, target: InvestmentItem, item: InvestmentItem) {
        mutateInvestments(date, owner, "투자 종목 저장에 실패했습니다") { items ->
            val index = items.indexOf(target)
            check(index >= 0) { "수정하려는 투자 종목을 찾을 수 없습니다(다른 곳에서 이미 변경되었을 수 있습니다)" }
            check(items.withIndex().none { (i, existing) -> i != index && existing.isSameInvestmentKey(item) }) {
                "이미 같은 계좌·카테고리·투자종목 조합이 존재합니다"
            }
            items.mapIndexed { i, existing -> if (i == index) item else existing }
        }
    }

    // target과 완전히 일치하는 항목을 찾아 삭제한다(리스트 index 대신 항목 내용으로 식별).
    fun deleteInvestment(date: String, owner: String, target: InvestmentItem) {
        mutateInvestments(date, owner, "투자 종목 삭제에 실패했습니다") { items ->
            check(target in items) { "삭제하려는 투자 종목을 찾을 수 없습니다(이미 삭제되었을 수 있습니다)" }
            items - target
        }
    }

    // 여러 종목을 하나의 upsert로 한 번에 삭제한다("전체 삭제"/"선택 삭제"가 공통으로 사용하는 메서드).
    // 결과가 비면 mutateInvestments가 문서 자체를 삭제하므로, "전체 삭제"는 현재 목록 전체를 넘기면 된다.
    fun deleteInvestments(date: String, owner: String, targets: List<InvestmentItem>) {
        if (targets.isEmpty()) return
        val targetSet = targets.toSet()
        mutateInvestments(date, owner, "투자 종목 삭제에 실패했습니다") { items -> items.filterNot { it in targetSet } }
    }

    // 고정된 원본 구글시트에서 명의별 블록(전지훈 H:N, 권유경 P:V)을 읽어 각 블록을 해당 명의로
    // 파싱한 뒤 미리보기 상태로 만든다. 접근 동의가 필요하면 복구 인텐트를 노출하고, 그 외 오류는
    // actionError로 안내한다(BenchmarkViewModel.importFromSheet와 동일한 흐름).
    fun importFromSheet() {
        if (_sheetImport.value == InvestmentSheetImportState.Loading) return
        sheetImportJob = viewModelScope.launch {
            _sheetImport.value = InvestmentSheetImportState.Loading
            // 응답이 지나치게 늦으면(네트워크/토큰 지연) 로딩에 갇히지 않도록 타임아웃을 둔다.
            // withTimeoutOrNull은 초과 시 예외 대신 null을 돌려주므로 취소(CancellationException)와 구분된다.
            runCatching { withTimeoutOrNull(SHEET_IMPORT_TIMEOUT_MS) { sheetRepository.readInvestmentBlocks() } }
                .onSuccess { blocks ->
                    if (blocks == null) {
                        _sheetImport.value = InvestmentSheetImportState.Idle
                        _actionError.value = "구글시트를 불러오지 못했습니다: 응답 시간이 초과되었습니다"
                        return@onSuccess
                    }
                    val importBlocks = blocks.map { block ->
                        val parsed = parseInvestmentRows(block.rows, block.owner)
                        parsed.filter { it.error != null }.forEach { row ->
                            Log.w(TAG, "투자 종목 시트 파싱 실패(owner=${block.owner}): error=${row.error}, input=\"${row.rawLine}\"")
                        }
                        InvestmentSheetImportBlock(owner = block.owner, rows = parsed)
                    }
                    _sheetImport.value = InvestmentSheetImportState.Preview(importBlocks)
                }
                .onFailure { e ->
                    // 사용자가 로딩을 취소하면 조용히 종료한다(상태는 dismissSheetImport가 이미 정리).
                    if (e is CancellationException) return@onFailure
                    _sheetImport.value = InvestmentSheetImportState.Idle
                    when (e) {
                        is InvestmentSheetAuthException -> _sheetAuthRecoveryIntent.value = e.recoveryIntent
                        else -> _actionError.value =
                            "구글시트를 불러오지 못했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                    }
                }
        }
    }

    // 계정 선택/동의 화면에서 돌아온 계정 이름을 시트 저장소에 반영한다.
    fun onSheetAccountSelected(accountName: String) {
        sheetRepository.setAccount(accountName)
    }

    fun clearSheetAuthRecoveryIntent() {
        _sheetAuthRecoveryIntent.value = null
    }

    // 미리보기에서 확인한 명의별 종목을 실행 시점 날짜(date)로 저장하고 미리보기를 닫는다.
    // 명의별 문서가 분리되어 있으므로 블록마다 importInvestments를 호출한다(각각 upsert/merge).
    fun confirmSheetImport(date: String) {
        val preview = _sheetImport.value as? InvestmentSheetImportState.Preview ?: return
        // importInvestments는 저장 후 해당 명의/날짜로 화면을 전환하지만, 두 명의를 함께 저장하는
        // 여기서는 마지막 블록 명의로 탭이 튀는 게 자연스럽지 않다(빈 블록이 섞이면 비결정적이기도 하다).
        // 사용자가 보고 있던 명의를 유지한 채 오늘 날짜만 보여주도록, 저장 후 선택 상태를 명시적으로 되돌린다.
        val targetOwner = _selectedOwner.value
        preview.blocks.forEach { block ->
            importInvestments(date, block.owner, block.rows.mapNotNull { it.item })
        }
        selectOwner(targetOwner)
        selectDate(date)
        _sheetImport.value = InvestmentSheetImportState.Idle
    }

    fun dismissSheetImport() {
        sheetImportJob?.cancel()
        _sheetImport.value = InvestmentSheetImportState.Idle
    }

    // (계좌, 카테고리, 투자종목)이 같은 항목은 시세/보유수량/매수금액(통화 포함)을 갱신하고,
    // 없는 항목은 새로 추가한다.
    fun importInvestments(date: String, owner: String, items: List<InvestmentItem>) {
        if (items.isEmpty()) return
        mutateInvestments(date, owner, "투자 종목 저장에 실패했습니다") { existing ->
            val merged = existing.toMutableList()
            items.forEach { imported ->
                val index = merged.indexOfFirst {
                    it.assetName == imported.assetName && it.category == imported.category && it.investmentName == imported.investmentName
                }
                if (index >= 0) {
                    val current = merged[index]
                    merged[index] = current.copy(
                        pricePerShare = imported.pricePerShare,
                        valuationAmount = imported.valuationAmount,
                        quantity = imported.quantity,
                        purchaseAmount = imported.purchaseAmount,
                    )
                } else {
                    merged.add(imported)
                }
            }
            merged
        }
        // 붙여넣기 다이얼로그에서 고른 날짜/명의로 화면을 전환해, 저장한 내용이 바로 보이게 한다.
        selectOwner(owner)
        selectDate(date)
    }

    fun consumeActionError() {
        _actionError.value = null
    }

    // date+owner 문서에 대한 투자 종목 목록 변경을 뮤텍스로 직렬화해 read-modify-write 사이에
    // 다른 변경이 끼어들지 않게 한다. 결과가 비면 문서 자체를 삭제하고, 그렇지 않으면 upsert한다.
    private fun mutateInvestments(
        date: String,
        owner: String,
        errorMessage: String,
        transform: (List<InvestmentItem>) -> List<InvestmentItem>,
    ) {
        viewModelScope.launch {
            investmentMutationMutex.withLock {
                runCatching {
                    val current = findInvestment(date, owner)
                    val updated = transform(current?.investments.orEmpty())
                    if (updated.isEmpty()) {
                        repository.deleteDailyAssetInvestment(date, owner)
                    } else {
                        repository.upsertDailyAssetInvestment(
                            DailyAssetInvestment(date = date, owner = owner, investments = updated)
                        )
                    }
                }.onFailure { e ->
                    _actionError.value = "$errorMessage: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
            }
        }
    }

    private fun findInvestment(date: String, owner: String): DailyAssetInvestment? =
        (uiState.value as? DailyAssetInvestmentUiState.Success)?.investments?.find { it.date == date && it.owner == owner }

    private fun InvestmentItem.isSameInvestmentKey(other: InvestmentItem): Boolean =
        assetName == other.assetName && category == other.category && investmentName == other.investmentName

    companion object {
        private const val TAG = "DailyAssetInvestmentViewModel"
        private const val SHEET_IMPORT_TIMEOUT_MS = 30_000L

        fun factory(
            sheetRepository: InvestmentSheetRepository = InvestmentSheetRepository.NoOp,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { DailyAssetInvestmentViewModel(sheetRepository = sheetRepository) } }
    }
}
