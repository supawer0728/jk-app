package com.jkapp.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import com.jkapp.common.snapshotFlow
import kotlinx.coroutines.flow.Flow

class UserRepositoryImpl(
    private val db: FirebaseFirestore = AppFirestore.instance,
) : UserRepository {

    private val usersRef = db.collection(COLLECTION_USERS)

    override suspend fun upsertUserProfile(uid: String, email: String, displayName: String) {
        val docRef = usersRef.document(uid)
        val exists = docRef.get().await().exists()
        val fields = mutableMapOf<String, Any>(
            FIELD_EMAIL to email,
            FIELD_DISPLAY_NAME to displayName,
            FIELD_LAST_LOGIN_AT to System.currentTimeMillis(),
        )
        // 최초 생성 시에만 preference 기본값을 넣는다. 이미 존재하면 merge가 건드리지 않아 사용자가 저장한 설정이 보존된다.
        if (!exists) {
            fields[FIELD_PREFERENCE] = UserPreference().toFieldMap()
        }
        docRef.set(fields, SetOptions.merge()).await()
    }

    override fun observePreference(uid: String): Flow<UserPreference> =
        usersRef.document(uid).snapshotFlow { it.toUserPreference() }

    override suspend fun updatePreference(uid: String, preference: UserPreference) {
        usersRef.document(uid)
            .set(mapOf(FIELD_PREFERENCE to preference.toFieldMap()), SetOptions.merge())
            .await()
    }

    override suspend fun getPushToken(uid: String): PushToken? =
        usersRef.document(uid).get().await().toPushToken()

    override suspend fun updatePushToken(uid: String, pushToken: PushToken) {
        usersRef.document(uid)
            .set(mapOf(FIELD_PUSH_TOKEN to pushToken.toFieldMap()), SetOptions.merge())
            .await()
    }

    // Firestore whereIn은 최대 30개 값까지 지원한다. 가족 2인 고정 매핑이라 emails는 최대 2개뿐이라
    // 항상 한도 내다. emails가 비어 있으면 whereIn 자체가 유효하지 않은 쿼리라 빈 목록으로 짧게 반환한다.
    override suspend fun getPushTokensByEmails(emails: List<String>): List<UserPushTarget> {
        if (emails.isEmpty()) return emptyList()
        return usersRef.whereIn(FIELD_EMAIL, emails).get().await().documents.mapNotNull { doc ->
            val token = doc.toPushToken()?.token ?: return@mapNotNull null
            UserPushTarget(uid = doc.id, token = token)
        }
    }

    private fun DocumentSnapshot?.toPushToken(): PushToken? {
        val pushTokenMap = this?.get(FIELD_PUSH_TOKEN) as? Map<*, *> ?: return null
        val token = pushTokenMap[FIELD_PUSH_TOKEN_TOKEN] as? String ?: return null
        return PushToken(
            token = token,
            updatedAt = pushTokenMap[FIELD_PUSH_TOKEN_UPDATED_AT] as? Long ?: 0L,
            platform = pushTokenMap[FIELD_PUSH_TOKEN_PLATFORM] as? String ?: "android",
        )
    }

    private fun PushToken.toFieldMap() = mapOf(
        FIELD_PUSH_TOKEN_TOKEN to token,
        FIELD_PUSH_TOKEN_UPDATED_AT to updatedAt,
        FIELD_PUSH_TOKEN_PLATFORM to platform,
    )

    private fun DocumentSnapshot?.toUserPreference(): UserPreference {
        val defaults = UserPreference()
        val preferenceMap = this?.get(FIELD_PREFERENCE) as? Map<*, *>
        return UserPreference(
            language = preferenceMap?.get(FIELD_PREFERENCE_LANGUAGE) as? String ?: defaults.language,
            timeZone = preferenceMap?.get(FIELD_PREFERENCE_TIME_ZONE) as? String ?: defaults.timeZone,
        )
    }

    private fun UserPreference.toFieldMap() = mapOf(
        FIELD_PREFERENCE_LANGUAGE to language,
        FIELD_PREFERENCE_TIME_ZONE to timeZone,
    )

    companion object {
        private const val COLLECTION_USERS = "users"
        private const val FIELD_EMAIL = "email"
        private const val FIELD_DISPLAY_NAME = "displayName"
        private const val FIELD_LAST_LOGIN_AT = "lastLoginAt"
        private const val FIELD_PREFERENCE = "preference"
        private const val FIELD_PREFERENCE_LANGUAGE = "language"
        private const val FIELD_PREFERENCE_TIME_ZONE = "timeZone"
        private const val FIELD_PUSH_TOKEN = "pushToken"
        private const val FIELD_PUSH_TOKEN_TOKEN = "token"
        private const val FIELD_PUSH_TOKEN_UPDATED_AT = "updatedAt"
        private const val FIELD_PUSH_TOKEN_PLATFORM = "platform"
    }
}
