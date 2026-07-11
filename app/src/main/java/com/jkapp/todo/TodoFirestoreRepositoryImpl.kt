package com.jkapp.todo

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import com.jkapp.common.snapshotFlow
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class TodoFirestoreRepositoryImpl(
    db: FirebaseFirestore = AppFirestore.instance,
    // 현재 로그인 사용자 uid 공급자. 저장 시 lastEditedByUid에 주입해 서버 알림 로직이 편집자
    // 본인을 제외할 수 있게 한다. 기본값은 FirebaseAuth이며, 테스트에서 대체할 수 있도록 분리한다.
    private val currentUidProvider: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
) : TodoFirestoreRepository {

    private val itemsRef = db.collection(COLLECTION_ITEMS)

    override fun getTodoItems(): Flow<List<TodoItem>> = itemsRef.snapshotFlow { snapshot ->
        snapshot?.documents?.mapNotNull { it.toTodoItem() } ?: emptyList()
    }

    override suspend fun getTodoItemOnce(firestoreId: String): TodoItem? =
        itemsRef.document(firestoreId).get().await().toTodoItem()

    override suspend fun addTodoItem(item: TodoItem): String {
        val anchored = item.withRecurrenceAnchored().withEditor()
        val createdAt = anchored.createdAt ?: Instant.now()
        val data = anchored.toMap() + (FIELD_CREATED_AT to createdAt.toTimestamp())
        return itemsRef.add(data).await().id
    }

    override suspend fun updateTodoItem(item: TodoItem) {
        val id = item.firestoreId ?: throw IllegalArgumentException("수정할 할일의 ID가 없습니다")
        itemsRef.document(id).update(item.withRecurrenceAnchored().withEditor().toMap()).await()
    }

    override suspend fun deleteTodoItem(firestoreId: String) {
        itemsRef.document(firestoreId).delete().await()
    }

    override suspend fun completeTodoItem(firestoreId: String) {
        val current = getTodoItemOnce(firestoreId)
            ?: throw IllegalArgumentException("완료할 할일을 찾을 수 없습니다: $firestoreId")
        val updated = current.completeOccurrence(Instant.now()).withEditor()
        itemsRef.document(firestoreId).update(updated.toMap()).await()
    }

    // 저장 시점에 편집자(현재 로그인 사용자)의 uid를 박아둔다. add/update/complete 모든 쓰기 경로가
    // 이 값을 갱신해, 서버 알림 로직이 마지막으로 저장한 사람을 대상에서 제외할 수 있게 한다.
    private fun TodoItem.withEditor(): TodoItem = copy(lastEditedByUid = currentUidProvider())

    // createdAt은 addTodoItem에서만 값을 부여하는 불변 필드이므로 여기(toMap)에는 포함하지 않는다.
    // 포함시키면 updateTodoItem/completeTodoItem이 매번 최신 값으로 덮어써 생성 시각을 잃어버린다.
    private fun TodoItem.toMap(): Map<String, Any?> = mapOf(
        FIELD_TITLE to title,
        FIELD_MEMO to memo,
        FIELD_STATUS to status.name,
        FIELD_ASSIGNEE to assignee.name,
        // status로 전환했지만, 마이그레이션 중 구버전 코드가 남은 다른 기기와 완료 여부가 어긋나지 않게
        // isCompleted(파생값)도 병기한다(이슈 #71 ADR).
        FIELD_IS_COMPLETED to isCompleted,
        FIELD_DUE_AT to dueAt?.toTimestamp(),
        FIELD_REMINDER_OFFSET_MINUTES to reminderOffsetMinutes,
        FIELD_PRIORITY to priority.name,
        FIELD_RECURRENCE to recurrence?.toMap(),
        FIELD_COMPLETION_HISTORY to completionHistory.map { it.toTimestamp() },
        FIELD_COMPLETED_AT to completedAt?.toTimestamp(),
        FIELD_LAST_EDITED_BY_UID to lastEditedByUid,
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
        val history = (get(FIELD_COMPLETION_HISTORY) as? List<Timestamp>) ?: emptyList()
        // status가 있으면 그대로 파싱하고, 없으면(status 도입 이전 문서) isCompleted로부터 유도한다.
        val status = getString(FIELD_STATUS)?.toTodoStatusOrNull()
            ?: todoStatusFromLegacyCompleted(getBoolean(FIELD_IS_COMPLETED) ?: false)
        return TodoItem(
            firestoreId = id,
            title = title,
            memo = getString(FIELD_MEMO) ?: "",
            status = status,
            assignee = TodoAssignee.fromNameOrDefault(getString(FIELD_ASSIGNEE)),
            dueAt = getTimestamp(FIELD_DUE_AT)?.toInstantValue(),
            reminderOffsetMinutes = getLong(FIELD_REMINDER_OFFSET_MINUTES)?.toInt(),
            priority = getString(FIELD_PRIORITY)?.toTodoPriorityOrNull() ?: TodoPriority.NONE,
            recurrence = recurrenceMap?.toRecurrenceRule(id),
            completionHistory = history.map { it.toInstantValue() },
            createdAt = getTimestamp(FIELD_CREATED_AT)?.toInstantValue(),
            completedAt = getTimestamp(FIELD_COMPLETED_AT)?.toInstantValue(),
            lastEditedByUid = getString(FIELD_LAST_EDITED_BY_UID),
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

        private const val FIELD_TITLE = "title"
        private const val FIELD_MEMO = "memo"
        private const val FIELD_STATUS = "status"
        private const val FIELD_ASSIGNEE = "assignee"
        private const val FIELD_IS_COMPLETED = "isCompleted"
        private const val FIELD_DUE_AT = "dueAt"
        private const val FIELD_REMINDER_OFFSET_MINUTES = "reminderOffsetMinutes"
        private const val FIELD_PRIORITY = "priority"
        private const val FIELD_RECURRENCE = "recurrence"
        private const val FIELD_COMPLETION_HISTORY = "completionHistory"
        private const val FIELD_CREATED_AT = "createdAt"
        private const val FIELD_COMPLETED_AT = "completedAt"
        private const val FIELD_LAST_EDITED_BY_UID = "lastEditedByUid"

        private const val FIELD_RECURRENCE_FREQUENCY = "frequency"
        private const val FIELD_RECURRENCE_INTERVAL = "interval"
        private const val FIELD_RECURRENCE_DAYS_OF_WEEK = "daysOfWeek"
        private const val FIELD_RECURRENCE_END_AT = "endAt"
        private const val FIELD_RECURRENCE_ANCHOR_DAY = "anchorDay"
    }
}
