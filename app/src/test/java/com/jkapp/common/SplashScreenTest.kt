package com.jkapp.common

import org.junit.Assert.assertEquals
import org.junit.Test

class SplashScreenTest {

    @Test
    fun `시간이 흐르지 않았으면 전체 지속 시간만큼 대기한다`() {
        assertEquals(2_000L, remainingSplashDelayMs(startTime = 0L, now = 0L, durationMs = 2_000L))
    }

    @Test
    fun `일부 시간이 흘렀으면 남은 시간만 대기한다`() {
        assertEquals(1_500L, remainingSplashDelayMs(startTime = 0L, now = 500L, durationMs = 2_000L))
    }

    @Test
    fun `화면 회전으로 재시작되어도 이미 지난 시간은 다시 대기하지 않는다`() {
        // 회전 전 500ms가 지난 뒤 재생성되어 startTime은 그대로 보존되고, now만 진행된 상황을 가정한다.
        assertEquals(0L, remainingSplashDelayMs(startTime = 0L, now = 3_000L, durationMs = 2_000L))
    }
}
