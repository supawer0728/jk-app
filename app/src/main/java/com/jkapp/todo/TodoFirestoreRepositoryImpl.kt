package com.jkapp.todo

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import com.jkapp.common.snapshotFlow
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class TodoFirestoreRepositoryImpl : TodoFirestoreRepository {

    private val db = AppFirestore.instance
    private val itemsRef = db.collection(COLLECTION_ITEMS)
    private val categoriesRef = db.collection(COLLECTION_CATEGORIES)

    override fun getTodoItems(): Flow<List<TodoItem>> = itemsRef.snapshotFlow { snapshot ->
        snapshot?.documents?.mapNotNull { it.toTodoItem() } ?: emptyList()
    }

    override fun getCategories(): Flow<List<TodoCategory>> =
        categoriesRef.snapshotFlow { snapshot ->
            snapshot?.documents?.mapNotNull { it.toTodoCategory() }?.sortedBy { it.name } ?: emptyList()
        }

    override suspend fun getTodoItemOnce(firestoreId: String): TodoItem? =
        itemsRef.document(firestoreId).get().await().toTodoItem()

    override suspend fun addTodoItem(item: TodoItem): String {
        val anchored = item.withRecurrenceAnchored()
        val createdAt = anchored.createdAt ?: Instant.now()
        val data = anchored.toMap() + (FIELD_CREATED_AT to createdAt.toTimestamp())
        return itemsRef.add(data).await().id
    }

    override suspend fun updateTodoItem(item: TodoItem) {
        val id = item.firestoreId ?: throw IllegalArgumentException("수정할 할일의 ID가 없습니다")
        itemsRef.document(id).update(item.withRecurrenceAnchored().toMap()).await()
    }

    override suspend fun deleteTodoItem(firestoreId: String) {
        itemsRef.document(firestoreId).delete().await()
    }

    override suspend fun completeTodoItem(firestoreId: String) {
        val current = getTodoItemOnce(firestoreId)
            ?: throw IllegalArgumentException("완료할 할일을 찾을 수 없습니다: $firestoreId")
        val updated = current.completeOccurrence(Instant.now())
        itemsRef.document(firestoreId).update(updated.toMap()).await()
    }

    override suspend fun addCategory(category: TodoCategory): String =
        categoriesRef.add(category.toMap()).await().id

    override suspend fun updateCategory(category: TodoCategory) {
        val docId = category.docId.ifBlank { throw IllegalArgumentException("수정할 카테고리의 ID가 없습니다") }
        categoriesRef.document(docId).update(category.toMap()).await()
    }

    override suspend fun deleteCategoryAndUnassignItems(
        categoryDocId: String,
        affectedItemIds: List<String>,
    ) {
        val batch = db.batch()
        affectedItemIds.forEach { itemId ->
            batch.update(itemsRef.document(itemId), FIELD_CATEGORY_ID, null)
        }
        batch.delete(categoriesRef.document(categoryDocId))
        batch.commit().await()
    }

    // createdAt은 addTodoItem에서만 값을 부여하는 불변 필드이므로 여기(toMap)에는 포함하지 않는다.
    // 포함시키면 updateTodoItem/completeTodoItem이 매번 최신 값으로 덮어써 생성 시각을 잃어버린다.
    private fun TodoItem.toMap(): Map<String, Any?> = mapOf(
        FIELD_TITLE to title,
        FIELD_MEMO to memo,
        FIELD_IS_COMPLETED to isCompleted,
        FIELD_DUE_AT to dueAt?.toTimestamp(),
        FIELD_REMINDER_OFFSET_MINUTES to reminderOffsetMinutes,
        FIELD_PRIORITY to priority.name,
        FIELD_CATEGORY_ID to categoryId,
        FIELD_TAGS to tags,
        FIELD_RECURRENCE to recurrence?.toMap(),
        FIELD_COMPLETION_HISTORY to completionHistory.map { it.toTimestamp() },
        FIELD_COMPLETED_AT to completedAt?.toTimestamp(),
    )

    private fun TodoCategory.toMap(): Map<String, Any?> = mapOf(
        FIELD_CATEGORY_NAME to name,
        FIELD_CATEGORY_EMOJI to emoji,
        FIELD_CATEGORY_COLOR_HEX to colorHex,
    )

    private fun RecurrenceRule.toMap(): Map<String, Any?> = mapOf(
        FIELD_RECURRENCE_FREQUENCY to frequency.name,
        FIELD_RECURRENCE_INTERVAL to interval,
        FIELD_RECURRENCE_DAYS_OF_WEEK to daysOfWeek.toList(),
        FIELD_RECURRENCE_END_AT to endAt?.toTimestamp(),
        FIELD_RECURRENCE_ANCHOR_DAY to anchorDay,
    )

    private fun DocumentSnapshot.toTodoItem(): TodoItem? {
        val title = getString(FIELD_TITLE) ?: return null
        @Suppress("UNCHECKED_CAST")
        val recurrenceMap = get(FIELD_RECURRENCE) as? Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val tags = (get(FIELD_TAGS) as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val history = (get(FIELD_COMPLETION_HISTORY) as? List<Timestamp>) ?: emptyList()
        return TodoItem(
            firestoreId = id,
            title = title,
            memo = getString(FIELD_MEMO) ?: "",
            isCompleted = getBoolean(FIELD_IS_COMPLETED) ?: false,
            dueAt = getTimestamp(FIELD_DUE_AT)?.toInstantValue(),
            reminderOffsetMinutes = getLong(FIELD_REMINDER_OFFSET_MINUTES)?.toInt(),
            priority = getString(FIELD_PRIORITY)?.toTodoPriorityOrNull() ?: TodoPriority.NONE,
            categoryId = getString(FIELD_CATEGORY_ID),
            tags = tags,
            recurrence = recurrenceMap?.toRecurrenceRule(id),
            completionHistory = history.map { it.toInstantValue() },
            createdAt = getTimestamp(FIELD_CREATED_AT)?.toInstantValue(),
            completedAt = getTimestamp(FIELD_COMPLETED_AT)?.toInstantValue(),
        )
    }

    private fun DocumentSnapshot.toTodoCategory(): TodoCategory? {
        val name = getString(FIELD_CATEGORY_NAME) ?: return null
        return TodoCategory(
            docId = id,
            name = name,
            emoji = getString(FIELD_CATEGORY_EMOJI) ?: "📁",
            colorHex = getString(FIELD_CATEGORY_COLOR_HEX) ?: "#9E9E9E",
        )
    }

    private fun Map<String, Any?>.toRecurrenceRule(itemDocId: String): RecurrenceRule? {
        val frequencyRaw = this[FIELD_RECURRENCE_FREQUENCY] as? String
        val frequency = frequencyRaw?.toRecurrenceFrequencyOrNull()
        if (frequency == null) {
            if (frequencyRaw != null) {
                Log.w(TAG, "todo-items/$itemDocId 문서의 recurrence.frequency를 알 수 없어 무시합니다: $frequencyRaw")
            }
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val daysOfWeek = (this[FIELD_RECURRENCE_DAYS_OF_WEEK] as? List<Long>)
            ?.map { it.toInt() }?.toSet() ?: emptySet()
        return RecurrenceRule(
            frequency = frequency,
            interval = (this[FIELD_RECURRENCE_INTERVAL] as? Long)?.toInt() ?: 1,
            daysOfWeek = daysOfWeek,
            endAt = (this[FIELD_RECURRENCE_END_AT] as? Timestamp)?.toInstantValue(),
            anchorDay = (this[FIELD_RECURRENCE_ANCHOR_DAY] as? Long)?.toInt(),
        )
    }

    private fun String.toTodoPriorityOrNull(): TodoPriority? =
        runCatching { TodoPriority.valueOf(this) }.getOrNull()

    private fun String.toRecurrenceFrequencyOrNull(): RecurrenceFrequency? =
        runCatching { RecurrenceFrequency.valueOf(this) }.getOrNull()

    private fun Instant.toTimestamp(): Timestamp = Timestamp(epochSecond, nano)

    private fun Timestamp.toInstantValue(): Instant =
        Instant.ofEpochSecond(seconds, nanoseconds.toLong())

    companion object {
        private const val TAG = "TodoFirestoreRepositoryImpl"

        private const val COLLECTION_ITEMS = "todo-items"
        private const val COLLECTION_CATEGORIES = "todo-categories"

        private const val FIELD_TITLE = "title"
        private const val FIELD_MEMO = "memo"
        private const val FIELD_IS_COMPLETED = "isCompleted"
        private const val FIELD_DUE_AT = "dueAt"
        private const val FIELD_REMINDER_OFFSET_MINUTES = "reminderOffsetMinutes"
        private const val FIELD_PRIORITY = "priority"
        private const val FIELD_CATEGORY_ID = "categoryId"
        private const val FIELD_TAGS = "tags"
        private const val FIELD_RECURRENCE = "recurrence"
        private const val FIELD_COMPLETION_HISTORY = "completionHistory"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_COMPLETED_AT = "completedAt"

        private const val FIELD_RECURRENCE_FREQUENCY = "frequency"
        private const val FIELD_RECURRENCE_INTERVAL = "interval"
        private const val FIELD_RECURRENCE_DAYS_OF_WEEK = "daysOfWeek"
        private const val FIELD_RECURRENCE_END_AT = "endAt"
        private const val FIELD_RECURRENCE_ANCHOR_DAY = "anchorDay"

        private const val FIELD_CATEGORY_NAME = "name"
        private const val FIELD_CATEGORY_EMOJI = "emoji"
        private const val FIELD_CATEGORY_COLOR_HEX = "colorHex"
    }
}
