package com.jkapp.finance.asset

import kotlinx.coroutines.flow.Flow

interface AssetFirestoreRepository {
    fun getDailyAssets(): Flow<List<DailyAsset>>
    suspend fun upsertDailyAsset(asset: DailyAsset)
    suspend fun deleteDailyAsset(date: String)
}
