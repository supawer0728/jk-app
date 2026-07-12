package com.jkapp.finance.investment

data class Portfolio(
    val firestoreId: String? = null,
    val name: String,
    val groups: List<PortfolioGroup> = emptyList(),
)

data class PortfolioGroup(
    val name: String,
    val owners: List<String> = emptyList(),
    val accounts: List<String> = emptyList(),
    val categories: List<String>? = null,
    val stockNames: List<String>? = null,
    // 목표 비율(%). 0 이상 100 이하 정수. 포트폴리오 내 모든 그룹의 합이 정확히 100이어야 저장 가능.
    val targetRatio: Int = 0,
)

/**
 * 포트폴리오 저장 전 유효성 검증. 모든 그룹의 targetRatio 합이 정확히 100이어야 한다.
 * @return null이면 유효, 그 외 오류 메시지.
 */
fun validatePortfolioGroups(groups: List<PortfolioGroup>): String? {
    val sum = groups.sumOf { it.targetRatio }
    return if (sum == 100) null else "그룹 목표 비율 합계가 ${sum}%입니다. 정확히 100%가 되어야 저장할 수 있습니다."
}
