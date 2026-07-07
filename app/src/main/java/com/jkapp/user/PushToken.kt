package com.jkapp.user

// 향후 갱신 시각 기반 만료 판단(예: 1년 이상 미갱신 토큰 정리)이 필요할 수 있어 문자열이 아닌 맵으로 감싼다.
data class PushToken(
    val token: String,
    val updatedAt: Long,
    val platform: String = "android",
)
