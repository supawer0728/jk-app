package com.jkapp.todo

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoFilterTest {

    private fun item(
        title: String = "title",
        isCompleted: Boolean = false,
        categoryId: String? = null,
        tags: List<String> = emptyList(),
        priority: TodoPriority = TodoPriority.NONE,
        dueAt: Instant? = null,
        createdAt: Instant? = null,
    ) = TodoItem(
        title = title,
        isCompleted = isCompleted,
        categoryId = categoryId,
        tags = tags,
        priority = priority,
        dueAt = dueAt,
        createdAt = createdAt,
    )

    // region toggleInSet

    @Test
    fun `toggleInSet adds id when not present`() {
        val result = TodoViewModel.toggleInSet("A", emptySet())
        assertEquals(setOf("A"), result)
    }

    @Test
    fun `toggleInSet removes id when already present`() {
        val result = TodoViewModel.toggleInSet("A", setOf("A", "B"))
        assertEquals(setOf("B"), result)
    }

    // endregion

    // region filterByStatus

    @Test
    fun `filterByStatus ALL returns every item`() {
        val items = listOf(item(isCompleted = true), item(isCompleted = false))
        assertEquals(items, TodoViewModel.filterByStatus(items, TodoStatusFilter.ALL))
    }

    @Test
    fun `filterByStatus ACTIVE keeps only incomplete items`() {
        val incomplete = item(isCompleted = false)
        val items = listOf(incomplete, item(isCompleted = true))
        assertEquals(listOf(incomplete), TodoViewModel.filterByStatus(items, TodoStatusFilter.ACTIVE))
    }

    @Test
    fun `filterByStatus COMPLETED keeps only completed items`() {
        val completed = item(isCompleted = true)
        val items = listOf(item(isCompleted = false), completed)
        assertEquals(listOf(completed), TodoViewModel.filterByStatus(items, TodoStatusFilter.COMPLETED))
    }

    // endregion

    // region filterByCategory

    @Test
    fun `filterByCategory returns all items when categoryIds is empty`() {
        val items = listOf(item(categoryId = "A"), item(categoryId = "B"))
        assertEquals(items, TodoViewModel.filterByCategory(items, emptySet()))
    }

    @Test
    fun `filterByCategory keeps only matching category`() {
        val matching = item(categoryId = "A")
        val items = listOf(matching, item(categoryId = "B"), item(categoryId = null))
        assertEquals(listOf(matching), TodoViewModel.filterByCategory(items, setOf("A")))
    }

    // endregion

    // region filterByTag

    @Test
    fun `filterByTag returns all items when tags is empty`() {
        val items = listOf(item(tags = listOf("urgent")), item(tags = emptyList()))
        assertEquals(items, TodoViewModel.filterByTag(items, emptySet()))
    }

    @Test
    fun `filterByTag keeps items containing any selected tag`() {
        val matching = item(tags = listOf("urgent", "work"))
        val items = listOf(matching, item(tags = listOf("home")))
        assertEquals(listOf(matching), TodoViewModel.filterByTag(items, setOf("urgent")))
    }

    // endregion

    // region sortItems

    @Test
    fun `sortItems DUE_DATE orders earliest first and pushes null dueAt to the end`() {
        val earlier = item(title = "earlier", dueAt = Instant.parse("2024-01-01T00:00:00Z"))
        val later = item(title = "later", dueAt = Instant.parse("2024-02-01T00:00:00Z"))
        val noDueDate = item(title = "no-due", dueAt = null)
        val result = TodoViewModel.sortItems(listOf(later, noDueDate, earlier), TodoSortOption.DUE_DATE)
        assertEquals(listOf("earlier", "later", "no-due"), result.map { it.title })
    }

    @Test
    fun `sortItems PRIORITY orders highest priority first`() {
        val low = item(title = "low", priority = TodoPriority.LOW)
        val high = item(title = "high", priority = TodoPriority.HIGH)
        val none = item(title = "none", priority = TodoPriority.NONE)
        val result = TodoViewModel.sortItems(listOf(low, none, high), TodoSortOption.PRIORITY)
        assertEquals(listOf("high", "low", "none"), result.map { it.title })
    }

    @Test
    fun `sortItems CREATED_AT orders most recently created first`() {
        val older = item(title = "older", createdAt = Instant.parse("2024-01-01T00:00:00Z"))
        val newer = item(title = "newer", createdAt = Instant.parse("2024-02-01T00:00:00Z"))
        val result = TodoViewModel.sortItems(listOf(older, newer), TodoSortOption.CREATED_AT)
        assertEquals(listOf("newer", "older"), result.map { it.title })
    }

    @Test
    fun `sortItems CREATED_AT pushes null createdAt to the end`() {
        val withDate = item(title = "with-date", createdAt = Instant.parse("2024-01-01T00:00:00Z"))
        val noDate = item(title = "no-date", createdAt = null)
        val result = TodoViewModel.sortItems(listOf(noDate, withDate), TodoSortOption.CREATED_AT)
        assertEquals(listOf("with-date", "no-date"), result.map { it.title })
    }

    // endregion

    // region filterAndSort

    @Test
    fun `filterAndSort applies status, category, tag filters then sorts`() {
        val target = item(
            title = "target",
            isCompleted = false,
            categoryId = "work",
            tags = listOf("urgent"),
            dueAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
        val wrongCategory = item(isCompleted = false, categoryId = "home", tags = listOf("urgent"))
        val completed = item(isCompleted = true, categoryId = "work", tags = listOf("urgent"))
        val items = listOf(wrongCategory, completed, target)

        val result = TodoViewModel.filterAndSort(
            items,
            statusFilter = TodoStatusFilter.ACTIVE,
            categoryFilter = setOf("work"),
            tagFilter = setOf("urgent"),
            sortOption = TodoSortOption.DUE_DATE,
        )

        assertEquals(listOf(target), result)
        assertTrue(result.isNotEmpty())
    }

    // endregion
}
