package com.jkapp.push

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// 30일 지난 pushes 문서를 하루 1회만 정리한다(이슈 #89). 앱 시작/포그라운드 복귀마다 호출돼도,
// 마지막 실행 날짜(get/set 콜백으로 주입, 실제로는 common.AppPreferences가 DataStore로 캐싱)가
// 오늘과 같으면 즉시 반환해 중복 Firestore 쿼리를 막는다. AppPreferences(Android Context 필요)를
// 직접 참조하지 않아 JVM 단위 테스트에서 가드 로직만 독립적으로 검증할 수 있다.
class PushCleanupScheduler(
    private val pushRepository: PushRepository,
    private val getLastCleanupDate: suspend () -> LocalDate?,
    private val setLastCleanupDate: suspend (LocalDate) -> Unit,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Instant = Instant::now,
) {
    suspend fun runIfNeeded() {
        val today = now().atZone(zone).toLocalDate()
        if (getLastCleanupDate() == today) return

        pushRepository.deleteExpiredPushes(now().minus(RETENTION_DAYS, ChronoUnit.DAYS))
        setLastCleanupDate(today)
    }

    companion object {
        const val RETENTION_DAYS = 30L
    }
}
