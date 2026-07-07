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
import com.jkapp.user.FakeLoginHistoryRepository
import com.jkapp.user.FakeUserRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockAuth: FirebaseAuth
    private lateinit var mockCredentialManager: CredentialManager
    private lateinit var fakeUserRepository: FakeUserRepository
    private lateinit var fakeLoginHistoryRepository: FakeLoginHistoryRepository
    private lateinit var viewModel: AuthViewModel
    private val authStateListenerSlot = slot<FirebaseAuth.AuthStateListener>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockAuth = mockk(relaxed = true)
        mockCredentialManager = mockk(relaxed = true)
        fakeUserRepository = FakeUserRepository()
        fakeLoginHistoryRepository = FakeLoginHistoryRepository()
        every { mockAuth.currentUser } returns null
        every { mockAuth.addAuthStateListener(capture(authStateListenerSlot)) } just Runs
        viewModel = AuthViewModel(
            app = mockk<Application>(relaxed = true),
            auth = mockAuth,
            credentialManager = mockCredentialManager,
            userRepository = fakeUserRepository,
            loginHistoryRepository = fakeLoginHistoryRepository,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 성공 시나리오: success 리스너만 발화, failure 리스너는 발화하지 않음 */
    private fun givenSignInSucceeds(mockUser: FirebaseUser) {
        every { mockUser.uid } returns "test-uid"
        every { mockUser.email } returns "test@example.com"
        every { mockUser.displayName } returns "Test User"
        val mockAuthResult = mockk<AuthResult> { every { user } returns mockUser }
        val task = mockk<Task<AuthResult>>()
        every { task.addOnSuccessListener(any()) } answers {
            firstArg<OnSuccessListener<AuthResult>>().onSuccess(mockAuthResult)
            task
        }
        every { task.addOnFailureListener(any()) } returns task
        every { mockAuth.signInWithCredential(any()) } returns task
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
    fun `초기 isAuthReady는 false이다`() {
        assertFalse(viewModel.isAuthReady.value)
    }

    @Test
    fun `authStateListener가 발화하면 isAuthReady가 true가 되고 user가 갱신된다`() {
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns mockUser

        authStateListenerSlot.captured.onAuthStateChanged(mockAuth)

        assertTrue(viewModel.isAuthReady.value)
        assertEquals(mockUser, viewModel.user.value)
    }

    @Test
    fun `authStateListener가 로그아웃 상태로 발화해도 isAuthReady는 true가 된다`() {
        authStateListenerSlot.captured.onAuthStateChanged(mockAuth)

        assertTrue(viewModel.isAuthReady.value)
        assertNull(viewModel.user.value)
    }

    @Test
    fun `authStateListener가 타임아웃 내에 발화하지 않으면 isAuthReady가 강제로 true가 된다`() = runTest {
        assertFalse(viewModel.isAuthReady.value)

        advanceUntilIdle()

        assertTrue(viewModel.isAuthReady.value)
    }

    @Test
    fun `authStateListener가 타임아웃 전에 발화하면 강제 완료 없이 그 상태를 유지한다`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        every { mockAuth.currentUser } returns mockUser

        authStateListenerSlot.captured.onAuthStateChanged(mockAuth)
        advanceUntilIdle()

        assertTrue(viewModel.isAuthReady.value)
        assertEquals(mockUser, viewModel.user.value)
    }

    @Test
    fun `ViewModel이 clear되면 authStateListener가 해제된다`() {
        val onClearedMethod = AuthViewModel::class.java.getDeclaredMethod("onCleared")
        onClearedMethod.isAccessible = true

        onClearedMethod.invoke(viewModel)

        verify { mockAuth.removeAuthStateListener(authStateListenerSlot.captured) }
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
    fun `firebaseAuthWithGoogle 성공 시 userRepository_upsertUserProfile과 loginHistoryRepository_recordLogin이 호출되고 user_onResult가 갱신된다`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        givenSignInSucceeds(mockUser)

        var callbackResult: Boolean? = null
        viewModel.firebaseAuthWithGoogle("test-id-token") { callbackResult = it }
        advanceUntilIdle()

        assertEquals("test-uid", fakeUserRepository.lastUpsertedUid)
        assertEquals("test@example.com", fakeUserRepository.lastUpsertedEmail)
        assertEquals("Test User", fakeUserRepository.lastUpsertedDisplayName)
        assertEquals("test-uid", fakeLoginHistoryRepository.lastUid)
        assertEquals(mockUser, viewModel.user.value)
        assertTrue(callbackResult == true)
    }

    // Firebase는 signInWithCredential 성공 시 authStateListener를 비동기로 발화시켜 _user를 먼저 갱신할 수 있다.
    // 이후 프로필 upsert/로그인 기록이 실패하면 그 세션을 실제로 롤백해야 로그인 실패와 user 상태가 일치한다.
    private fun givenAuthStateListenerAlreadyFired(mockUser: FirebaseUser) {
        every { mockAuth.currentUser } returns mockUser
        authStateListenerSlot.captured.onAuthStateChanged(mockAuth)
    }

    @Test
    fun `userRepository_upsertUserProfile이 실패하면 signOut으로 세션이 롤백되고 user는 null을 유지한다`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        givenSignInSucceeds(mockUser)
        givenAuthStateListenerAlreadyFired(mockUser)
        fakeUserRepository.upsertUserProfileError = RuntimeException("firestore error")

        var callbackResult: Boolean? = null
        viewModel.firebaseAuthWithGoogle("test-id-token") { callbackResult = it }
        advanceUntilIdle()

        assertNull(viewModel.user.value)
        assertTrue(callbackResult == false)
        assertEquals(0, fakeLoginHistoryRepository.recordCallCount)
        verify { mockAuth.signOut() }
    }

    @Test
    fun `loginHistoryRepository_recordLogin이 실패하면 signOut으로 세션이 롤백되고 user는 null을 유지한다`() = runTest {
        val mockUser = mockk<FirebaseUser>()
        givenSignInSucceeds(mockUser)
        givenAuthStateListenerAlreadyFired(mockUser)
        fakeLoginHistoryRepository.recordLoginError = RuntimeException("firestore error")

        var callbackResult: Boolean? = null
        viewModel.firebaseAuthWithGoogle("test-id-token") { callbackResult = it }
        advanceUntilIdle()

        assertNull(viewModel.user.value)
        assertTrue(callbackResult == false)
        verify { mockAuth.signOut() }
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
