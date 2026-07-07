package com.jkapp.todo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTodoFirestoreRepository : TodoFirestoreRepository {

    private val _items = MutableStateFlow<List<TodoItem>>(emptyList())
    private val _categories = MutableStateFlow<List<TodoCategory>>(emptyList())

    var updateTodoItemError: Throwable? = null
    var lastUpdatedItem: TodoItem? = null

    fun setItems(items: List<TodoItem>) { _items.value = items }
    fun setCategories(categories: List<TodoCategory>) { _categories.value = categories }

    override fun getTodoItems(): Flow<List<TodoItem>> = _items
    override fun getCategories(): Flow<List<TodoCategory>> = _categories

    override suspend fun getTodoItemOnce(firestoreId: String): TodoItem? =
        _items.value.find { it.firestoreId == firestoreId }

    override suspend fun addTodoItem(item: TodoItem): String {
        val id = "fake-id-${_items.value.size}"
        _items.value = _items.value + item.copy(firestoreId = id)
        return id
    }

    override suspend fun updateTodoItem(item: TodoItem) {
        updateTodoItemError?.let { throw it }
        lastUpdatedItem = item
        _items.value = _items.value.map { if (it.firestoreId == item.firestoreId) item else it }
    }

    override suspend fun deleteTodoItem(firestoreId: String) {
        _items.value = _items.value.filter { it.firestoreId != firestoreId }
    }

    override suspend fun completeTodoItem(firestoreId: String) {
        _items.value = _items.value.map {
            if (it.firestoreId == firestoreId) it.completeOccurrence(java.time.Instant.now()) else it
        }
    }

    override suspend fun addCategory(category: TodoCategory): String {
        val id = "fake-cat-${_categories.value.size}"
        _categories.value = _categories.value + category.copy(docId = id)
        return id
    }

    override suspend fun updateCategory(category: TodoCategory) {
        _categories.value = _categories.value.map { if (it.docId == category.docId) category else it }
    }

    override suspend fun deleteCategoryAndUnassignItems(categoryDocId: String, affectedItemIds: List<String>) {
        _items.value = _items.value.map {
            if (it.firestoreId in affectedItemIds) it.copy(categoryId = null) else it
        }
        _categories.value = _categories.value.filter { it.docId != categoryDocId }
    }
}
