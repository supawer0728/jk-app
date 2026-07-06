package com.jkapp.diary

data class CatRecordType(
    val id: String,
    val name: String,
    val emoji: String,
    val fontColor: String,
    val backgroundColor: String,
    val docId: String = "",
)
