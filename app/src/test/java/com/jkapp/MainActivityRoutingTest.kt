package com.jkapp

import com.jkapp.nav.HomeRoute
import com.jkapp.nav.LoginRoute
import com.jkapp.nav.SplashRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityRoutingTest {

    @Test
    fun `로그인 상태면 HomeRoute로 이동한다`() {
        assertEquals(HomeRoute, resolveAuthRoute(isLoggedIn = true))
    }

    @Test
    fun `로그인하지 않았으면 LoginRoute로 이동한다`() {
        assertEquals(LoginRoute, resolveAuthRoute(isLoggedIn = false))
    }

    @Test
    fun `최소 노출 시간이 지나지 않았으면 인증 준비 여부와 무관하게 SplashRoute다`() {
        assertEquals(
            SplashRoute,
            resolveInitialRoute(minSplashDurationElapsed = false, isAuthReady = true, isLoggedIn = true),
        )
    }

    @Test
    fun `인증 준비가 끝나지 않았으면 최소 노출 시간이 지났어도 SplashRoute다`() {
        assertEquals(
            SplashRoute,
            resolveInitialRoute(minSplashDurationElapsed = true, isAuthReady = false, isLoggedIn = true),
        )
    }

    @Test
    fun `최소 노출 시간과 인증 준비가 모두 끝나면 로그인 상태에 따른 라우트로 계산된다`() {
        assertEquals(
            HomeRoute,
            resolveInitialRoute(minSplashDurationElapsed = true, isAuthReady = true, isLoggedIn = true),
        )
        assertEquals(
            LoginRoute,
            resolveInitialRoute(minSplashDurationElapsed = true, isAuthReady = true, isLoggedIn = false),
        )
    }

    @Test
    fun `navigateIfChanged은 타겟이 마지막 항목과 같으면 스택을 바꾸지 않는다`() {
        val backStack = mutableListOf<Any>(SplashRoute, HomeRoute)

        navigateIfChanged(backStack, HomeRoute)

        assertEquals(listOf(SplashRoute, HomeRoute), backStack)
    }

    @Test
    fun `navigateIfChanged은 타겟이 마지막 항목과 다르면 스택을 target 하나로 교체한다`() {
        val backStack = mutableListOf<Any>(SplashRoute)

        navigateIfChanged(backStack, HomeRoute)

        assertEquals(listOf(HomeRoute), backStack)
    }
}
