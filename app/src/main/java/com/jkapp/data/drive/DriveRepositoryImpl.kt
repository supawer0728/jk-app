package com.jkapp.data.drive

import com.jkapp.data.model.AkiHealthRecord
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.InputStreamContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DriveRepositoryImpl(
    private val moshi: Moshi,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DriveRepository {

    private val adapter by lazy { moshi.adapter(AkiHealthRecord::class.java) }

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedDrive: Drive? = null

    private fun getDrive(accessToken: String): Drive {
        if (cachedToken == accessToken && cachedDrive != null) return cachedDrive!!
        return Drive.Builder(
            GoogleNetHttpTransport.newTrustedTransport(),
            GsonFactory.getDefaultInstance()
        ) { request -> request.headers.authorization = "Bearer $accessToken" }
            .setApplicationName(DriveConfig.APP_NAME)
            .build()
            .also {
                cachedToken = accessToken
                cachedDrive = it
            }
    }

    override suspend fun fetchRecord(accessToken: String): AkiHealthRecord =
        withContext(ioDispatcher) {
            // Google Docs 네이티브 파일은 export API로 읽어야 함 (executeMediaAsInputStream은 404 반환)
            // text/plain export 결과 앞에 BOM(U+FEFF)이 붙을 수 있어 제거 후 파싱
            val raw = getDrive(accessToken)
                .files().export(DriveConfig.FILE_ID, "text/plain")
                .executeAsInputStream()
                .bufferedReader(Charsets.UTF_8)
                .readText()
            val json = raw.trimStart(0xFEFF.toChar()).trim()
            adapter.fromJson(json) ?: error("AkiHealthRecord 파싱 실패")
        }

    override suspend fun saveRecord(accessToken: String, record: AkiHealthRecord) =
        withContext(ioDispatcher) {
            val json = adapter.toJson(record)
            // text/plain으로 업로드하면 Drive가 Google Docs 문서 내용을 교체함
            val content = InputStreamContent("text/plain", json.byteInputStream())
            getDrive(accessToken)
                .files().update(DriveConfig.FILE_ID, null, content)
                .execute()
            Unit
        }
}
