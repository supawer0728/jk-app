package com.jkapp.user

import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun upsertUserProfile(uid: String, email: String, displayName: String)
    fun observePreference(uid: String): Flow<UserPreference>
    suspend fun updatePreference(uid: String, preference: UserPreference)
    suspend fun getPushToken(uid: String): PushToken?
    suspend fun updatePushToken(uid: String, pushToken: PushToken)

    // emails에 해당하는 사용자 중 pushToken이 있는 사용자만 uid·token 쌍으로 반환한다(이슈 #89).
    // 편집자 본인 제외 등 발송 대상의 최종 판단은 호출한 feature가 uid로 수행한다. emails가 비어
    // 있으면 빈 목록을 반환한다.
    suspend fun getPushTokensByEmails(emails: List<String>): List<UserPushTarget>
}
