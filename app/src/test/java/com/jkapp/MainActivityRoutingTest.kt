package com.jkapp

import com.jkapp.nav.HomeRoute
import com.jkapp.nav.LoginRoute
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
}
