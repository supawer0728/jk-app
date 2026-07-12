package com.jkapp.finance.investment

import java.math.BigDecimal

/**
 * 포트폴리오 그룹 매칭·합산 로직. 모두 순수 함수로 구성하여 단위 테스트가 쉽다.
 */
object PortfolioGroupMatcher {

    /**
     * [owner]·[item] 조합이 [group]에 속하는지 판단한다.
     *
     * 매칭 규칙:
     * - 축 간 AND: owners AND accounts AND categories AND stockNames 모두 통과해야 함.
     * - 축 내 OR: 각 축에서 값이 리스트에 포함되면 통과.
     * - null 또는 빈 리스트인 축은 "제한 없음(전체 통과)"으로 처리.
     */
    fun matches(group: PortfolioGroup, owner: String, item: InvestmentItem): Boolean {
        if (group.owners.isNotEmpty() && owner !in group.owners) return false
        if (group.accounts.isNotEmpty() && item.assetName !in group.accounts) return false
        if (!group.categories.isNullOrEmpty() && item.category !in group.categories) return false
        if (!group.stockNames.isNullOrEmpty() && item.investmentName !in group.stockNames) return false
        return true
    }

    /**
     * [portfolio]의 그룹별 평가금액(원화) 합계를 계산한다.
     *
     * @param portfolio 계산 대상 포트폴리오
     * @param ownerItemPairs (owner, InvestmentItem) 쌍의 목록 — latestDate 기준 전체 명의 종목
     * @return 그룹명 → 합산 평가금액. **어느 그룹에도 속하지 않는 종목은 제외된다(미분류 제외).**
     *         한 종목이 여러 그룹에 겹치면 각 그룹에 중복 합산된다.
     */
    fun computeGroupAmounts(
        portfolio: Portfolio,
        ownerItemPairs: List<Pair<String, InvestmentItem>>,
    ): Map<String, BigDecimal> {
        val result = portfolio.groups.associate { group ->
            val total = ownerItemPairs
                .filter { (owner, item) -> matches(group, owner, item) }
                .sumOf { (_, item) -> item.valuationAmount }
            group.name to total
        }
        return result
    }

    /**
     * 파이 차트용 데이터를 계산한다.
     *
     * @return 그룹명 → [PieSlice]. 분모는 분류된 종목(어느 그룹에든 속한 종목)의 합계.
     *         분모가 0이면 모든 슬라이스의 actualRatio = 0.
     */
    fun computePieSlices(
        portfolio: Portfolio,
        ownerItemPairs: List<Pair<String, InvestmentItem>>,
    ): List<PieSlice> {
        val groupAmounts = computeGroupAmounts(portfolio, ownerItemPairs)

        // 분모 = 어느 그룹엔가 매칭되는 (owner, item) 쌍을 distinct 없이 그대로 합산한다.
        // 분자(computeGroupAmounts)는 각 그룹마다 매칭 종목을 개별 합산하므로, 같은 종목이
        // 여러 그룹에 겹치면 그만큼 중복 합산된다. 분모도 동일한 카운팅 규칙(pair 단위, distinct
        // 없음)을 써야 슬라이스 비율의 합이 100%로 유지된다(두 명의가 동일 값 종목을 보유해도 각
        // pair가 별개로 집계됨). 단, 한 종목이 여러 그룹에 겹치면 슬라이스 합은 100%를 초과할 수
        // 있는데(중복 합산 허용 규칙), 이는 의도된 동작이다.
        val totalClassified = ownerItemPairs
            .filter { (owner, item) -> portfolio.groups.any { group -> matches(group, owner, item) } }
            .sumOf { (_, item) -> item.valuationAmount }

        return portfolio.groups.map { group ->
            val amount = groupAmounts[group.name] ?: BigDecimal.ZERO
            val actualRatio = if (totalClassified > BigDecimal.ZERO) {
                amount.multiply(BigDecimal(100))
                    .divide(totalClassified, 1, java.math.RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }
            PieSlice(
                groupName = group.name,
                amount = amount,
                actualRatioPct = actualRatio,
                targetRatioPct = BigDecimal(group.targetRatio),
            )
        }
    }
}

/**
 * 파이 차트 한 슬라이스(그룹)의 데이터.
 *
 * @param groupName 그룹명
 * @param amount 그룹에 속한 종목의 평가금액 합계(원화)
 * @param actualRatioPct 실제 비율 (소수점 1자리, 예: 34.5)
 * @param targetRatioPct 목표 비율 (정수, 예: 30)
 */
data class PieSlice(
    val groupName: String,
    val amount: BigDecimal,
    val actualRatioPct: BigDecimal,
    val targetRatioPct: BigDecimal,
)
