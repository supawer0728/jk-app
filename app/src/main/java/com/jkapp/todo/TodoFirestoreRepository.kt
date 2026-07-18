package com.jkapp.todo

import kotlinx.coroutines.flow.Flow

interface TodoFirestoreRepository {
    fun getTodoItems(): Flow<List<TodoItem>>
    suspend fun getTodoItemOnce(firestoreId: String): TodoItem?
    suspend fun addTodoItem(item: TodoItem): String
    /**
     * 자식(SUB) 항목 추가. type=SUB·mainTodoId 자동 주입.
     * 완료 부모는 IN_PROGRESS로 되돌림(무편집·무알림).
     * @param notify true면 자식 생성 push 1건 발송, false면 무알림(반복 복제용).
     */
    suspend fun addSubTodoItem(parentId: String, item: TodoItem, notify: Boolean = true): String
    suspend fun updateTodoItem(item: TodoItem)
    /** 단건 삭제. MAIN이면 자식(SUB)도 cascade 삭제. */
    suspend fun deleteTodoItem(firestoreId: String)
    /** 다중 삭제. 각 id가 MAIN이면 자식(SUB)도 cascade 삭제. */
    suspend fun deleteTodoItems(ids: List<String>)
    suspend fun completeTodoItem(firestoreId: String)
}
