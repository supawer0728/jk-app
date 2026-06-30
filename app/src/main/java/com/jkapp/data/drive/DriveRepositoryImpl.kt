package com.jkapp.data.drive

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.InputStreamContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.jkapp.data.model.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class DriveRepositoryImpl(context: Context) : DriveRepository {

    private val credential = GoogleAccountCredential.usingOAuth2(
        context.applicationContext,
        listOf(DriveScopes.DRIVE),
    )
    private val folderIdCache = mutableMapOf<String, String>()
    private val folderCacheLock = Any()
    @Volatile private var sharedRootFolderId: String? = null

    override fun setAccount(accountName: String) {
        credential.selectedAccountName = accountName
        synchronized(folderCacheLock) { folderIdCache.clear() }
    }

    override fun setSharedRootFolderId(id: String?) {
        sharedRootFolderId = id
        synchronized(folderCacheLock) { folderIdCache.clear() }
    }

    override suspend fun uploadFile(
        recordId: String,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
    ): Attachment = withContext(Dispatchers.IO) {
        if (credential.selectedAccountName == null) {
            throw DriveAuthRequiredException(credential.newChooseAccountIntent())
        }
        try {
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
        } catch (e: UserRecoverableAuthIOException) {
            throw DriveAuthRequiredException(e.intent)
        }
    }

    override suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        if (credential.selectedAccountName == null) return@withContext
        try {
            buildDriveService().files().delete(fileId).execute()
        } catch (_: UserRecoverableAuthIOException) {
            // GC best-effort: 인증 동의 미완료 시 무시
        }
    }

    override suspend fun downloadFile(fileId: String): InputStream = withContext(Dispatchers.IO) {
        if (credential.selectedAccountName == null) {
            throw DriveAuthRequiredException(credential.newChooseAccountIntent())
        }
        try {
            buildDriveService().files().get(fileId).executeMediaAsInputStream()
        } catch (e: UserRecoverableAuthIOException) {
            throw DriveAuthRequiredException(e.intent)
        }
    }

    private fun buildDriveService(): Drive =
        Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("jkapp").build()

    private fun getOrCreateAttachmentFolder(drive: Drive, recordId: String): String =
        synchronized(folderCacheLock) {
            val rootId = sharedRootFolderId ?: folderIdCache.getOrPut("jkapp") {
                getOrCreateFolder(drive, "jkapp", null)
            }
            val catRecordId = folderIdCache.getOrPut("cat-record") {
                getOrCreateFolder(drive, "cat-record", rootId)
            }
            val recordDirId = folderIdCache.getOrPut("record/$recordId") {
                getOrCreateFolder(drive, recordId, catRecordId)
            }
            folderIdCache.getOrPut("attachment/$recordId") {
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
