package com.jkapp.data.drive

import com.jkapp.data.model.Attachment
import java.io.InputStream

interface DriveRepository {
    fun setAccount(accountName: String)
    suspend fun uploadFile(recordId: String, inputStream: InputStream, fileName: String, mimeType: String): Attachment
    suspend fun deleteFile(fileId: String)
    suspend fun downloadFile(fileId: String): InputStream

    companion object {
        val NoOp: DriveRepository = object : DriveRepository {
            override fun setAccount(accountName: String) {}
            override suspend fun uploadFile(recordId: String, inputStream: InputStream, fileName: String, mimeType: String): Attachment =
                throw IllegalStateException("Drive 계정이 초기화되지 않았습니다.")
            override suspend fun deleteFile(fileId: String) {}
            override suspend fun downloadFile(fileId: String): InputStream =
                throw IllegalStateException("Drive 계정이 초기화되지 않았습니다.")
        }
    }
}
