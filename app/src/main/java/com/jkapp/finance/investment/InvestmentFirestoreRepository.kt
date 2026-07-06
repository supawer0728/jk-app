package com.jkapp.finance.investment

import kotlinx.coroutines.flow.Flow

interface InvestmentFirestoreRepository {
    fun getDailyAssetInvestments(): Flow<List<DailyAssetInvestment>>
    suspend fun upsertDailyAssetInvestment(investment: DailyAssetInvestment)
    suspend fun deleteDailyAssetInvestment(date: String, owner: String)
}
