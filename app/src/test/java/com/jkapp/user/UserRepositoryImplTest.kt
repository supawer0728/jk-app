package com.jkapp.user

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UserRepositoryImplTest {

    // 이슈 #57 코드 리뷰 지적: DocumentSnapshot -> UserPreference 매핑(null/누락/타입 불일치 케이스)이
    // 테스트 없이 방치되어 있었다. addSnapshotListener를 즉시 발화하도록 스텁해 observePreference의
    // 첫 값을 검증한다.
    private fun repositoryObservingSnapshot(snapshot: DocumentSnapshot?): UserRepositoryImpl {
        val docRef = mockk<DocumentReference>()
        every { docRef.addSnapshotListener(any<EventListener<DocumentSnapshot>>()) } answers {
            firstArg<EventListener<DocumentSnapshot>>().onEvent(snapshot, null)
            mockk<ListenerRegistration>(relaxed = true)
        }
        val collectionRef = mockk<CollectionReference>()
        every { collectionRef.document("uid-1") } returns docRef
        val db = mockk<FirebaseFirestore>()
        every { db.collection("users") } returns collectionRef
        return UserRepositoryImpl(db)
    }

    private fun snapshotWithPreference(preferenceMap: Map<String, Any>?): DocumentSnapshot {
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.get("preference") } returns preferenceMap
        return snapshot
    }

    @Test
    fun `snapshot 자체가 null이면 기본값을 반환한다`() = runTest {
        val repository = repositoryObservingSnapshot(null)

        assertEquals(UserPreference(), repository.observePreference("uid-1").first())
    }

    @Test
    fun `preference 필드가 없으면 기본값을 반환한다`() = runTest {
        val repository = repositoryObservingSnapshot(snapshotWithPreference(null))

        assertEquals(UserPreference(), repository.observePreference("uid-1").first())
    }

    @Test
    fun `preference의 language, timeZone을 그대로 매핑한다`() = runTest {
        val repository = repositoryObservingSnapshot(
            snapshotWithPreference(mapOf("language" to "ko", "timeZone" to "UTC"))
        )

        assertEquals(
            UserPreference(language = "ko", timeZone = "UTC"),
            repository.observePreference("uid-1").first(),
        )
    }

    @Test
    fun `preference 필드 중 일부만 있으면 나머지는 기본값으로 채운다`() = runTest {
        val repository = repositoryObservingSnapshot(
            snapshotWithPreference(mapOf("timeZone" to "UTC"))
        )

        assertEquals(
            UserPreference(language = "ko", timeZone = "UTC"),
            repository.observePreference("uid-1").first(),
        )
    }

    @Test
    fun `preference 필드 값의 타입이 String이 아니면 기본값으로 대체한다`() = runTest {
        val repository = repositoryObservingSnapshot(
            snapshotWithPreference(mapOf("language" to 1, "timeZone" to "UTC"))
        )

        assertEquals(
            UserPreference(language = "ko", timeZone = "UTC"),
            repository.observePreference("uid-1").first(),
        )
    }
}
