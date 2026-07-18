package com.jkapp.todo

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import com.jkapp.common.snapshotFlow
import com.jkapp.notification.CHANNEL_ID_TODO_ASSIGNMENT
import com.jkapp.push.PushMessage
import com.jkapp.push.PushRepository
import com.jkapp.push.PushRepositoryImpl
import com.jkapp.user.UserRepository
import com.jkapp.user.UserRepositoryImpl
import java.time.Instant
import kotlinx.coroutines.flow.Flow

class TodoFirestoreRepositoryImpl(
    private val db: FirebaseFirestore = AppFirestore.instance,
    // 현재 로그인 사용자 uid 공급자. 저장 시 lastEditedByUid에 주입해 마지막 편집자를 감사(audit)
    // 기록으로 남기고, 담당자 배정 push 생성 시 편집자 본인을 대상에서 제외하는 데도 쓴다(→ ADR/60/01
    // 근거 갱신). 기본값은 FirebaseAuth이며, 테스트에서 대체할 수 있도록 분리한다.
    private val currentUidProvider: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid },
    private val userRepository: UserRepository = UserRepositoryImpl(),
    private val pushRepository: PushRepository = PushRepositoryImpl(),
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
        val id = itemsRef.add(data).await().id
        maybeCreateAssignmentPush(before = null, after = anchored.copy(firestoreId = id))
        return id
    }

    /**
     * 자식(SUB) 항목 추가.
     * - type=SUB, mainTodoId=parentId 를 자동 주입한다.
     * - 완료(DONE) 부모는 IN_PROGRESS로 되돌린다(상태변경 무알림). 자동 되돌림은 사용자 편집이
     *   아니므로 부모의 lastEditedByUid는 보존한다(withEditor 미적용).
     * - notify=true면 자식 생성 push 1건 발송, notify=false면 무알림(반복 복제용, ≤1 push 원칙).
     */
    override suspend fun addSubTodoItem(parentId: String, item: TodoItem, notify: Boolean): String {
        // 완료 부모 → IN_PROGRESS 되돌림(무편집·무알림). 쓰기 실패해도 자식 추가는 진행.
        runCatching {
            val parent = getTodoItemOnce(parentId)
            if (parent != null && parent.status == TodoStatus.DONE) {
                // 자동 되돌림은 사용자 편집이 아니므로 withEditor()로 lastEditedByUid를 덮지 않는다.
                val revertedParent = parent.copy(
                    status = TodoStatus.IN_PROGRESS,
                    completedAt = null,
                )
                itemsRef.document(parentId).update(revertedParent.toMap()).await()
                // 부모 상태 변경은 assignee/title 변경이 아니므로 push 없음(≤1 원칙).
            }
        }.onFailure { Log.w(LOG_TAG, "완료 부모 되돌림 실패(자식 추가는 계속)", it) }

        val sub = item.copy(type = TodoType.SUB, mainTodoId = parentId).withEditor()
        val createdAt = sub.createdAt ?: Instant.now()
        val data = sub.toMap() + (FIELD_CREATED_AT to createdAt.toTimestamp())
        val id = itemsRef.add(data).await().id

        // 자식 생성 push는 notify=true일 때만 1건 발송(복제는 무알림으로 ≤1 원칙 충족).
        if (notify) {
            maybeCreateAssignmentPush(before = null, after = sub.copy(firestoreId = id))
        }
        return id
    }

    override suspend fun updateTodoItem(item: TodoItem) {
        val id = item.firestoreId ?: throw IllegalArgumentException("수정할 할일의 ID가 없습니다")
        val before = getTodoItemOnce(id)
        val after = item.withRecurrenceAnchored().withEditor()
        itemsRef.document(id).update(after.toMap()).await()
        maybeCreateAssignmentPush(before, after)
    }

    /**
     * 단건 삭제. MAIN이면 자식(SUB)도 cascade 삭제.
     */
    override suspend fun deleteTodoItem(firestoreId: String) {
        val batch = db.batch()
        batch.delete(itemsRef.document(firestoreId))
        // 자식 cascade: mainTodoId == firestoreId 인 문서 모두 삭제.
        val subs = itemsRef
            .whereEqualTo(FIELD_MAIN_TODO_ID, firestoreId)
            .get().await()
        subs.documents.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    /**
     * 다중 삭제. 각 id가 MAIN이면 자식(SUB)도 cascade 삭제.
     * Firestore 배치는 500건 제한이 있으므로 500건 단위로 나눠 커밋한다.
     */
    override suspend fun deleteTodoItems(ids: List<String>) {
        if (ids.isEmpty()) return
        // 삭제 대상 문서 ref 목록 수집(자식 cascade 포함).
        val allRefs = ids.flatMap { parentId ->
            val subRefs = itemsRef
                .whereEqualTo(FIELD_MAIN_TODO_ID, parentId)
                .get().await()
                .documents
                .map { it.reference }
            listOf(itemsRef.document(parentId)) + subRefs
        }
        // 500건 단위로 배치 분할(Firestore 배치 제한).
        allRefs.chunked(MAX_BATCH_SIZE).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it) }
            batch.commit().await()
        }
    }

    override suspend fun completeTodoItem(firestoreId: String) {
        val current = getTodoItemOnce(firestoreId)
            ?: throw IllegalArgumentException("완료할 할일을 찾을 수 없습니다: $firestoreId")
        val updated = current.completeOccurrence(Instant.now()).withEditor()
        itemsRef.document(firestoreId).update(updated.toMap()).await()

        // 부모 DONE 시 자식 cascade 완료(무알림).
        if (updated.status == TodoStatus.DONE) {
            cascadeCompleteChildren(firestoreId)
        }
        // completeOccurrence는 assignee·title을 바꾸지 않으므로 push를 만들지 않는다(FEATURE 참고).
    }

    /**
     * 부모가 DONE이 되었을 때 자식을 모두 DONE으로 cascade 완료한다.
     * 자식 알림은 발송하지 않는다(무알림, ≤1 push 원칙).
     */
    private suspend fun cascadeCompleteChildren(parentId: String) {
        runCatching {
            val subs = itemsRef
                .whereEqualTo(FIELD_MAIN_TODO_ID, parentId)
                .get().await()
            if (subs.isEmpty) return@runCatching
            val now = Instant.now()
            val batch = db.batch()
            subs.documents.forEach { doc ->
                val sub = doc.toTodoItem() ?: return@forEach
                if (sub.status != TodoStatus.DONE) {
                    val done = sub.copy(status = TodoStatus.DONE, completedAt = now).withEditor()
                    batch.update(doc.reference, done.toMap())
                }
            }
            batch.commit().await()
        }.onFailure { Log.w(LOG_TAG, "자식 cascade 완료 실패", it) }
    }

    // 저장 시점에 편집자(현재 로그인 사용자)의 uid를 박아둔다. add/update/complete 모든 쓰기 경로가
    // 이 값을 갱신해, 마지막으로 저장한 사람을 감사 기록으로 남긴다.
    private fun TodoItem.withEditor(): TodoItem = copy(lastEditedByUid = currentUidProvider())

    // 담당자 배정 push 생성(이슈 #89, 구 Cloud Functions 로직을 앱으로 이식). 이전 문서(before, 신규
    // 생성이면 null)와 저장된 문서(after)를 비교해 assignee·title이 실제로 바뀐 경우에만 push를
    // 만든다. 상태 순환·완료 전진은 이 비교에서 걸러지므로 completeTodoItem 경로는 호출하지 않는다.
    //
    // push 경로(getPushTokensByEmails 읽기 + createPush 쓰기)의 예외는 여기서 격리한다. 이 함수는
    // 주 작업(todo 저장)이 이미 성공한 뒤 부수적으로 호출되므로, push 실패가 저장 결과를 "실패"로
    // 오염시켜 사용자가 재시도(→ 중복 할일 생성)하게 만들면 안 된다.
    private suspend fun maybeCreateAssignmentPush(before: TodoItem?, after: TodoItem) {
        if (!shouldCreateAssignmentPush(before, after)) return

        // runCatching으로 push 경로 예외를 격리한다(TodoViewModel과 동일한 관용). 주 작업(todo
        // 저장)은 이미 성공했으므로 push 실패는 삼키고 기록만 한다.
        runCatching {
            val editorUid = after.lastEditedByUid
            val tokens = userRepository.getPushTokensByEmails(after.assignee.emails)
                .filterNot { it.uid == editorUid }
                .map { it.token }
            if (tokens.isNotEmpty()) {
                pushRepository.createPush(
                    PushMessage(
                        title = assignmentPushTitle(before),
                        body = after.title,
                        channelId = CHANNEL_ID_TODO_ASSIGNMENT,
                        tokens = tokens,
                    )
                )
            }
        }.onFailure { Log.w(LOG_TAG, "담당자 배정 push 생성 실패(저장은 완료됨)", it) }
    }

    // createdAt은 addTodoItem에서만 값을 부여하는 불변 필드이므로 여기(toMap)에는 포함하지 않는다.
    // 포함시키면 updateTodoItem/completeTodoItem이 매번 최신 값으로 덮어써 생성 시각을 잃어버린다.
    private fun TodoItem.toMap(): Map<String, Any?> = mapOf(
        FIELD_TYPE to type.name,
        FIELD_MAIN_TODO_ID to mainTodoId,
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
        // type이 없는 레거시 문서는 MAIN으로 처리한다(하위 호환).
        val type = TodoType.fromNameOrDefault(getString(FIELD_TYPE))
        return TodoItem(
            firestoreId = id,
            type = type,
            mainTodoId = getString(FIELD_MAIN_TODO_ID),
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

        // 앱 전역 로그 태그(AGENT.md의 로그 필터 규칙과 통일). push 실패 등 부수 작업 경고에 쓴다.
        private const val LOG_TAG = "jkapp"

        private const val COLLECTION_ITEMS = "todo-items"

        // Firestore 배치 최대 쓰기 건수.
        private const val MAX_BATCH_SIZE = 500

        // 이전 문서(before)와 저장된 문서(after)를 비교해 배정(assignee)·제목(title)이 모두 그대로면
        // 상태 순환·완료 전진 등 배정과 무관한 쓰기이므로 push를 만들 필요가 없다. before가 없으면
        // (신규 생성) 항상 만든다. 순수 함수라 Firestore/push 의존 없이 직접 단위 테스트할 수 있다.
        fun shouldCreateAssignmentPush(before: TodoItem?, after: TodoItem): Boolean =
            before == null || before.assignee != after.assignee || before.title != after.title

        fun assignmentPushTitle(before: TodoItem?): String =
            if (before == null) "새 할일이 등록되었습니다" else "할일이 수정되었습니다"

        private const val FIELD_TYPE = "type"
        private const val FIELD_MAIN_TODO_ID = "mainTodoId"
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
