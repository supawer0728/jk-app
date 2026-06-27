package com.example.jkapp.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AkiHealthRecord(
    @Json(name = "pet_name") val petName: String,
    val records: List<HealthRecord>
)

@JsonClass(generateAdapter = true)
data class HealthRecord(
    val date: String,
    @Json(name = "day_of_week") val dayOfWeek: String,
    @Json(name = "record_type") val recordType: RecordType,
    @Json(name = "hospital_visit") val hospitalVisit: HospitalVisit? = null,
    @Json(name = "daily_notes") val dailyNotes: List<String>? = null
)

enum class RecordType {
    HOSPITAL_VISIT,
    DAILY_NOTE
}

@JsonClass(generateAdapter = true)
data class HospitalVisit(
    val category: String,
    val details: String,
    val note: String? = null
)
