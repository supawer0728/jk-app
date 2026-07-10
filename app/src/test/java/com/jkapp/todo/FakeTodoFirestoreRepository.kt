package com.jkapp.todo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTodoFirestoreRepository : TodoFirestoreRepository {

    private val _items = MutableStateFlow<List<TodoItem>>(emptyList())

    var updateTodoItemError: Throwable? = null
    var addTodoItemError: Throwable? = null
    var deleteTodoItemError: Throwable? = null

    var lastUpdatedItem: TodoItem? = null
    var lastAddedItem: TodoItem? = null
    var lastDeletedItemId: String? = null

    fun setItems(items: List<TodoItem>) { _items.value = items }

    override fun getTodoItems(): Flow<List<TodoItem>> = _items

    override suspend fun getTodoItemOnce(firestoreId: String): TodoItem? =
        _items.value.find { it.firestoreId == firestoreId }

    override suspend fun addTodoItem(item: TodoItem): String {
        addTodoItemError?.let { throw it }
        lastAddedItem = item
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
        deleteTodoItemError?.let { throw it }
        lastDeletedItemId = firestoreId
        _items.value = _items.value.filter { it.firestoreId != firestoreId }
    }

    override suspend fun completeTodoItem(firestoreId: String) {
        _items.value = _items.value.map {
            if (it.firestoreId == firestoreId) it.completeOccurrence(java.time.Instant.now()) else it
        }
    }
}
