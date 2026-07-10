package com.jkapp.todo

import kotlinx.coroutines.flow.Flow

interface TodoFirestoreRepository {
    fun getTodoItems(): Flow<List<TodoItem>>
    suspend fun getTodoItemOnce(firestoreId: String): TodoItem?
    suspend fun addTodoItem(item: TodoItem): String
    suspend fun updateTodoItem(item: TodoItem)
    suspend fun deleteTodoItem(firestoreId: String)
    suspend fun completeTodoItem(firestoreId: String)
}
