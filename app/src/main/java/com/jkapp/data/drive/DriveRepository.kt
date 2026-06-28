package com.jkapp.data.drive

import com.jkapp.data.model.Attachment
import java.io.InputStream

interface DriveRepository {
    fun setAccessToken(accessToken: String)
    suspend fun uploadFile(recordId: String, inputStream: InputStream, fileName: String, mimeType: String): Attachment
    suspend fun deleteFile(fileId: String)
    suspend fun downloadFile(fileId: String): InputStream
}
