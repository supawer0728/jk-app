package com.jkapp.user

// darkMode/hapticIntensity는 기기별로 다르게 유지되어야 해서 제외한다 — AppPreferences(DataStore)가 로컬 담당.
data class UserPreference(
    val language: String = "ko",
    val timeZone: String = "Asia/Seoul",
)
