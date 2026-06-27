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
            // Google Docs 네이티브 파일은 export API로 읽어야 함 (executeMediaAsInputStream은 404 반환)
            val json = buildDrive(accessToken)
                .files().export(DriveConfig.FILE_ID, "text/plain")
                .executeAsInputStream()
                .bufferedReader()
                .readText()
            adapter.fromJson(json) ?: error("AkiHealthRecord 파싱 실패")
        }

    override suspend fun saveRecord(accessToken: String, record: AkiHealthRecord) =
        withContext(Dispatchers.IO) {
            val json = adapter.toJson(record)
            // text/plain으로 업로드하면 Drive가 Google Docs 문서 내용을 교체함
            val content = InputStreamContent("text/plain", json.byteInputStream())
            buildDrive(accessToken)
                .files().update(DriveConfig.FILE_ID, null, content)
                .execute()
            Unit
        }
}
