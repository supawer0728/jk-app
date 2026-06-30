package com.jkapp.auth

import android.app.Application
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockCredentialManager: CredentialManager
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAuth = mockk(relaxed = true)
        mockCredentialManager = mockk(relaxed = true)
        every { mockAuth.currentUser } returns null
        viewModel = AuthViewModel(
            app = mockk<Application>(relaxed = true),
            auth = mockAuth,
            credentialManager = mockCredentialManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 성공 시나리오: success 리스너만 발화, failure 리스너는 발화하지 않음 */
    private fun givenSignInSucceeds(mockUser: FirebaseUser): AuthResult {
        val mockAuthResult = mockk<AuthResult> { every { user } returns mockUser }
        val task = mockk<Task<AuthResult>>()
        every { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<AuthResult>>().onSuccess(mockAuthResult)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        every { mockAuth.signInWithCredential(any()) } returns task
        return mockAuthResult
    }

    /** 실패 시나리오: failure 리스너만 발화, success 리스너는 발화하지 않음 */
    private fun givenSignInFails(ex: Exception = RuntimeException("auth failed")) {
        val task = mockk<Task<AuthResult>>()
        every { task.addOnSuccessListener(any()) } returns task
        every { task.addOnFailureListener(any()) } answers {
            firstArg<OnFailureListener>().onFailure(ex)
            task
        }
        every { mockAuth.signInWithCredential(any()) } returns task
    }

    @Test
    fun `firebaseAuthWithGoogle 성공 시 user가 authResult의 user로 업데이트된다`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        givenSignInSucceeds(mockUser)

        var callbackResult: Boolean? = null
        viewModel.firebaseAuthWithGoogle("test-id-token") { callbackResult = it }
        advanceUntilIdle()

        assertEquals(mockUser, viewModel.user.value)
        assertTrue(callbackResult == true)
    }

    @Test
    fun `firebaseAuthWithGoogle 성공 시 auth_currentUser가 아닌 authResult_user를 사용한다`() = runTest {
        // auth.currentUser는 null이지만 authResult.user는 실제 유저 — 이슈 #27 레이스 컨디션 재현
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns null
        givenSignInSucceeds(mockUser)

        viewModel.firebaseAuthWithGoogle("test-id-token") {}
        advanceUntilIdle()

        assertEquals(mockUser, viewModel.user.value)
    }

    @Test
    fun `firebaseAuthWithGoogle 실패 시 user가 null을 유지하고 onResult가 false를 받는다`() = runTest {
        givenSignInFails()

        var callbackResult: Boolean? = null
        viewModel.firebaseAuthWithGoogle("test-id-token") { callbackResult = it }
        advanceUntilIdle()

        assertNull(viewModel.user.value)
        assertTrue(callbackResult == false)
    }

    @Test
    fun `signOut 시 user가 null로 업데이트되고 auth_signOut이 호출된다`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        givenSignInSucceeds(mockUser)

        viewModel.firebaseAuthWithGoogle("test-id-token") {}
        advanceUntilIdle()
        assertEquals(mockUser, viewModel.user.value)

        viewModel.signOut()
        advanceUntilIdle()

        assertNull(viewModel.user.value)
        verify { mockAuth.signOut() }
    }

    @Test
    fun `signOut 시 credentialManager_clearCredentialState가 호출된다`() = runTest {
        coEvery { mockCredentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) } returns mockk()

        viewModel.signOut()
        advanceUntilIdle()

        coVerify { mockCredentialManager.clearCredentialState(any<ClearCredentialStateRequest>()) }
    }
}
