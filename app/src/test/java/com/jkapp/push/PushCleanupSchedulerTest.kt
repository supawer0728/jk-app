package com.jkapp.push

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 이슈 #89: pushes 30일 정리는 앱 시작/포그라운드 복귀마다 호출되지만 하루 1회만 실제로 실행돼야
// 한다. AppPreferences(Android Context) 없이도 가드 로직만 검증할 수 있도록 get/set 콜백을
// 인메모리 변수로 대체해 테스트한다.
class PushCleanupSchedulerTest {

    private val fixedNow = Instant.parse("2026-07-11T00:00:00Z")
    private val today = LocalDate.of(2026, 7, 11)

    private fun schedulerWithLastCleanupDate(
        initialLastCleanupDate: LocalDate?,
        pushRepository: FakePushRepository,
    ): PushCleanupScheduler {
        var lastCleanupDate = initialLastCleanupDate
        return PushCleanupScheduler(
            pushRepository = pushRepository,
            getLastCleanupDate = { lastCleanupDate },
            setLastCleanupDate = { lastCleanupDate = it },
            zone = ZoneOffset.UTC,
            now = { fixedNow },
        )
    }

    @Test
    fun `마지막 정리 날짜가 없으면(최초 실행) 정리를 실행하고 오늘 날짜로 갱신한다`() = runTest {
        val pushRepository = FakePushRepository()
        val scheduler = schedulerWithLastCleanupDate(initialLastCleanupDate = null, pushRepository)

        scheduler.runIfNeeded()

        assertEquals(1, pushRepository.deleteExpiredPushesCallCount)
        val retentionSeconds = PushCleanupScheduler.RETENTION_DAYS * 24 * 60 * 60
        assertEquals(fixedNow.minusSeconds(retentionSeconds), pushRepository.lastDeleteThreshold)
    }

    @Test
    fun `마지막 정리 날짜가 오늘이면 정리를 건너뛴다`() = runTest {
        val pushRepository = FakePushRepository()
        val scheduler = schedulerWithLastCleanupDate(initialLastCleanupDate = today, pushRepository)

        scheduler.runIfNeeded()

        assertEquals(0, pushRepository.deleteExpiredPushesCallCount)
    }

    @Test
    fun `마지막 정리 날짜가 오늘이 아니면(예 어제) 정리를 실행한다`() = runTest {
        val pushRepository = FakePushRepository()
        val lastDate = today.minusDays(1)
        val scheduler = schedulerWithLastCleanupDate(lastDate, pushRepository)

        scheduler.runIfNeeded()

        assertEquals(1, pushRepository.deleteExpiredPushesCallCount)
    }

    @Test
    fun `정리 실행 후 마지막 정리 날짜가 오늘로 갱신되어 같은 날 다시 호출하면 스킵된다`() = runTest {
        val pushRepository = FakePushRepository()
        var savedDate: LocalDate? = null
        val scheduler = PushCleanupScheduler(
            pushRepository = pushRepository,
            getLastCleanupDate = { savedDate },
            setLastCleanupDate = { savedDate = it },
            zone = ZoneOffset.UTC,
            now = { fixedNow },
        )

        scheduler.runIfNeeded()
        scheduler.runIfNeeded()

        assertEquals(today, savedDate)
        assertEquals(1, pushRepository.deleteExpiredPushesCallCount)
    }

    @Test
    fun `runIfNeeded를 호출하기 전에는 정리를 실행하지 않는다`() = runTest {
        val pushRepository = FakePushRepository()
        var savedDate: LocalDate? = null
        PushCleanupScheduler(
            pushRepository = pushRepository,
            getLastCleanupDate = { savedDate },
            setLastCleanupDate = { savedDate = it },
            zone = ZoneOffset.UTC,
            now = { fixedNow },
        )

        assertNull(savedDate)
        assertEquals(0, pushRepository.deleteExpiredPushesCallCount)
    }
}
