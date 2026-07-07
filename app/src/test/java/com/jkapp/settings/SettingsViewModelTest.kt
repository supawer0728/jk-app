package com.jkapp.settings

import com.jkapp.auth.FakeAuthRepository
import com.jkapp.common.AppPreferences
import com.jkapp.common.DarkModeSetting
import com.jkapp.user.FakeUserRepository
import com.jkapp.user.UserPreference
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appPreferences: AppPreferences
    private lateinit var darkModeSettingFlow: MutableStateFlow<DarkModeSetting>
    private lateinit var hapticIntensityFlow: MutableStateFlow<Int>
    private lateinit var authRepository: FakeAuthRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        darkModeSettingFlow = MutableStateFlow(DarkModeSetting.SYSTEM)
        hapticIntensityFlow = MutableStateFlow(5)
        appPreferences = mockk(relaxed = true)
        every { appPreferences.darkModeSetting } returns darkModeSettingFlow
        every { appPreferences.hapticIntensity } returns hapticIntensityFlow
        authRepository = FakeAuthRepository()
        userRepository = FakeUserRepository()
        viewModel = SettingsViewModel(appPreferences, authRepository, userRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `초기값은 AppPreferences에 저장된 다크모드 설정을 따른다`() = runTest {
        advanceUntilIdle()

        assertEquals(DarkModeSetting.SYSTEM, viewModel.darkModeSetting.value)
    }

    @Test
    fun `darkModeSetting Flow가 갱신되면 상태도 갱신된다`() = runTest {
        advanceUntilIdle()

        darkModeSettingFlow.value = DarkModeSetting.OFF
        advanceUntilIdle()

        assertEquals(DarkModeSetting.OFF, viewModel.darkModeSetting.value)
    }

    @Test
    fun `setDarkModeSetting 호출 시 AppPreferences에 저장한다`() = runTest {
        viewModel.setDarkModeSetting(DarkModeSetting.ON)
        advanceUntilIdle()

        coVerify { appPreferences.setDarkModeSetting(DarkModeSetting.ON) }
    }

    @Test
    fun `초기값은 AppPreferences에 저장된 햅틱 강도를 따른다`() = runTest {
        advanceUntilIdle()

        assertEquals(5, viewModel.hapticIntensity.value)
    }

    @Test
    fun `hapticIntensity Flow가 갱신되면 상태도 갱신된다`() = runTest {
        advanceUntilIdle()

        hapticIntensityFlow.value = 0
        advanceUntilIdle()

        assertEquals(0, viewModel.hapticIntensity.value)
    }

    @Test
    fun `setHapticIntensity 호출 시 AppPreferences에 저장한다`() = runTest {
        viewModel.setHapticIntensity(8)
        advanceUntilIdle()

        coVerify { appPreferences.setHapticIntensity(8) }
    }

    @Test
    fun `로그아웃 상태에서는 preference가 기본값이다`() = runTest {
        advanceUntilIdle()

        assertEquals(UserPreference(), viewModel.preference.value)
    }

    @Test
    fun `로그인 상태이면 UserRepository의 preference를 따른다`() = runTest {
        authRepository.currentUserId = "uid-1"
        authRepository.setLoggedIn(true)
        advanceUntilIdle()
        userRepository.updatePreference("uid-1", UserPreference(language = "ko", timeZone = "UTC"))
        advanceUntilIdle()

        assertEquals(UserPreference(language = "ko", timeZone = "UTC"), viewModel.preference.value)
    }

    @Test
    fun `로그인 후 로그아웃하면 preference가 기본값으로 되돌아간다`() = runTest {
        authRepository.currentUserId = "uid-1"
        authRepository.setLoggedIn(true)
        advanceUntilIdle()
        userRepository.updatePreference("uid-1", UserPreference(language = "ko", timeZone = "UTC"))
        advanceUntilIdle()
        assertEquals(UserPreference(language = "ko", timeZone = "UTC"), viewModel.preference.value)

        authRepository.setLoggedIn(false)
        advanceUntilIdle()

        assertEquals(UserPreference(), viewModel.preference.value)
    }

    @Test
    fun `setLanguage 호출 시 현재 로그인한 uid로 UserRepository에 반영한다`() = runTest {
        authRepository.currentUserId = "uid-1"
        authRepository.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.setLanguage("ko")
        advanceUntilIdle()

        assertEquals("uid-1", userRepository.lastUpdatedPreferenceUid)
        assertEquals(UserPreference(language = "ko"), userRepository.lastUpdatedPreference)
    }

    @Test
    fun `setTimeZone 호출 시 현재 로그인한 uid로 UserRepository에 반영한다`() = runTest {
        authRepository.currentUserId = "uid-1"
        authRepository.setLoggedIn(true)
        advanceUntilIdle()

        viewModel.setTimeZone("UTC")
        advanceUntilIdle()

        assertEquals("uid-1", userRepository.lastUpdatedPreferenceUid)
        assertEquals(UserPreference(timeZone = "UTC"), userRepository.lastUpdatedPreference)
    }

    @Test
    fun `로그아웃 상태에서 setLanguage를 호출해도 UserRepository에 반영되지 않는다`() = runTest {
        viewModel.setLanguage("ko")
        advanceUntilIdle()

        assertEquals(null, userRepository.lastUpdatedPreferenceUid)
    }
}
