package com.jkapp.common

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun todayDate(): String =
    LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

// DatePicker가 넘겨주는 값은 각 날짜의 UTC 자정 epoch millis다. 이를 기준일(referenceDate, 기본 오늘)의
// UTC 자정 millis와 비교해 미래(기준일 다음날 이후) 여부를 판단한다. IsoDatePickerDialog의 미래 날짜
// 차단(selectableDates)에서 쓰인다. todayDate()가 시스템 시간대를 쓰므로 UTC±12h 경계 조건에서 하루
// 차이가 날 수 있는 것은 IsoDatePickerDialog의 UTC 변환과 동일한 의도적 tradeoff다.
fun isNotAfterUtcDay(utcTimeMillis: Long, referenceDate: String = todayDate()): Boolean {
    val referenceMillis = LocalDate.parse(referenceDate, DateTimeFormatter.ISO_LOCAL_DATE)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    return utcTimeMillis <= referenceMillis
}

// 날짜(yyyy-MM-dd)를 가진 항목들 중 미래 날짜를 제외하고 가장 최신인 항목을 찾는다.
// 기기에 미래 날짜로 잘못 입력된 항목이 최신값으로 집계되지 않도록 여러 뷰모델에서 공통으로 쓰인다.
fun <T> latestNotFuture(items: List<T>, dateOf: (T) -> String): T? {
    val today = todayDate()
    return items.filter { dateOf(it) <= today }.maxByOrNull(dateOf)
}

fun computeDayOfWeek(date: String): String =
    LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
        .dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.KOREAN)
