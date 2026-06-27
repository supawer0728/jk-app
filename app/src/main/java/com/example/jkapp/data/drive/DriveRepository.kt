package com.jkapp.data.drive

import com.jkapp.data.model.AkiHealthRecord

interface DriveRepository {
    suspend fun fetchRecord(accessToken: String): AkiHealthRecord
    suspend fun saveRecord(accessToken: String, record: AkiHealthRecord)
}
