package com.example.jkapp.data.drive

import com.example.jkapp.data.model.AkiHealthRecord

interface DriveRepository {
    suspend fun fetchRecord(accessToken: String): AkiHealthRecord
    suspend fun saveRecord(accessToken: String, record: AkiHealthRecord)
}
