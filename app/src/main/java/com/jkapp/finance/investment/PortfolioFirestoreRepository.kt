package com.jkapp.finance.investment

import kotlinx.coroutines.flow.Flow

interface PortfolioFirestoreRepository {
    fun getPortfolios(): Flow<List<Portfolio>>
    suspend fun upsertPortfolio(portfolio: Portfolio): String
    suspend fun deletePortfolio(firestoreId: String)
}
