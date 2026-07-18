package com.jkapp.todo

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTodoFirestoreRepository : TodoFirestoreRepository {

    private val _items = MutableStateFlow<List<TodoItem>>(emptyList())

    var updateTodoItemError: Throwable? = null
    var addTodoItemError: Throwable? = null
    var addSubTodoItemError: Throwable? = null
    var deleteTodoItemError: Throwable? = null

    // updateTodoItem 호출 카운트. failUpdateFromCall 이상 회차부터 실패시킨다(부수 작업 실패 격리 검증).
    private var updateCallCount: Int = 0
    private var failUpdateFromCall: Int = Int.MAX_VALUE

    // 앞으로 successCount번째까지는 성공시키고, 그 다음 updateTodoItem 호출부터 실패시킨다.
    fun failNextUpdateAfter(successCount: Int) {
        updateCallCount = 0
        failUpdateFromCall = successCount + 1
    }

    var lastUpdatedItem: TodoItem? = null
    var lastAddedItem: TodoItem? = null
    var lastAddedSubParentId: String? = null
    var lastAddedSubItem: TodoItem? = null
    var lastAddSubNotify: Boolean? = null
    var lastDeletedItemId: String? = null
    var lastDeletedItemIds: List<String>? = null
    var lastCompletedItemId: String? = null

    // firestoreId로 현재 저장된 항목을 조회한다(테스트 검증용).
    fun itemById(firestoreId: String): TodoItem? =
        _items.value.find { it.firestoreId == firestoreId }

    // 자식 생성 push가 발생한(= notify=true) 횟수. ≤1 push 원칙 검증용.
    var subNotifyPushCount: Int = 0
        private set

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

    override suspend fun addSubTodoItem(parentId: String, item: TodoItem, notify: Boolean): String {
        addSubTodoItemError?.let { throw it }
        lastAddedSubParentId = parentId
        lastAddedSubItem = item
        lastAddSubNotify = notify
        // 완료 부모 → IN_PROGRESS 되돌림(무편집·무알림) 모사. lastEditedByUid는 보존한다.
        _items.value = _items.value.map {
            if (it.firestoreId == parentId && it.status == TodoStatus.DONE) {
                it.copy(status = TodoStatus.IN_PROGRESS, completedAt = null)
            } else {
                it
            }
        }
        val id = "fake-sub-id-${_items.value.size}"
        _items.value = _items.value + item.copy(
            firestoreId = id,
            type = TodoType.SUB,
            mainTodoId = parentId,
        )
        // notify=true일 때만 자식 생성 push가 발생한다.
        if (notify) subNotifyPushCount++
        return id
    }

    override suspend fun updateTodoItem(item: TodoItem) {
        updateTodoItemError?.let { throw it }
        updateCallCount++
        if (updateCallCount >= failUpdateFromCall) {
            error("업데이트 실패(테스트 주입)")
        }
        lastUpdatedItem = item
        _items.value = _items.value.map { if (it.firestoreId == item.firestoreId) item else it }
    }

    override suspend fun deleteTodoItem(firestoreId: String) {
        deleteTodoItemError?.let { throw it }
        lastDeletedItemId = firestoreId
        // cascade: 부모 삭제 시 자식도 삭제.
        _items.value = _items.value.filter {
            it.firestoreId != firestoreId && it.mainTodoId != firestoreId
        }
    }

    override suspend fun deleteTodoItems(ids: List<String>) {
        deleteTodoItemError?.let { throw it }
        lastDeletedItemIds = ids
        // cascade: 각 부모 삭제 시 자식도 삭제.
        _items.value = _items.value.filter {
            it.firestoreId !in ids && it.mainTodoId !in ids
        }
    }

    override suspend fun completeTodoItem(firestoreId: String) {
        updateTodoItemError?.let { throw it }
        lastCompletedItemId = firestoreId
        val now = java.time.Instant.now()
        val target = _items.value.find { it.firestoreId == firestoreId }
        _items.value = _items.value.map {
            if (it.firestoreId == firestoreId) it.completeOccurrence(now) else it
        }
        // 부모가 DONE이면 자식도 cascade 완료(무알림).
        val parentDone = target?.completeOccurrence(now)?.status == TodoStatus.DONE
        if (parentDone) {
            _items.value = _items.value.map {
                if (it.mainTodoId == firestoreId && it.status != TodoStatus.DONE) {
                    it.copy(status = TodoStatus.DONE)
                } else {
                    it
                }
            }
        }
    }
}
