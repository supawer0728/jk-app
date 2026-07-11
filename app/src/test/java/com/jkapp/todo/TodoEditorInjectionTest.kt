package com.jkapp.todo

import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
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
        return TodoFirestoreRepositoryImpl(db) { uid }
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
        val repository = TodoFirestoreRepositoryImpl(db) { "uid-editor" }

        repository.updateTodoItem(TodoItem(firestoreId = "todo-1", title = "빨래"))

        assertEquals("uid-editor", dataSlot.captured["lastEditedByUid"])
    }
}
