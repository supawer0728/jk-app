package com.jkapp.todo

import kotlinx.coroutines.flow.Flow

interface TodoFirestoreRepository {
    fun getTodoItems(): Flow<List<TodoItem>>
    fun getCategories(): Flow<List<TodoCategory>>
    suspend fun getTodoItemOnce(firestoreId: String): TodoItem?
    suspend fun addTodoItem(item: TodoItem): String
    suspend fun updateTodoItem(item: TodoItem)
    suspend fun deleteTodoItem(firestoreId: String)
    suspend fun completeTodoItem(firestoreId: String)
    suspend fun addCategory(category: TodoCategory): String
    suspend fun updateCategory(category: TodoCategory)
    suspend fun deleteCategoryAndUnassignItems(categoryDocId: String, affectedItemIds: List<String>)
}
