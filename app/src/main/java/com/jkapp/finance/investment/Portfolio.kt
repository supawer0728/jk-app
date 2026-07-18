package com.jkapp.finance.investment

data class Portfolio(
    val firestoreId: String? = null,
    val name: String,
    val groups: List<PortfolioGroup> = emptyList(),
    // 표시 순서. 사용자가 직접 입력하지 않으며 재정렬로만 바뀐다. 하위 호환을 위해 nullable이고
    // 정렬은 nullsFirst(null이 앞). null이면 아직 재정렬된 적 없는 항목이다.
    val order: Int? = null,
)

data class PortfolioGroup(
    val name: String,
    val owners: List<String> = emptyList(),
    val accounts: List<String> = emptyList(),
    val categories: List<String>? = null,
    val stockNames: List<String>? = null,
    // 목표 비율(%). 0 이상 100 이하 정수. 포트폴리오 내 모든 그룹의 합이 정확히 100이어야 저장 가능.
    val targetRatio: Int = 0,
    // 그룹 표시 순서. 사용자가 직접 입력하지 않으며, 포트폴리오 저장 시 목록 위치로 부여된다.
    // 하위 호환을 위해 nullable이고 정렬은 nullsFirst.
    val order: Int? = null,
)

private const val TARGET_RATIO_TOTAL = 100

/**
 * 포트폴리오 저장 전 유효성 검증. 모든 그룹의 targetRatio 합이 정확히 100이어야 한다.
 * @return null이면 유효, 그 외 오류 메시지.
 */
fun validatePortfolioGroups(groups: List<PortfolioGroup>): String? {
    val sum = groups.sumOf { it.targetRatio }
    return if (sum == TARGET_RATIO_TOTAL) null else "그룹 목표 비율 합계가 ${sum}%입니다. 정확히 100%가 되어야 저장할 수 있습니다."
}

/** 포트폴리오를 `order` 기준 nullsFirst(null이 앞, 이후 오름차순, 동률은 안정 정렬)로 정렬한다. */
fun List<Portfolio>.sortedByOrder(): List<Portfolio> =
    sortedWith(compareBy(nullsFirst()) { it.order })

/** 그룹을 `order` 기준 nullsFirst로 정렬한다. */
fun List<PortfolioGroup>.groupsSortedByOrder(): List<PortfolioGroup> =
    sortedWith(compareBy(nullsFirst()) { it.order })

/**
 * 재정렬된 포트폴리오 목록에 `order = index`를 재부여했을 때, 값이 실제로 바뀌는 항목만
 * `firestoreId → newOrder` 맵으로 반환한다.
 *
 * - firestoreId가 없는(아직 저장 안 된) 항목은 제외한다.
 * - 현재 order가 목록 위치(index)와 이미 같은 항목은 제외한다(불필요한 쓰기 방지).
 * - 최초 재정렬처럼 모든 order가 null이면 전체가 포함된다.
 */
fun computePortfolioOrderUpdates(orderedPortfolios: List<Portfolio>): Map<String, Int> =
    orderedPortfolios.mapIndexedNotNull { index, portfolio ->
        val id = portfolio.firestoreId ?: return@mapIndexedNotNull null
        if (portfolio.order != index) id to index else null
    }.toMap()
