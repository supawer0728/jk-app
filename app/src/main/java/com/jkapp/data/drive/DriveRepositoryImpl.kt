package com.jkapp.data.drive

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.jkapp.data.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class DriveRepositoryImpl : DriveRepository {

    private var accessToken: String = ""
    private val folderIdCache = mutableMapOf<String, String>()

    override fun setAccessToken(accessToken: String) {
        this.accessToken = accessToken
        folderIdCache.clear()
    }

    override suspend fun uploadFile(
        recordId: String,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
    ): Attachment = withContext(Dispatchers.IO) {
        val drive = buildDriveService()
        val folderId = getOrCreateAttachmentFolder(drive, recordId)

        val fileMetadata = DriveFile().apply {
            name = fileName
            parents = listOf(folderId)
        }
        val uploaded = inputStream.use { stream ->
            drive.files().create(fileMetadata, InputStreamContent(mimeType, stream))
                .setFields("id,name,mimeType,size")
                .execute()
        }

        Attachment(
            fileId = uploaded.id,
            name = uploaded.name ?: fileName,
            mimeType = uploaded.mimeType ?: mimeType,
            size = uploaded.getSize() ?: 0L,
        )
    }

    override suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        buildDriveService().files().delete(fileId).execute()
    }

    override suspend fun downloadFile(fileId: String): InputStream = withContext(Dispatchers.IO) {
        buildDriveService().files().get(fileId).executeMediaAsInputStream()
    }

    @Suppress("DEPRECATION")
    private fun buildDriveService(): Drive =
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            GoogleCredential().setAccessToken(accessToken),
        ).setApplicationName("jkapp").build()

    private fun getOrCreateAttachmentFolder(drive: Drive, recordId: String): String {
        val jkappId = folderIdCache.getOrPut("jkapp") {
            getOrCreateFolder(drive, "jkapp", null)
        }
        val catRecordId = folderIdCache.getOrPut("cat-record") {
            getOrCreateFolder(drive, "cat-record", jkappId)
        }
        val recordDirId = folderIdCache.getOrPut("record/$recordId") {
            getOrCreateFolder(drive, recordId, catRecordId)
        }
        return folderIdCache.getOrPut("attachment/$recordId") {
            getOrCreateFolder(drive, "attachment", recordDirId)
        }
    }

    private fun getOrCreateFolder(drive: Drive, name: String, parentId: String?): String {
        val query = buildString {
            append("mimeType = '$MIME_TYPE_FOLDER' and name = '$name' and trashed = false")
            if (parentId != null) append(" and '$parentId' in parents")
        }
        val existing = drive.files().list()
            .setQ(query)
            .setFields("files(id)")
            .execute()
            .files

        if (existing.isNotEmpty()) return existing[0].id

        val folder = DriveFile().apply {
            this.name = name
            mimeType = MIME_TYPE_FOLDER
            if (parentId != null) parents = listOf(parentId)
        }
        return drive.files().create(folder).setFields("id").execute().id
    }

    companion object {
        private const val MIME_TYPE_FOLDER = "application/vnd.google-apps.folder"
    }
}
