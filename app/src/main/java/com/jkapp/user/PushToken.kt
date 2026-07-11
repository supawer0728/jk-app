package com.jkapp.user

// 향후 갱신 시각 기반 만료 판단(예: 1년 이상 미갱신 토큰 정리)이 필요할 수 있어 문자열이 아닌 맵으로 감싼다.
data class PushToken(
    val token: String,
    val updatedAt: Long,
    val platform: String = "android",
)

// UserRepository.getPushTokensByEmails의 반환 요소(이슈 #89). 호출한 feature가 uid로 편집자
// 본인을 걸러낸 뒤 token만 남겨 push 발송 대상으로 쓴다.
data class UserPushTarget(
    val uid: String,
    val token: String,
)
