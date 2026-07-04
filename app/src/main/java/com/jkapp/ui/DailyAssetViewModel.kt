package com.jkapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.jkapp.data.firestore.FirestoreRepository
import com.jkapp.data.firestore.FirestoreRepositoryImpl
import com.jkapp.data.model.AssetItem
import com.jkapp.data.model.DailyAsset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DailyAssetViewModel(
    private val repository: FirestoreRepository = FirestoreRepositoryImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow<DailyAssetUiState>(DailyAssetUiState.Loading)
    val uiState: StateFlow<DailyAssetUiState> = _uiState.asStateFlow()

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
        fun factory(): ViewModelProvider.Factory =
            viewModelFactory { initializer { DailyAssetViewModel() } }
    }
}
