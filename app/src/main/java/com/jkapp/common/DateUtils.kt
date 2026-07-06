package com.jkapp.common

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

fun todayDate(): String =
    LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

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
