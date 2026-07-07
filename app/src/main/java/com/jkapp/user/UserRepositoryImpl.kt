package com.jkapp.user

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.jkapp.common.AppFirestore
import com.jkapp.common.await

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
    }
}
