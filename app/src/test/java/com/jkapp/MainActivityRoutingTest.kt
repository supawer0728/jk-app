package com.jkapp

import com.jkapp.nav.HomeRoute
import com.jkapp.nav.LoginRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityRoutingTest {

    @Test
    fun `시간이 흐르지 않았으면 전체 지속 시간만큼 대기한다`() {
        assertEquals(1_500L, remainingSplashDelayMs(splashStartTime = 0L, now = 0L, durationMs = 1_500L))
    }

    @Test
    fun `일부 시간이 흘렀으면 남은 시간만 대기한다`() {
        assertEquals(1_000L, remainingSplashDelayMs(splashStartTime = 0L, now = 500L, durationMs = 1_500L))
    }

    @Test
    fun `화면 회전으로 재시작되어도 이미 지난 시간은 다시 대기하지 않는다`() {
        // 회전 전 500ms가 지난 뒤 재생성되어 splashStartTime은 그대로 보존되고, now만 진행된 상황을 가정한다.
        assertEquals(0L, remainingSplashDelayMs(splashStartTime = 0L, now = 2_000L, durationMs = 1_500L))
    }

    @Test
    fun `로그인 상태면 HomeRoute로 이동한다`() {
        assertEquals(HomeRoute, resolveAuthRoute(isLoggedIn = true))
    }

    @Test
    fun `로그인하지 않았으면 LoginRoute로 이동한다`() {
        assertEquals(LoginRoute, resolveAuthRoute(isLoggedIn = false))
    }
}
