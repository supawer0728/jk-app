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
import com.jkapp.common.todayDate
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

// 투자종목 등록/수정 폼에서 참조하는 고정 명의 목록 (DailyAsset의 ASSET_OWNERS와 달리 "공동"은 없다).
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

    // 다중 선택 필터. 빈 Set은 "전체(제한 없음)".
    private val _filter = MutableStateFlow(InvestmentFilter())
    val filter: StateFlow<InvestmentFilter> = _filter.asStateFlow()

    fun applyFilter(filter: InvestmentFilter) {
        _filter.value = filter
    }

    fun clearFilter() {
        _filter.value = InvestmentFilter()
    }

    // 오늘 이하 전체 명의 데이터 중 가장 최신 날짜 1개. 날짜 네비게이터·명의 탭을 대체한다.
    val latestDate: StateFlow<String?> = uiState
        .map { state ->
            if (state !is DailyAssetInvestmentUiState.Success) return@map null
            val today = todayDate()
            state.investments.filter { it.date <= today }.mapNotNull { it.date.takeIf { d -> d.isNotBlank() } }.maxOrNull()
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // latestDate 기준 전체 명의 (owner, InvestmentItem) 쌍 목록. 필터 선택지 원본.
    val latestOwnerItemPairs: StateFlow<List<Pair<String, InvestmentItem>>> = combine(
        uiState, latestDate,
    ) { state, date ->
        if (state !is DailyAssetInvestmentUiState.Success || date == null) return@combine emptyList()
        state.investments
            .filter { it.date == date }
            .flatMap { doc -> doc.investments.map { item -> doc.owner to item } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 필터 모달 선택지 — 필터링 전 latestDate 전체 데이터 기준(선택지 자체가 필터로 줄어들지 않음).
    val ownerFilterOptions: StateFlow<List<String>> = latestOwnerItemPairs
        .map { pairs -> pairs.map { it.first }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val accountFilterOptions: StateFlow<List<String>> = latestOwnerItemPairs
        .map { pairs -> pairs.map { it.second.assetName }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categoryFilterOptions: StateFlow<List<String>> = latestOwnerItemPairs
        .map { pairs -> pairs.map { it.second.category }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stockNameFilterOptions: StateFlow<List<String>> = latestOwnerItemPairs
        .map { pairs -> pairs.map { it.second.investmentName }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 필터 적용 후 표시할 (owner, InvestmentItem) 쌍 목록.
    private val filteredOwnerItemPairs: StateFlow<List<Pair<String, InvestmentItem>>> = combine(
        latestOwnerItemPairs, _filter,
    ) { pairs, filter ->
        if (filter.isEmpty) pairs
        else pairs.filter { (owner, item) -> filter.matches(owner, item) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 종목별 수익금(평가금액 - 매수금액)은 데이터가 실제로 바뀔 때만 계산되어 캐시된다.
    val investmentRowMetrics: StateFlow<List<InvestmentItemMetrics>> = filteredOwnerItemPairs
        .map { pairs -> pairs.map { it.second }.withProfitMetrics() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 계좌(assetName) 단위로 묶어 화면에서 매번 다시 묶지 않도록 여기서 한 번만 그룹화해 캐시한다.
    // 명의 탭을 제거하고 전체 명의를 통합 표시하므로, 화면에서 수정·삭제 시 owner를 알 수 있도록
    // (owner, InvestmentItemMetrics) 쌍으로 그룹화한다.
    val groupedInvestmentRowMetrics: StateFlow<Map<String, List<Pair<String, InvestmentItemMetrics>>>> =
        filteredOwnerItemPairs
            .map { pairs ->
                pairs.map { (owner, item) ->
                    owner to InvestmentItemMetrics(item = item, profit = item.valuationAmount - item.purchaseAmount.amount)
                }.groupBy { it.second.item.assetName }.toSortedMap()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    // 필터 적용 후 표시 중인 전체 (owner, InvestmentItem) 쌍. "전체 삭제"가 현재 화면의 종목들을
    // owner별로 삭제하는 데 쓴다.
    val filteredOwnerItemsForDelete: StateFlow<List<Pair<String, InvestmentItem>>> = filteredOwnerItemPairs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // latestDate 기준 모든 명의 종목 평가금액 합계. 벤치마크(전체 포트폴리오 금액) 비교용.
    val latestDateTotalValuationAmount: StateFlow<BigDecimal?> = latestOwnerItemPairs
        .map { pairs ->
            if (pairs.isEmpty()) null
            else pairs.sumOf { it.second.valuationAmount }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
    fun confirmSheetImport(date: String) {
        val preview = _sheetImport.value as? InvestmentSheetImportState.Preview ?: return
        preview.blocks.forEach { block ->
            importInvestments(date, block.owner, block.rows.mapNotNull { it.item })
        }
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
