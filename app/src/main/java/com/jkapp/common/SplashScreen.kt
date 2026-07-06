package com.jkapp.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.jkapp.R
import kotlinx.coroutines.delay

private const val MIN_SPLASH_DURATION_MS = 2_000L

// 화면 회전 등으로 재구성되어도 startTime은 rememberSaveable로 보존되므로,
// 이미 흘러간 시간만큼을 제외한 나머지 시간만 대기한다. 그렇지 않으면 회전을 반복할 때마다
// 최소 노출 시간이 매번 처음부터 다시 시작되어 스플래시가 계속 늘어난다.
internal fun remainingSplashDelayMs(
    startTime: Long,
    now: Long,
    durationMs: Long = MIN_SPLASH_DURATION_MS,
): Long = (durationMs - (now - startTime)).coerceAtLeast(0L)

@Composable
fun SplashScreen(onMinDurationElapsed: () -> Unit) {
    val startTime = rememberSaveable { System.currentTimeMillis() }

    LaunchedEffect(Unit) {
        delay(remainingSplashDelayMs(startTime, System.currentTimeMillis()))
        onMinDurationElapsed()
    }

    Image(
        painter = painterResource(id = R.drawable.being),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
