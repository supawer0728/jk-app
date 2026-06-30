package com.jkapp.data.drive

import com.jkapp.data.model.Attachment
import java.io.InputStream

class FakeDriveRepository : DriveRepository {

    private var accessToken: String = ""
    val uploadedFiles = mutableListOf<Attachment>()
    val deletedFileIds = mutableListOf<String>()

    var uploadError: Throwable? = null
    var deleteError: Throwable? = null
    var downloadError: Throwable? = null
    var sharedRootFolderIdSet: String? = "NOT_CALLED"

    fun reset() {
        uploadedFiles.clear()
        deletedFileIds.clear()
        uploadError = null
        deleteError = null
        downloadError = null
        sharedRootFolderIdSet = "NOT_CALLED"
    }

    override fun setAccount(accountName: String) {
        this.accessToken = accountName
    }

    override fun setSharedRootFolderId(id: String?) {
        sharedRootFolderIdSet = id
    }

    override suspend fun uploadFile(
        recordId: String,
        inputStream: InputStream,
        fileName: String,
        mimeType: String,
    ): Attachment {
        uploadError?.let { throw it }
        return Attachment(
            fileId = "fake-file-id-${uploadedFiles.size}",
            name = fileName,
            mimeType = mimeType,
            size = 1024L,
        ).also { uploadedFiles.add(it) }
    }

    override suspend fun deleteFile(fileId: String) {
        deleteError?.let { throw it }
        deletedFileIds.add(fileId)
    }

    override suspend fun downloadFile(fileId: String): InputStream {
        downloadError?.let { throw it }
        return "fake-content".byteInputStream()
    }
}
