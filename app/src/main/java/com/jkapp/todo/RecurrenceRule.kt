package com.jkapp.todo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

enum class RecurrenceFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }

// daysOfWeek는 java.time.DayOfWeek.getValue() 값(1=월요일~7=일요일)을 쓰며 WEEKLY에서만 의미를 갖는다.
// anchorDay는 MONTHLY/YEARLY에서만 의미를 갖는다. 원래 지정된 일자(1~31)를 고정해두면 말일 클램프로
// dueAt이 줄어든 뒤에도(예: 1/31 -> 2/28) 다음 전진에서 원래 일자로 복원된다(예: 2/28 -> 3/31).
// null이면 anchorDay가 없던 기존 항목처럼 직전 dueAt의 day-of-month를 그대로 써서 클램프가 누적된다.
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val daysOfWeek: Set<Int> = emptySet(),
    val endAt: Instant? = null,
    val anchorDay: Int? = null,
)

private const val DAYS_PER_WEEK = 7L

// 완료된 회차(currentDueAt)를 기준으로 다음 회차의 dueAt을 계산한다.
fun RecurrenceRule.nextDueAt(currentDueAt: Instant): Instant = when (frequency) {
    RecurrenceFrequency.DAILY -> currentDueAt.plus(interval.toLong(), ChronoUnit.DAYS)
    RecurrenceFrequency.WEEKLY -> nextWeeklyDueAt(currentDueAt)
    RecurrenceFrequency.MONTHLY -> currentDueAt.plusCalendarUnit(anchorDay) { plusMonths(interval.toLong()) }
    RecurrenceFrequency.YEARLY -> currentDueAt.plusCalendarUnit(anchorDay) { plusYears(interval.toLong()) }
}

// daysOfWeek가 비어 있으면 interval주 단위로 전진한다. 지정되어 있으면 같은 주기 내
// 다음 지정 요일로 전진하고, 마지막 지정 요일을 지나면(또는 현재 요일이 지정 요일이 아니면)
// interval주 뒤 첫 지정 요일로 이동한다.
private fun RecurrenceRule.nextWeeklyDueAt(currentDueAt: Instant): Instant {
    if (daysOfWeek.isEmpty()) {
        return currentDueAt.plus(interval * DAYS_PER_WEEK, ChronoUnit.DAYS)
    }
    val currentDayOfWeek = currentDueAt.atZone(ZoneOffset.UTC).dayOfWeek.value
    val sortedDays = daysOfWeek.sorted()
    val daysOffset = sortedDays.firstOrNull { it > currentDayOfWeek }?.minus(currentDayOfWeek)
        ?: (sortedDays.first() - currentDayOfWeek + DAYS_PER_WEEK + DAYS_PER_WEEK * (interval - 1))
    return currentDueAt.plus(daysOffset.toLong(), ChronoUnit.DAYS)
}

// LocalDate.plusMonths/plusYears로 월/연을 전진시킨 뒤, anchorDay가 있으면 전진된 달의 실제 말일에
// 맞춰 day-of-month를 anchorDay로 복원한다(예: anchorDay=31, 4월 -> 4/30). anchorDay가 없으면
// 직전 dueAt의 day-of-month를 그대로 써서 java.time의 기본 클램프 동작에 맡긴다.
private fun Instant.plusCalendarUnit(anchorDay: Int?, transform: LocalDate.() -> LocalDate): Instant {
    val zoned = atZone(ZoneOffset.UTC)
    val advancedDate = zoned.toLocalDate().transform()
    val targetDay = anchorDay ?: zoned.dayOfMonth
    val resolvedDate = advancedDate.withDayOfMonth(minOf(targetDay, advancedDate.lengthOfMonth()))
    return resolvedDate.atTime(zoned.toLocalTime()).atZone(ZoneOffset.UTC).toInstant()
}
