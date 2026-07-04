package com.jkapp.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.data.firestore.FirestoreRepository
import com.jkapp.data.firestore.FirestoreRepositoryImpl
import com.jkapp.data.model.AssetItem
import com.jkapp.data.model.DailyAsset
import java.math.BigDecimal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DailyAssetViewModel(
    private val repository: FirestoreRepository = FirestoreRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyAssetUiState>(DailyAssetUiState.Loading)
    val uiState: StateFlow<DailyAssetUiState> = _uiState.asStateFlow()

    // 가장 최신 날짜의 자산 중 숨김 처리되지 않은 항목의 합계(순자산). 데이터가 없으면 null.
    val netWorth: StateFlow<BigDecimal?> = uiState
        .map { state ->
            (state as? DailyAssetUiState.Success)?.dailyAssets
                ?.maxByOrNull { it.date }
                ?.assets
                ?.filterNot { it.hidden }
                ?.sumOf { it.amount ?: BigDecimal.ZERO }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var dataJob: Job? = null

    init {
        dataJob = viewModelScope.launch {
            repository.getDailyAssets()
                .map { DailyAssetUiState.Success(it) as DailyAssetUiState }
                .catch { e ->
                    emit(DailyAssetUiState.Error("자산 목록을 불러오는 중 오류가 발생했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"))
                }
                .collect { state -> _uiState.value = state }
        }
    }

    override fun onCleared() {
        super.onCleared()
        dataJob?.cancel()
    }

    fun addAsset(date: String, item: AssetItem) {
        updateAssetList(date) { it + item }
    }

    fun updateAsset(date: String, index: Int, item: AssetItem) {
        updateAssetList(date) { assets ->
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
        updateAssetList(date) { existing ->
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

    fun deleteAsset(date: String, index: Int) {
        viewModelScope.launch {
            val current = currentDailyAsset(date) ?: return@launch
            val updated = current.assets.filterIndexed { i, _ -> i != index }
            runCatching {
                if (updated.isEmpty()) {
                    repository.deleteDailyAsset(date)
                } else {
                    repository.upsertDailyAsset(current.copy(assets = updated))
                }
            }.onFailure { e ->
                _uiState.value = DailyAssetUiState.Error(
                    "자산 삭제에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    private fun updateAssetList(date: String, transform: (List<AssetItem>) -> List<AssetItem>) {
        viewModelScope.launch {
            val current = currentDailyAsset(date)
            val updatedAssets = transform(current?.assets.orEmpty())
            runCatching {
                repository.upsertDailyAsset(DailyAsset(firestoreId = date, date = date, assets = updatedAssets))
            }.onFailure { e ->
                _uiState.value = DailyAssetUiState.Error(
                    "자산 저장에 실패했습니다: ${e.localizedMessage ?: "알 수 없는 오류"}"
                )
            }
        }
    }

    private fun currentDailyAsset(date: String): DailyAsset? =
        (uiState.value as? DailyAssetUiState.Success)?.dailyAssets?.find { it.date == date }

    companion object {
        private const val TAG = "DailyAssetViewModel"

        fun factory(): ViewModelProvider.Factory =
            viewModelFactory { initializer { DailyAssetViewModel() } }
    }
}
