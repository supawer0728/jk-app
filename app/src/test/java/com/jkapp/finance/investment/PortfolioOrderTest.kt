package com.jkapp.finance.investment

import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioOrderTest {

    private fun p(id: String?, order: Int? = null) = Portfolio(firestoreId = id, name = id ?: "new", order = order)

    // ---- sortedByOrder (nullsFirst) ----

    @Test
    fun `sortedByOrder는 null을 앞에 두고 이후 오름차순으로 정렬한다`() {
        val list = listOf(p("b", 1), p("a", null), p("c", 0), p("d", null))
        val sorted = list.sortedByOrder().map { it.firestoreId }
        // null(a,d)이 먼저(원래 순서 유지), 그다음 order 오름차순(c=0, b=1)
        assertEquals(listOf("a", "d", "c", "b"), sorted)
    }

    @Test
    fun `sortedByOrder는 동률(같은 order)에서 기존 순서를 유지한다(안정 정렬)`() {
        val list = listOf(p("x", 5), p("y", 5), p("z", 5))
        assertEquals(listOf("x", "y", "z"), list.sortedByOrder().map { it.firestoreId })
    }

    @Test
    fun `groupsSortedByOrder도 nullsFirst로 정렬한다`() {
        val groups = listOf(
            PortfolioGroup(name = "b", targetRatio = 50, order = 1),
            PortfolioGroup(name = "a", targetRatio = 50, order = null),
            PortfolioGroup(name = "c", targetRatio = 0, order = 0),
        )
        assertEquals(listOf("a", "c", "b"), groups.groupsSortedByOrder().map { it.name })
    }

    // ---- computePortfolioOrderUpdates (변경분만) ----

    @Test
    fun `최초 재정렬에서 모든 order가 null이면 전체에 index가 부여된다`() {
        val ordered = listOf(p("a", null), p("b", null), p("c", null))
        val updates = computePortfolioOrderUpdates(ordered)
        assertEquals(mapOf("a" to 0, "b" to 1, "c" to 2), updates)
    }

    @Test
    fun `이미 index와 order가 일치하면 빈 맵을 반환한다`() {
        val ordered = listOf(p("a", 0), p("b", 1), p("c", 2))
        assertEquals(emptyMap<String, Int>(), computePortfolioOrderUpdates(ordered))
    }

    @Test
    fun `한 칸 이동 시 값이 바뀌는 문서만 업데이트 대상이 된다`() {
        // [a=0, b=1, c=2] 에서 c를 맨 앞으로 옮긴 결과: [c, a, b]
        val ordered = listOf(p("c", 2), p("a", 0), p("b", 1))
        val updates = computePortfolioOrderUpdates(ordered)
        // c:2→0, a:0→1, b:1→2 모두 바뀜
        assertEquals(mapOf("c" to 0, "a" to 1, "b" to 2), updates)
    }

    @Test
    fun `인접 두 항목만 바뀌면 그 둘만 업데이트 대상이 된다`() {
        // [a=0, b=1, c=2, d=3] 에서 b와 c를 스왑: [a, c, b, d]
        val ordered = listOf(p("a", 0), p("c", 2), p("b", 1), p("d", 3))
        val updates = computePortfolioOrderUpdates(ordered)
        // a(0==0), d(3==3)는 제외. c:2→1, b:1→2만.
        assertEquals(mapOf("c" to 1, "b" to 2), updates)
    }

    @Test
    fun `firestoreId가 없는 항목은 업데이트 대상에서 제외된다`() {
        val ordered = listOf(p(null, null), p("b", null))
        assertEquals(mapOf("b" to 1), computePortfolioOrderUpdates(ordered))
    }
}
