package com.jkapp.finance.asset

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.diary.DiaryViewModel
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 자산 등록/수정 폼과 명의 필터 옵션 계산(ownerFilterOptions)이 함께 참조하는 고정 명의 목록.
internal val ASSET_OWNERS = listOf("전지훈", "권유경", "공동")

class DailyAssetViewModel(
    private val repository: AssetFirestoreRepository = AssetFirestoreRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyAssetUiState>(DailyAssetUiState.Loading)
    val uiState: StateFlow<DailyAssetUiState> = _uiState.asStateFlow()

    // 오늘 혹은 그보다 가까운 과거 날짜 중 가장 최신인 자산의, 숨김 처리되지 않은 항목 합계(순자산).
    // 데이터가 없거나 전부 숨김이면 null. 미래 날짜로 잘못 입력된 항목은 제외한다.
    val netWorth: StateFlow<BigDecimal?> = uiState
        .map { state ->
            (state as? DailyAssetUiState.Success)?.dailyAssets
                ?.let { DiaryViewModel.latestNotFuture(it) { asset -> asset.date } }
                ?.assets
                ?.filterNot { it.hidden }
                ?.takeIf { it.isNotEmpty() }
                ?.sumOf { it.amount ?: BigDecimal.ZERO }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 아래 파생 State들은 원래 AssetScreen.kt의 DailyAssetTab 컴포저블 remember 안에 있었다.
    // MainScreen의 탭 전환이 when(selectedTab) 단순 분기라 다른 탭에 갔다가 돌아오면 컴포지션이
    // 통째로 새로 생성되어 remember가 초기화되고 매번 재계산됐다(이슈 #37). 벤치마크 탭(rowMetrics)과
    // 동일하게 뷰모델 StateFlow로 옮겨, 데이터가 실제로 바뀔 때만 재계산되도록 한다.
    val availableDates: StateFlow<List<String>> = uiState
        .map { state -> (state as? DailyAssetUiState.Success)?.dailyAssets?.map { it.date }?.sortedDescending() ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // _selectedDate/_selectedOwners/_showHidden는 SavedStateHandle 없는 평범한 MutableStateFlow라
    // process death 후 복원 시 기본값으로 초기화된다(기존에는 AssetScreen.kt의 rememberSaveable로
    // 유지됐음). DiaryViewModel의 _selectedTypeIds/_selectedYearMonth도 이 프로젝트에서 이미 같은
    // 패턴이라 이 PR 스코프에서는 현행을 유지하고, SavedStateHandle 도입은 별도 이슈로 다룬다.
    private val _selectedDate = MutableStateFlow<String?>(null)

    // selectedDate를 availableDates와 combine해서 매번 in-list 여부로 걸러내면, 사용자가
    // 아직 자산이 없는 새 날짜를 골라 등록하려는 흐름(날짜 선택 다이얼로그 → FAB로 추가)까지
    // 막아버린다. availableDates가 실제로 바뀔 때만(데이터 삭제 등) 보정하도록 별도 collector로
    // 처리하고, selectedDate 자체는 _selectedDate를 그대로 노출한다.
    val selectedDate: StateFlow<String?> = _selectedDate.asStateFlow()

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    private val _selectedOwners = MutableStateFlow<Set<String>>(emptySet())
    val selectedOwners: StateFlow<Set<String>> = _selectedOwners.asStateFlow()

    fun toggleOwnerFilter(owner: String) {
        _selectedOwners.update { if (owner in it) it - owner else it + owner }
    }

    fun clearOwnerFilter() {
        _selectedOwners.value = emptySet()
    }

    private val _showHidden = MutableStateFlow(false)
    val showHidden: StateFlow<Boolean> = _showHidden.asStateFlow()

    fun toggleShowHidden() {
        _showHidden.update { !it }
    }

    // selectedDate가 null -> 실제 날짜로의 보정은 availableDates를 구독하는 별도 collector(init 참고)
    // 에서 비동기로 이뤄진다. 콜드 스타트에서 uiState가 Loading -> Success로 바뀌는 프레임에 이 combine이
    // 그 보정보다 먼저 반응하면 (Success, null) 조합으로 currentDailyAsset이 한 프레임 null이 될 수
    // 있어, date가 null인 경우에 한해 여기서도 동일한 fallback을 즉시 계산해 그 프레임을 없앤다.
    // (date가 non-null인데 목록에 없는 경우는 아직 자산이 없는 새 날짜를 고른 것이라 그대로 null을
    // 유지해야 한다 — 위 selectedDate 주석 참고.) 실제 조회는 findDailyAsset을 재사용해 조회 로직이
    // 두 곳에 중복되지 않게 한다.
    val currentDailyAsset: StateFlow<DailyAsset?> = combine(uiState, selectedDate) { state, date ->
        if (state !is DailyAssetUiState.Success) return@combine null
        val resolvedDate = date ?: state.dailyAssets.map { it.date }.maxOrNull()
        resolvedDate?.let(::findDailyAsset)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // 필터 칩은 고정된 ASSET_OWNERS에 더해, 과거 데이터 등으로 그 외의 명의 값이 존재하면 함께 노출한다.
    val ownerFilterOptions: StateFlow<List<String>> = currentDailyAsset
        .map { asset -> ASSET_OWNERS + asset?.assets.orEmpty().map { it.owner }.filter { it !in ASSET_OWNERS }.distinct() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ASSET_OWNERS)

    val groupedAssets: StateFlow<Map<String, List<IndexedValue<AssetItem>>>> = combine(
        currentDailyAsset, _selectedOwners, _showHidden,
    ) { asset, owners, showHidden ->
        asset?.assets.orEmpty()
            .withIndex()
            .filter { (owners.isEmpty() || it.value.owner in owners) && (showHidden || !it.value.hidden) }
            .groupBy({ it.value.owner }, { it })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private var dataJob: Job? = null

    // add/update/delete/import 요청을 직렬화해, 서로 다른 요청이 같은 stale 스냅샷을 읽고
    // 상대방의 변경을 덮어쓰는 lost-update를 방지한다.
    private val assetMutationMutex = Mutex()

    init {
        dataJob = viewModelScope.launch {
            repository.getDailyAssets()
                .map { DailyAssetUiState.Success(it) as DailyAssetUiState }
                .catch { e ->
                    emit(DailyAssetUiState.Error("자산 목록을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { state -> _uiState.value = state }
        }
        // 사용자가 고른 날짜가 목록에 없어졌으면(데이터 삭제 등) 가장 최신 날짜로 대체한다.
        // availableDates가 바뀔 때만 반응해야, 아직 자산이 없는 새 날짜를 고르는 동작이
        // 곧바로 최신 날짜로 되돌려지지 않는다.
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

    fun addAsset(date: String, item: AssetItem) {
        mutateAssets(date, "자산 저장에 실패했습니다") { it + item }
    }

    // target과 완전히 일치하는 항목을 찾아 교체한다(리스트 index 대신 항목 내용으로 식별).
    // 다이얼로그가 열려 있는 동안 목록 순서가 바뀌어도(다른 기기의 동시 수정 등) 엉뚱한 항목이 바뀌지 않는다.
    fun updateAsset(date: String, target: AssetItem, item: AssetItem) {
        mutateAssets(date, "자산 저장에 실패했습니다") { assets ->
            val index = assets.indexOf(target)
            check(index >= 0) { "수정하려는 자산을 찾을 수 없습니다(다른 곳에서 이미 변경되었을 수 있습니다)" }
            assets.mapIndexed { i, existing -> if (i == index) item else existing }
        }
    }

    // 구글시트 붙여넣기 텍스트를 파싱한다. 파싱 실패 행은 원본 값을 그대로 로그에 남겨 디버깅에 활용한다.
    fun parsePasteText(text: String, hasHeader: Boolean): List<ParsedAssetRow> {
        val result = parseGoogleSheetPaste(text, hasHeader)
        result.filter { it.error != null }.forEach { row ->
            Log.w(TAG, "구글시트 붙여넣기 파싱 실패: error=${row.error}, input=\"${row.rawLine}\"")
        }
        return result
    }

    // 이름+명의가 같은 항목은 필드 단위로 갱신하고, 없는 항목은 새로 추가한다(구글시트 붙여넣기용).
    // 이름이 같아도 명의가 다르면 별개의 자산으로 취급한다.
    // 붙여넣기 데이터에 없거나 사용자가 직접 관리하는 필드(card, hidden)는 기존 값을 그대로 유지해,
    // 재붙여넣기로 개별 입력/수정한 값이 지워지지 않게 한다.
    fun importAssets(date: String, items: List<AssetItem>) {
        mutateAssets(date, "자산 저장에 실패했습니다") { existing ->
            val merged = existing.toMutableList()
            items.forEach { imported ->
                val index = merged.indexOfFirst { it.name == imported.name && it.owner == imported.owner }
                if (index >= 0) {
                    val current = merged[index]
                    merged[index] = current.copy(
                        owner = imported.owner,
                        institution = imported.institution,
                        accountNumber = imported.accountNumber,
                        amount = imported.amount,
                    )
                } else {
                    merged.add(imported)
                }
            }
            merged
        }
    }

    // target과 완전히 일치하는 항목을 찾아 삭제한다(리스트 index 대신 항목 내용으로 식별).
    fun deleteAsset(date: String, target: AssetItem) {
        mutateAssets(date, "자산 삭제에 실패했습니다") { assets ->
            check(target in assets) { "삭제하려는 자산을 찾을 수 없습니다(이미 삭제되었을 수 있습니다)" }
            assets - target
        }
    }

    // date에 대한 자산 목록 변경을 뮤텍스로 직렬화해 read-modify-write 사이에 다른 변경이 끼어들지 않게 한다.
    // 결과가 비면 문서 자체를 삭제하고, 그렇지 않으면 upsert한다.
    private fun mutateAssets(date: String, errorMessage: String, transform: (List<AssetItem>) -> List<AssetItem>) {
        viewModelScope.launch {
            assetMutationMutex.withLock {
                runCatching {
                    val current = findDailyAsset(date)
                    val updated = transform(current?.assets.orEmpty())
                    if (updated.isEmpty()) {
                        repository.deleteDailyAsset(date)
                    } else {
                        repository.upsertDailyAsset(DailyAsset(firestoreId = date, date = date, assets = updated))
                    }
                }.onFailure { e ->
                    _uiState.value = DailyAssetUiState.Error(
                        "$errorMessage: ${e.localizedMessage ?: "알 수 없는 오류"}"
                    )
                }
            }
        }
    }

    private fun findDailyAsset(date: String): DailyAsset? =
        (uiState.value as? DailyAssetUiState.Success)?.dailyAssets?.find { it.date == date }

    companion object {
        private const val TAG = "DailyAssetViewModel"

        fun factory(): ViewModelProvider.Factory =
            viewModelFactory { initializer { DailyAssetViewModel() } }
    }
}
