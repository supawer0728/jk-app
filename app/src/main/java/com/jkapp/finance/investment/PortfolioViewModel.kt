package com.jkapp.finance.investment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState
    data class Success(val portfolios: List<Portfolio>) : PortfolioUiState
    data class Error(val message: String) : PortfolioUiState
}

class PortfolioViewModel(
    private val portfolioRepository: PortfolioFirestoreRepository = PortfolioFirestoreRepositoryImpl(),
    private val investmentRepository: InvestmentFirestoreRepository = InvestmentFirestoreRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Loading)
    val uiState: StateFlow<PortfolioUiState> = _uiState.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    // 현재 선택된 포트폴리오 ID. null이면 첫 번째 포트폴리오를 표시하거나 빈 상태.
    private val _selectedPortfolioId = MutableStateFlow<String?>(null)
    val selectedPortfolioId: StateFlow<String?> = _selectedPortfolioId.asStateFlow()

    // 투자 종목 전체(latestDate 기준). 파이 차트 계산 원본.
    private val _investmentUiState = MutableStateFlow<DailyAssetInvestmentUiState>(
        DailyAssetInvestmentUiState.Loading
    )

    // latestDate 기준 (owner, InvestmentItem) 쌍 목록. 포트폴리오 파이 차트 계산에 사용.
    val latestOwnerItemPairs: StateFlow<List<Pair<String, InvestmentItem>>> = _investmentUiState
        .map { state ->
            if (state !is DailyAssetInvestmentUiState.Success) return@map emptyList()
            val today = com.jkapp.common.todayDate()
            val latestDate = state.investments
                .filter { it.date <= today }
                .mapNotNull { it.date.takeIf { d -> d.isNotBlank() } }
                .maxOrNull() ?: return@map emptyList()
            state.investments
                .filter { it.date == latestDate }
                .flatMap { doc -> doc.investments.map { item -> doc.owner to item } }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 그룹 조건 입력 다이얼로그의 칩 선택지 — 최신 날짜 전체 명의 데이터에서 도출한다.
    // 소유주는 고정 집합(INVESTMENT_OWNERS)이라 화면에서 직접 쓰고, 여기서는 나머지 축만 노출한다.
    val accountOptions: StateFlow<List<String>> = latestOwnerItemPairs
        .map { pairs -> pairs.map { it.second.assetName }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categoryOptions: StateFlow<List<String>> = latestOwnerItemPairs
        .map { pairs -> pairs.map { it.second.category }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val stockNameOptions: StateFlow<List<String>> = latestOwnerItemPairs
        .map { pairs -> pairs.map { it.second.investmentName }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // 현재 선택된 포트폴리오.
    val selectedPortfolio: StateFlow<Portfolio?> = combine(
        uiState, _selectedPortfolioId,
    ) { state, selectedId ->
        if (state !is PortfolioUiState.Success) return@combine null
        if (selectedId != null) {
            state.portfolios.find { it.firestoreId == selectedId }
        } else {
            state.portfolios.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            portfolioRepository.getPortfolios()
                .map { PortfolioUiState.Success(it) as PortfolioUiState }
                .catch { e ->
                    emit(PortfolioUiState.Error("포트폴리오를 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { _uiState.value = it }
        }
        viewModelScope.launch {
            investmentRepository.getDailyAssetInvestments()
                .map { DailyAssetInvestmentUiState.Success(it) as DailyAssetInvestmentUiState }
                .catch { e ->
                    emit(DailyAssetInvestmentUiState.Error("투자 종목을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { _investmentUiState.value = it }
        }
    }

    fun selectPortfolio(firestoreId: String) {
        _selectedPortfolioId.value = firestoreId
    }

    /**
     * 드래그&드롭으로 정해진 새 순서(포트폴리오 firestoreId 목록)를 반영한다.
     *
     * 새 순서대로 order=index를 재부여하되, 값이 실제로 바뀌는 문서만 부분 업데이트한다(최초 재정렬 시
     * 전부 null이므로 전체가 대상, 이후엔 이동에 영향받은 문서만). 순서가 그대로면 아무 것도 쓰지 않는다.
     */
    fun reorderPortfolios(orderedIds: List<String>) {
        val state = _uiState.value as? PortfolioUiState.Success ?: return
        val byId = state.portfolios.associateBy { it.firestoreId }
        val reordered = orderedIds.mapNotNull { byId[it] }
        val updates = computePortfolioOrderUpdates(reordered)
        if (updates.isEmpty()) return
        viewModelScope.launch {
            runCatching { portfolioRepository.updatePortfolioOrders(updates) }
                .onFailure { e ->
                    _actionError.value = "포트폴리오 순서 변경에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    /**
     * 포트폴리오를 저장한다. 그룹 targetRatio 합이 100이 아니면 저장하지 않고 actionError를 설정한다.
     * 저장 시 그룹에는 목록 위치대로 order를 부여한다(사용자가 직접 입력하지 않는 순서 값).
     */
    fun savePortfolio(portfolio: Portfolio) {
        val error = validatePortfolioGroups(portfolio.groups)
        if (error != null) {
            _actionError.value = error
            return
        }
        val toSave = portfolio.copy(
            groups = portfolio.groups.mapIndexed { index, group -> group.copy(order = index) },
        )
        viewModelScope.launch {
            runCatching { portfolioRepository.upsertPortfolio(toSave) }
                .onSuccess { newId ->
                    // 저장 후 새로 생성된 문서를 자동 선택한다.
                    _selectedPortfolioId.value = newId
                }
                .onFailure { e ->
                    _actionError.value = "포트폴리오 저장에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    fun deletePortfolio(firestoreId: String) {
        viewModelScope.launch {
            runCatching { portfolioRepository.deletePortfolio(firestoreId) }
                .onFailure { e ->
                    _actionError.value = "포트폴리오 삭제에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                }
        }
    }

    fun consumeActionError() {
        _actionError.value = null
    }
}
