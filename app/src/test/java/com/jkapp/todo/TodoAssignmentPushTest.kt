package com.jkapp.todo

import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.jkapp.notification.CHANNEL_ID_TODO_ASSIGNMENT
import com.jkapp.push.FakePushRepository
import com.jkapp.push.PushMessage
import com.jkapp.user.FakeUserRepository
import com.jkapp.user.PushToken
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// 이슈 #89: 담당자 배정 push 생성(구 Cloud Functions 로직을 앱으로 이식) 검증.
// 1) 순수 비교 함수(shouldCreateAssignmentPush/assignmentPushTitle) 단위 테스트.
// 2) TodoFirestoreRepositoryImpl의 add/updateTodoItem이 실제로 push를 만드는지
//    (대상 해석·편집자 제외 포함).
@OptIn(ExperimentalCoroutinesApi::class)
class TodoAssignmentPushTest {

    // --- 순수 함수: assignee·title 변경 비교 ---

    @Test
    fun `before가 null이면(신규 생성) 항상 push를 만든다`() {
        val after = TodoItem(title = "빨래", assignee = TodoAssignee.SHARED)
        val actual =
            TodoFirestoreRepositoryImpl.shouldCreateAssignmentPush(before = null, after = after)
        assertTrue(actual)
    }

    @Test
    fun `assignee가 바뀌면 push를 만든다`() {
        val before = TodoItem(title = "빨래", assignee = TodoAssignee.SHARED)
        val after = before.copy(assignee = TodoAssignee.KWON_YUKYEONG)
        assertTrue(TodoFirestoreRepositoryImpl.shouldCreateAssignmentPush(before, after))
    }

    @Test
    fun `title이 바뀌면 push를 만든다`() {
        val before = TodoItem(title = "빨래", assignee = TodoAssignee.SHARED)
        val after = before.copy(title = "청소")
        assertTrue(TodoFirestoreRepositoryImpl.shouldCreateAssignmentPush(before, after))
    }

    @Test
    fun `assignee·title이 모두 그대로면(상태 순환·완료 전진) push를 만들지 않는다`() {
        val before = TodoItem(
            title = "빨래",
            assignee = TodoAssignee.SHARED,
            status = TodoStatus.NOT_STARTED,
        )
        val after = before.copy(status = TodoStatus.IN_PROGRESS)
        assertFalse(TodoFirestoreRepositoryImpl.shouldCreateAssignmentPush(before, after))
    }

    @Test
    fun `assignmentPushTitle은 before가 null이면 생성, 아니면 수정 문구를 반환한다`() {
        assertEquals(
            "새 할일이 등록되었습니다",
            TodoFirestoreRepositoryImpl.assignmentPushTitle(before = null),
        )
        assertEquals(
            "할일이 수정되었습니다",
            TodoFirestoreRepositoryImpl.assignmentPushTitle(before = TodoItem(title = "빨래")),
        )
    }

    // --- 통합: 저장 경로에서 실제로 push가 생성되는지 ---

    private fun <T> taskReturning(value: T): Task<T> {
        val task = mockk<Task<T>>()
        every { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<T>>().onSuccess(value)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        return task
    }

    private fun repositoryForAdd(
        editorUid: String?,
        fakeUsers: FakeUserRepository,
        fakePushes: FakePushRepository,
    ): TodoFirestoreRepositoryImpl {
        val addedRef = mockk<DocumentReference>()
        every { addedRef.id } returns "new-id"
        val collectionRef = mockk<CollectionReference>()
        every { collectionRef.add(any()) } returns taskReturning(addedRef)
        val db = mockk<FirebaseFirestore>()
        every { db.collection("todo-items") } returns collectionRef
        return TodoFirestoreRepositoryImpl(db, { editorUid }, fakeUsers, fakePushes)
    }

    // before 문서가 없는 것처럼 만들려면(getTodoItemOnce가 before=null을 반환하도록) title이 없는
    // snapshot을 준다 - toTodoItem()은 title이 없으면 null을 반환한다.
    private fun snapshotWithoutTitle(): DocumentSnapshot {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true)
        every { snapshot.getString("title") } returns null
        return snapshot
    }

    private fun snapshotWith(title: String, assigneeName: String): DocumentSnapshot {
        val snapshot = mockk<DocumentSnapshot>(relaxed = true)
        every { snapshot.id } returns "todo-1"
        every { snapshot.getString("title") } returns title
        every { snapshot.getString("assignee") } returns assigneeName
        return snapshot
    }

    private fun repositoryForUpdate(
        beforeSnapshot: DocumentSnapshot,
        editorUid: String?,
        fakeUsers: FakeUserRepository,
        fakePushes: FakePushRepository,
    ): TodoFirestoreRepositoryImpl {
        val docRef = mockk<DocumentReference>()
        every { docRef.get() } returns taskReturning(beforeSnapshot)
        every { docRef.update(any()) } returns taskReturning(null)
        val collectionRef = mockk<CollectionReference>()
        every { collectionRef.document("todo-1") } returns docRef
        val db = mockk<FirebaseFirestore>()
        every { db.collection("todo-items") } returns collectionRef
        return TodoFirestoreRepositoryImpl(db, { editorUid }, fakeUsers, fakePushes)
    }

    private suspend fun FakeUserRepository.registerToken(
        uid: String,
        email: String,
        token: String,
    ) {
        setUserEmail(uid, email)
        updatePushToken(uid, PushToken(token = token, updatedAt = 0L))
    }

    @Test
    fun `addTodoItem은 신규 생성이면 편집자를 제외한 담당자에게 보낼 push를 만든다`() = runTest {
        val fakeUsers = FakeUserRepository()
        fakeUsers.registerToken(
            uid = "uid-jeon",
            email = TodoAssignee.EMAIL_JEON_JIHOON,
            token = "token-jeon",
        )
        fakeUsers.registerToken(
            uid = "uid-kwon",
            email = TodoAssignee.EMAIL_KWON_YUKYEONG,
            token = "token-kwon",
        )
        val fakePushes = FakePushRepository()
        val repository = repositoryForAdd(editorUid = "uid-jeon", fakeUsers, fakePushes)

        repository.addTodoItem(TodoItem(title = "빨래", assignee = TodoAssignee.SHARED))

        assertEquals(1, fakePushes.createdMessages.size)
        val message = fakePushes.createdMessages.single()
        assertEquals(
            PushMessage(
                title = "새 할일이 등록되었습니다",
                body = "빨래",
                channelId = CHANNEL_ID_TODO_ASSIGNMENT,
                tokens = listOf("token-kwon"),
            ),
            message,
        )
    }

    @Test
    fun `addTodoItem은 대상이 편집자 본인뿐이면 push를 만들지 않는다`() = runTest {
        val fakeUsers = FakeUserRepository()
        fakeUsers.registerToken(
            uid = "uid-jeon",
            email = TodoAssignee.EMAIL_JEON_JIHOON,
            token = "token-jeon",
        )
        val fakePushes = FakePushRepository()
        val repository = repositoryForAdd(editorUid = "uid-jeon", fakeUsers, fakePushes)

        repository.addTodoItem(TodoItem(title = "빨래", assignee = TodoAssignee.JEON_JIHOON))

        assertTrue(fakePushes.createdMessages.isEmpty())
    }

    @Test
    fun `addTodoItem은 대상 사용자에게 토큰이 없으면 push를 만들지 않는다`() = runTest {
        val fakeUsers = FakeUserRepository() // 토큰 등록 없음
        val fakePushes = FakePushRepository()
        val repository = repositoryForAdd(editorUid = "uid-jeon", fakeUsers, fakePushes)

        repository.addTodoItem(TodoItem(title = "빨래", assignee = TodoAssignee.SHARED))

        assertTrue(fakePushes.createdMessages.isEmpty())
    }

    @Test
    fun `updateTodoItem은 assignee·title이 그대로면(상태 변경만) push를 만들지 않는다`() = runTest {
        val fakeUsers = FakeUserRepository()
        fakeUsers.registerToken(
            uid = "uid-kwon",
            email = TodoAssignee.EMAIL_KWON_YUKYEONG,
            token = "token-kwon",
        )
        val fakePushes = FakePushRepository()
        val before = snapshotWith(title = "빨래", assigneeName = "SHARED")
        val repository = repositoryForUpdate(before, editorUid = "uid-jeon", fakeUsers, fakePushes)

        repository.updateTodoItem(
            TodoItem(
                firestoreId = "todo-1",
                title = "빨래",
                assignee = TodoAssignee.SHARED,
                status = TodoStatus.IN_PROGRESS,
            ),
        )

        assertTrue(fakePushes.createdMessages.isEmpty())
    }

    @Test
    fun `updateTodoItem은 assignee가 바뀌면 편집자를 제외한 새 담당자에게 push를 만든다`() = runTest {
        val fakeUsers = FakeUserRepository()
        fakeUsers.registerToken(
            uid = "uid-jeon",
            email = TodoAssignee.EMAIL_JEON_JIHOON,
            token = "token-jeon",
        )
        fakeUsers.registerToken(
            uid = "uid-kwon",
            email = TodoAssignee.EMAIL_KWON_YUKYEONG,
            token = "token-kwon",
        )
        val fakePushes = FakePushRepository()
        val before = snapshotWith(title = "빨래", assigneeName = "SHARED")
        val repository = repositoryForUpdate(before, editorUid = "uid-jeon", fakeUsers, fakePushes)

        repository.updateTodoItem(
            TodoItem(firestoreId = "todo-1", title = "빨래", assignee = TodoAssignee.KWON_YUKYEONG),
        )

        assertEquals(1, fakePushes.createdMessages.size)
        val message = fakePushes.createdMessages.single()
        assertEquals("할일이 수정되었습니다", message.title)
        assertEquals(listOf("token-kwon"), message.tokens)
    }

    @Test
    fun `updateTodoItem은 title만 바뀌어도 push를 만든다`() = runTest {
        val fakeUsers = FakeUserRepository()
        fakeUsers.registerToken(
            uid = "uid-kwon",
            email = TodoAssignee.EMAIL_KWON_YUKYEONG,
            token = "token-kwon",
        )
        val fakePushes = FakePushRepository()
        val before = snapshotWith(title = "빨래", assigneeName = "SHARED")
        val repository = repositoryForUpdate(before, editorUid = "uid-jeon", fakeUsers, fakePushes)

        repository.updateTodoItem(
            TodoItem(firestoreId = "todo-1", title = "청소", assignee = TodoAssignee.SHARED),
        )

        assertEquals(1, fakePushes.createdMessages.size)
    }

    @Test
    fun `updateTodoItem은 이전 문서가 없어도(레거시 등) push를 신규 생성으로 취급한다`() = runTest {
        val fakeUsers = FakeUserRepository()
        fakeUsers.registerToken(
            uid = "uid-kwon",
            email = TodoAssignee.EMAIL_KWON_YUKYEONG,
            token = "token-kwon",
        )
        val fakePushes = FakePushRepository()
        val beforeSnapshot = snapshotWithoutTitle()
        val repository =
            repositoryForUpdate(beforeSnapshot, editorUid = "uid-jeon", fakeUsers, fakePushes)

        repository.updateTodoItem(
            TodoItem(firestoreId = "todo-1", title = "빨래", assignee = TodoAssignee.SHARED),
        )

        assertEquals("새 할일이 등록되었습니다", fakePushes.createdMessages.single().title)
    }
}
