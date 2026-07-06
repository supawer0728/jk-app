package com.jkapp.finance.investment

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeInvestmentFirestoreRepository : InvestmentFirestoreRepository {

    private val _dailyAssetInvestments = MutableStateFlow<List<DailyAssetInvestment>>(emptyList())

    var upsertDailyAssetInvestmentError: Throwable? = null
    var deleteDailyAssetInvestmentError: Throwable? = null

    // 테스트에서 실제 Firestore 네트워크 왕복(suspension)을 흉내내기 위한 훅.
    // 동시 호출 시 뮤텍스로 직렬화되는지 검증하는 데 사용한다.
    var onUpsertDailyAssetInvestment: (suspend () -> Unit)? = null

    fun setDailyAssetInvestments(investments: List<DailyAssetInvestment>) { _dailyAssetInvestments.value = investments }

    override fun getDailyAssetInvestments(): Flow<List<DailyAssetInvestment>> = _dailyAssetInvestments

    override suspend fun upsertDailyAssetInvestment(investment: DailyAssetInvestment) {
        upsertDailyAssetInvestmentError?.let { throw it }
        onUpsertDailyAssetInvestment?.invoke()
        val existingIndex = _dailyAssetInvestments.value.indexOfFirst { it.date == investment.date && it.owner == investment.owner }
        _dailyAssetInvestments.value = if (existingIndex >= 0) {
            _dailyAssetInvestments.value.mapIndexed { index, existing -> if (index == existingIndex) investment else existing }
        } else {
            _dailyAssetInvestments.value + investment
        }
    }

    override suspend fun deleteDailyAssetInvestment(date: String, owner: String) {
        deleteDailyAssetInvestmentError?.let { throw it }
        _dailyAssetInvestments.value = _dailyAssetInvestments.value.filter { !(it.date == date && it.owner == owner) }
    }
}
