package com.jkapp.diary

import com.jkapp.drive.Attachment

data class CatRecord(
    val firestoreId: String? = null,
    val date: String,
    val recordType: String,
    val record: String,
    val attachments: List<Attachment> = emptyList(),
)
