package com.jkapp.data.model

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AkiHealthRecordJsonTest {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(AkiHealthRecord::class.java)

    private val sampleJson = """
        {
          "pet_name": "아키",
          "records": [
            {
              "date": "2025-01-10",
              "day_of_week": "금",
              "record_type": "HOSPITAL_VISIT",
              "hospital_visit": {
                "category": "정기검진",
                "details": "혈액검사 및 심장사상충 예방",
                "note": "이상없음"
              }
            },
            {
              "date": "2025-01-08",
              "day_of_week": "수",
              "record_type": "DAILY_NOTE",
              "daily_notes": ["밥 잘 먹음", "산책 30분"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parse sample JSON to AkiHealthRecord`() {
        val record = adapter.fromJson(sampleJson)!!

        assertEquals("아키", record.petName)
        assertEquals(2, record.records.size)
    }

    @Test
    fun `parse hospital visit record correctly`() {
        val record = adapter.fromJson(sampleJson)!!
        val hospitalRecord = record.records[0]

        assertEquals("2025-01-10", hospitalRecord.date)
        assertEquals("금", hospitalRecord.dayOfWeek)
        assertEquals(RecordType.HOSPITAL_VISIT, hospitalRecord.recordType)
        assertEquals("정기검진", hospitalRecord.hospitalVisit?.category)
        assertEquals("혈액검사 및 심장사상충 예방", hospitalRecord.hospitalVisit?.details)
        assertEquals("이상없음", hospitalRecord.hospitalVisit?.note)
    }

    @Test
    fun `parse daily note record correctly`() {
        val record = adapter.fromJson(sampleJson)!!
        val dailyRecord = record.records[1]

        assertEquals("2025-01-08", dailyRecord.date)
        assertEquals(RecordType.DAILY_NOTE, dailyRecord.recordType)
        assertNull(dailyRecord.hospitalVisit)
        assertEquals(listOf("밥 잘 먹음", "산책 30분"), dailyRecord.dailyNotes)
    }

    @Test
    fun `roundtrip JSON serialization preserves data`() {
        val original = adapter.fromJson(sampleJson)!!
        val json = adapter.toJson(original)
        val restored = adapter.fromJson(json)!!

        assertEquals(original.petName, restored.petName)
        assertEquals(original.records.size, restored.records.size)
        assertEquals(original.records[0].hospitalVisit?.category, restored.records[0].hospitalVisit?.category)
        assertEquals(original.records[1].dailyNotes, restored.records[1].dailyNotes)
    }

    @Test
    fun `optional note field is null when absent`() {
        val jsonWithoutNote = """
            {
              "pet_name": "테스트",
              "records": [
                {
                  "date": "2025-01-01",
                  "day_of_week": "수",
                  "record_type": "HOSPITAL_VISIT",
                  "hospital_visit": {
                    "category": "예방접종",
                    "details": "광견병"
                  }
                }
              ]
            }
        """.trimIndent()

        val record = adapter.fromJson(jsonWithoutNote)!!
        assertNull(record.records[0].hospitalVisit?.note)
    }
}
