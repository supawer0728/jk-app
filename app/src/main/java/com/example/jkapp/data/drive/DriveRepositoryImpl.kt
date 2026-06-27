package com.example.jkapp.data.drive

import com.example.jkapp.data.model.AkiHealthRecord
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveRepositoryImpl(private val moshi: Moshi) : DriveRepository {

    private val adapter by lazy { moshi.adapter(AkiHealthRecord::class.java) }

    private fun buildDrive(accessToken: String): Drive = Drive.Builder(
        GoogleNetHttpTransport.newTrustedTransport(),
        GsonFactory.getDefaultInstance()
    ) { request -> request.headers.authorization = "Bearer $accessToken" }
        .setApplicationName(DriveConfig.APP_NAME)
        .build()

    override suspend fun fetchRecord(accessToken: String): AkiHealthRecord =
        withContext(Dispatchers.IO) {
            val json = buildDrive(accessToken)
                .files().get(DriveConfig.FILE_ID)
                .executeMediaAsInputStream()
                .bufferedReader()
                .readText()
            adapter.fromJson(json) ?: error("AkiHealthRecord 파싱 실패")
        }

    override suspend fun saveRecord(accessToken: String, record: AkiHealthRecord) =
        withContext(Dispatchers.IO) {
            val json = adapter.toJson(record)
            val content = InputStreamContent("application/json", json.byteInputStream())
            buildDrive(accessToken)
                .files().update(DriveConfig.FILE_ID, null, content)
                .execute()
            Unit
        }
}
