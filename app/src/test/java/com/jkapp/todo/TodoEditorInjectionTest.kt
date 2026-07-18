package com.jkapp.todo

import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.jkapp.push.FakePushRepository
import com.jkapp.user.FakeUserRepository
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 이슈 #60: 담당자 배정 푸시에서 편집자 본인을 제외하려면 쓰기 시점에 현재 로그인 uid를
// lastEditedByUid로 저장해야 한다. add/update 경로가 currentUidProvider의 값을 실제로
// 문서 맵에 담는지 검증한다(FirebaseFirestore를 목킹해 기록되는 맵을 캡처).
@OptIn(ExperimentalCoroutinesApi::class)
class TodoEditorInjectionTest {

    private fun <T> taskReturning(value: T): Task<T> {
        val task = mockk<Task<T>>()
        every { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<T>>().onSuccess(value)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        return task
    }

    private fun repositoryCapturingAdd(
        dataSlot: CapturingSlot<Map<String, Any?>>,
        uid: String?,
    ): TodoFirestoreRepositoryImpl {
        val addedRef = mockk<DocumentReference>()
        every { addedRef.id } returns "new-id"
        val collectionRef = mockk<CollectionReference>()
        every { collectionRef.add(capture(dataSlot)) } returns taskReturning(addedRef)
        val db = mockk<FirebaseFirestore>()
        every { db.collection("todo-items") } returns collectionRef
        // userRepository/pushRepository는 fake로 대체해, 담당자 배정 push 생성 경로(이슈 #89)가
        // 실제 Firestore(AppFirestore.instance)를 건드리지 않게 한다. 토큰을 등록하지 않았으므로
        // push는 생성되지 않는다(대상이 없음) - 이 테스트의 관심사는 lastEditedByUid 주입뿐이다.
        return TodoFirestoreRepositoryImpl(
            db = db,
            currentUidProvider = { uid },
            userRepository = FakeUserRepository(),
            pushRepository = FakePushRepository(),
        )
    }

    @Test
    fun `addTodoItem은 currentUidProvider의 uid를 lastEditedByUid로 저장한다`() = runTest {
        val dataSlot = slot<Map<String, Any?>>()
        val repository = repositoryCapturingAdd(dataSlot, uid = "uid-editor")

        val id = repository.addTodoItem(TodoItem(title = "빨래"))

        assertEquals("new-id", id)
        assertEquals("uid-editor", dataSlot.captured["lastEditedByUid"])
    }

    @Test
    fun `uid가 없으면 lastEditedByUid는 null로 저장된다`() = runTest {
        val dataSlot = slot<Map<String, Any?>>()
        val repository = repositoryCapturingAdd(dataSlot, uid = null)

        repository.addTodoItem(TodoItem(title = "빨래"))

        assertNull(dataSlot.captured["lastEditedByUid"])
    }

    @Test
    fun `updateTodoItem은 currentUidProvider의 uid를 lastEditedByUid로 저장한다`() = runTest {
        val dataSlot = slot<Map<String, Any?>>()
        val docRef = mockk<DocumentReference>()
        every { docRef.update(capture(dataSlot)) } returns taskReturning(null)
        val collectionRef = mockk<CollectionReference>()
        every { collectionRef.document("todo-1") } returns docRef
        val db = mockk<FirebaseFirestore>()
        every { db.collection("todo-items") } returns collectionRef
        // getTodoItemOnce(before 조회)가 실행되므로 docRef.get()도 스텁한다. title이 없는 snapshot을
        // 반환하면 toTodoItem()이 null이 되어 before=null(변경 없음 판정과 무관하게 안전)로 처리된다.
        every { docRef.get() } returns taskReturning(mockk(relaxed = true))
        val repository = TodoFirestoreRepositoryImpl(
            db = db,
            currentUidProvider = { "uid-editor" },
            userRepository = FakeUserRepository(),
            pushRepository = FakePushRepository(),
        )

        repository.updateTodoItem(TodoItem(firestoreId = "todo-1", title = "빨래"))

        assertEquals("uid-editor", dataSlot.captured["lastEditedByUid"])
    }

    // 이슈 #88: 완료(DONE) 부모에 자식을 추가하면 부모가 IN_PROGRESS로 자동 되돌아간다. 이 자동
    // 되돌림은 사용자 편집이 아니므로 부모의 lastEditedByUid를 자식 편집자로 덮지 않고 보존해야 한다.
    @Test
    fun `addSubTodoItem의 완료 부모 되돌림은 부모 lastEditedByUid를 보존한다`() = runTest {
        val parentUpdateSlot = slot<Map<String, Any?>>()
        val subDataSlot = slot<Map<String, Any?>>()

        // 부모 조회 snapshot: DONE 상태 + 기존 편집자 "uid-original".
        val parentSnapshot = mockk<DocumentSnapshot>(relaxed = true)
        every { parentSnapshot.id } returns "p-1"
        every { parentSnapshot.getString("title") } returns "장보기"
        every { parentSnapshot.getString("status") } returns TodoStatus.DONE.name
        every { parentSnapshot.getString("lastEditedByUid") } returns "uid-original"

        val parentRef = mockk<DocumentReference>()
        every { parentRef.get() } returns taskReturning(parentSnapshot)
        every { parentRef.update(capture(parentUpdateSlot)) } returns taskReturning(null)

        val addedRef = mockk<DocumentReference>()
        every { addedRef.id } returns "sub-id"

        val collectionRef = mockk<CollectionReference>()
        every { collectionRef.document("p-1") } returns parentRef
        every { collectionRef.add(capture(subDataSlot)) } returns taskReturning(addedRef)
        val db = mockk<FirebaseFirestore>()
        every { db.collection("todo-items") } returns collectionRef

        val repository = TodoFirestoreRepositoryImpl(
            db = db,
            currentUidProvider = { "uid-child-editor" },
            userRepository = FakeUserRepository(),
            pushRepository = FakePushRepository(),
        )

        repository.addSubTodoItem("p-1", TodoItem(title = "우유"), notify = false)

        // 부모 되돌림은 기존 편집자 보존, 자식은 현재 편집자로 기록.
        assertEquals("uid-original", parentUpdateSlot.captured["lastEditedByUid"])
        assertEquals("IN_PROGRESS", parentUpdateSlot.captured["status"])
        assertEquals("uid-child-editor", subDataSlot.captured["lastEditedByUid"])
    }
}
