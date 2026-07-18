package com.jkapp.finance.investment

/**
 * 투자 종목 목록 화면의 다중 선택 필터 상태.
 *
 * 각 축은 선택된 값의 Set이며 **빈 Set은 "전체(제한 없음)"** 를 의미한다.
 * 매칭 규칙: 축 간 AND, 축 내 OR.
 */
data class InvestmentFilter(
    val owners: Set<String> = emptySet(),
    val accounts: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val stockNames: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = owners.isEmpty() && accounts.isEmpty() && categories.isEmpty() && stockNames.isEmpty()

    /**
     * [owner]·[item] 조합이 이 필터를 통과하는지 검사한다.
     *
     * 빈 Set인 축은 모든 값을 통과시킨다.
     */
    fun matches(owner: String, item: InvestmentItem): Boolean =
        (owners.isEmpty() || owner in owners) &&
            (accounts.isEmpty() || item.assetName in accounts) &&
            (categories.isEmpty() || item.category in categories) &&
            (stockNames.isEmpty() || item.investmentName in stockNames)
}
