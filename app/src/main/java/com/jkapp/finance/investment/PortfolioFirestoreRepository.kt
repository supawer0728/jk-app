package com.jkapp.finance.investment

import kotlinx.coroutines.flow.Flow

interface PortfolioFirestoreRepository {
    fun getPortfolios(): Flow<List<Portfolio>>
    suspend fun upsertPortfolio(portfolio: Portfolio): String

    /**
     * 여러 포트폴리오의 `order` 필드만 batch로 부분 업데이트한다.
     * @param orders firestoreId → 새 order 값. 값이 실제로 바뀌는 문서만 전달한다.
     */
    suspend fun updatePortfolioOrders(orders: Map<String, Int>)

    suspend fun deletePortfolio(firestoreId: String)
}
