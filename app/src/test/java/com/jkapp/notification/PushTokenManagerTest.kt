package com.jkapp.notification

import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import com.jkapp.user.FakeUserRepository
import com.jkapp.user.PushToken
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushTokenManagerTest {

    private fun firebaseMessagingReturning(token: String): FirebaseMessaging {
        val task = mockk<Task<String>>()
        every { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<String>>().onSuccess(token)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        return mockk<FirebaseMessaging> { every { this@mockk.token } returns task }
    }

    @Test
    fun `기존 토큰이 없으면 새 토큰을 저장한다`() = runTest {
        val userRepository = FakeUserRepository()
        val manager = PushTokenManager(userRepository, firebaseMessagingReturning("token-a"))

        manager.refreshTokenIfNeeded("uid-1")

        assertEquals(1, userRepository.updatePushTokenCallCount)
        assertEquals("token-a", userRepository.lastUpdatedPushToken?.token)
    }

    @Test
    fun `기존 토큰과 동일하면 저장하지 않는다`() = runTest {
        val userRepository = FakeUserRepository()
        userRepository.updatePushToken("uid-1", PushToken(token = "token-a", updatedAt = 1L))
        val manager = PushTokenManager(userRepository, firebaseMessagingReturning("token-a"))

        manager.refreshTokenIfNeeded("uid-1")

        assertEquals(1, userRepository.updatePushTokenCallCount)
    }

    @Test
    fun `기존 토큰과 다르면 갱신한다`() = runTest {
        val userRepository = FakeUserRepository()
        userRepository.updatePushToken("uid-1", PushToken(token = "token-old", updatedAt = 1L))
        val manager = PushTokenManager(userRepository, firebaseMessagingReturning("token-new"))

        manager.refreshTokenIfNeeded("uid-1")

        assertEquals(2, userRepository.updatePushTokenCallCount)
        assertEquals("token-new", userRepository.lastUpdatedPushToken?.token)
    }

    @Test
    fun `updateTokenIfChanged은 uid에 저장된 토큰이 없어도 정상 동작한다`() = runTest {
        val userRepository = FakeUserRepository()
        val manager = PushTokenManager(userRepository, firebaseMessagingReturning("unused"))

        assertNull(userRepository.getPushToken("uid-1"))
        manager.updateTokenIfChanged("uid-1", "token-a")

        assertEquals("token-a", userRepository.getPushToken("uid-1")?.token)
    }
}
