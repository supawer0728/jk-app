package com.jkapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.data.firestore.FirestoreRepository
import com.jkapp.data.firestore.FirestoreRepositoryImpl
import com.jkapp.data.model.DailyAssetInvestment
import com.jkapp.data.model.InvestmentItem
import com.jkapp.data.model.InvestmentItemMetrics
import com.jkapp.data.model.withProfitMetrics
import java.math.BigDecimal
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

// 투자종목 등록/수정 폼과 명의 탭이 함께 참조하는 고정 명의 목록 (DailyAsset의 ASSET_OWNERS와 달리 "공동"은 없다).
internal val INVESTMENT_OWNERS = listOf("전지훈", "권유경")

class DailyAssetInvestmentViewModel(
    private val repository: FirestoreRepository = FirestoreRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyAssetInvestmentUiState>(DailyAssetInvestmentUiState.Loading)
    val uiState: StateFlow<DailyAssetInvestmentUiState> = _uiState.asStateFlow()

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
    }

    // 개별 입력 다이얼로그는 날짜를 오늘로 기본값을 두되 자유롭게 고를 수 있게 했으므로, 저장 후
    // 방금 고른 날짜로 화면을 전환해 새로 추가한 항목이 바로 보이게 한다.
    fun addInvestment(date: String, owner: String, item: InvestmentItem) {
        mutateInvestments(date, owner, "투자 종목 저장에 실패했습니다") { it + item }
        selectDate(date)
    }

    // target과 완전히 일치하는 항목을 찾아 교체한다(리스트 index 대신 항목 내용으로 식별).
    fun updateInvestment(date: String, owner: String, target: InvestmentItem, item: InvestmentItem) {
        mutateInvestments(date, owner, "투자 종목 저장에 실패했습니다") { items ->
            val index = items.indexOf(target)
            check(index >= 0) { "수정하려는 투자 종목을 찾을 수 없습니다(다른 곳에서 이미 변경되었을 수 있습니다)" }
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

    // 구글시트 붙여넣기 텍스트를 파싱한다. owner는 붙여넣기 다이얼로그에서 선택한 명의로, 파싱된
    // 모든 종목에 공통 적용된다. 파싱 실패 행은 원본 값을 그대로 로그에 남겨 디버깅에 활용한다.
    fun parsePasteText(text: String, owner: String): List<ParsedInvestmentRow> {
        // 붙여넣기는 서식 없는 텍스트로만 전달되어 화면에서 원본 시트와 비교하기 어려우므로,
        // 원본 텍스트와 각 행의 파싱 결과(성공/실패 모두)를 로그로 남겨 어떤 값이 어떻게
        // 인식됐는지 logcat에서 확인할 수 있게 한다.
        Log.d(TAG, "투자 종목 붙여넣기 원본 텍스트(owner=$owner):\n$text")
        val result = parseInvestmentSheetPaste(text, owner)
        result.forEach { row ->
            if (row.error != null) {
                Log.w(TAG, "투자 종목 붙여넣기 파싱 실패: error=${row.error}, input=\"${row.rawLine}\"")
            } else {
                Log.d(TAG, "투자 종목 붙여넣기 파싱 성공: ${row.item}")
            }
        }
        return result
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

    companion object {
        private const val TAG = "DailyAssetInvestmentViewModel"

        fun factory(): ViewModelProvider.Factory =
            viewModelFactory { initializer { DailyAssetInvestmentViewModel() } }
    }
}
