package com.jkapp.user

class FakeLoginHistoryRepository : LoginHistoryRepository {

    var recordLoginError: Throwable? = null
    var lastUid: String? = null
    var lastDevice: LoginDevice? = null
    var recordCallCount = 0

    override suspend fun recordLogin(uid: String, device: LoginDevice) {
        recordCallCount++
        recordLoginError?.let { throw it }
        lastUid = uid
        lastDevice = device
    }
}
