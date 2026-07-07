package com.jkapp.user

import com.google.firebase.firestore.FirebaseFirestore
import com.jkapp.common.AppFirestore
import com.jkapp.common.await

class LoginHistoryRepositoryImpl(
    private val db: FirebaseFirestore = AppFirestore.instance,
) : LoginHistoryRepository {

    private val loginHistoryRef = db.collection(COLLECTION_LOGIN_HISTORY)

    override suspend fun recordLogin(uid: String, device: LoginDevice) {
        val fields = mapOf(
            FIELD_UID to uid,
            FIELD_LOGIN_AT to System.currentTimeMillis(),
            FIELD_DEVICE to mapOf(
                FIELD_DEVICE_OS to device.os,
                FIELD_DEVICE_OS_VERSION to device.osVersion,
                FIELD_DEVICE_MODEL to device.deviceModel,
                FIELD_DEVICE_APP_VERSION to device.appVersion,
            ),
        )
        loginHistoryRef.add(fields).await()
    }

    companion object {
        private const val COLLECTION_LOGIN_HISTORY = "login-history"
        private const val FIELD_UID = "uid"
        private const val FIELD_LOGIN_AT = "loginAt"
        private const val FIELD_DEVICE = "device"
        private const val FIELD_DEVICE_OS = "os"
        private const val FIELD_DEVICE_OS_VERSION = "osVersion"
        private const val FIELD_DEVICE_MODEL = "deviceModel"
        private const val FIELD_DEVICE_APP_VERSION = "appVersion"
    }
}
