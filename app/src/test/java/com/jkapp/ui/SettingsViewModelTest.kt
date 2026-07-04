package com.jkapp.ui

import com.jkapp.data.AppPreferences
import com.jkapp.data.DarkModeSetting
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
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        darkModeSettingFlow = MutableStateFlow(DarkModeSetting.SYSTEM)
        appPreferences = mockk(relaxed = true)
        every { appPreferences.darkModeSetting } returns darkModeSettingFlow
        viewModel = SettingsViewModel(appPreferences)
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
}
