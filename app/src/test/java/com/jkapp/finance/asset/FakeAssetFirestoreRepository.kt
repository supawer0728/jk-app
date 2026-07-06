package com.jkapp.finance.asset

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAssetFirestoreRepository : AssetFirestoreRepository {

    private val _dailyAssets = MutableStateFlow<List<DailyAsset>>(emptyList())

    var upsertDailyAssetError: Throwable? = null
    var deleteDailyAssetError: Throwable? = null

    // 테스트에서 실제 Firestore 네트워크 왕복(suspension)을 흉내내기 위한 훅.
    // 동시 호출 시 뮤텍스로 직렬화되는지 검증하는 데 사용한다.
    var onUpsertDailyAsset: (suspend () -> Unit)? = null

    fun setDailyAssets(dailyAssets: List<DailyAsset>) { _dailyAssets.value = dailyAssets }

    override fun getDailyAssets(): Flow<List<DailyAsset>> = _dailyAssets

    override suspend fun upsertDailyAsset(asset: DailyAsset) {
        upsertDailyAssetError?.let { throw it }
        onUpsertDailyAsset?.invoke()
        val existingIndex = _dailyAssets.value.indexOfFirst { it.date == asset.date }
        _dailyAssets.value = if (existingIndex >= 0) {
            _dailyAssets.value.mapIndexed { index, existing -> if (index == existingIndex) asset else existing }
        } else {
            _dailyAssets.value + asset
        }
    }

    override suspend fun deleteDailyAsset(date: String) {
        deleteDailyAssetError?.let { throw it }
        _dailyAssets.value = _dailyAssets.value.filter { it.date != date }
    }
}
