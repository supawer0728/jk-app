package com.jkapp.finance.benchmark

import android.content.Intent

// 시트 접근 동의가 아직 안 되어 있을 때, UI가 복구 인텐트(계정 선택/권한 동의 화면)를 실행하도록
// 던지는 예외. Drive의 DriveAuthRequiredException과 동일한 패턴을 따른다.
class BenchmarkSheetAuthException(val recoveryIntent: Intent) : Exception("구글시트 접근 권한 동의가 필요합니다.")

// 고정된 원본 구글시트(JK-APP raw)에서 벤치마크 원본 행을 읽어오는 저장소.
// 앱은 시트를 읽기만 하므로 읽기 전용 scope만 사용한다(doc/adr/73 참고).
interface BenchmarkSheetRepository {
    fun setAccount(accountName: String)

    // 시트에서 헤더 행(반환 목록의 0번째)과 데이터 행 전체를 셀 문자열 목록으로 읽어온다.
    // 접근 동의가 필요하면 BenchmarkSheetAuthException을 던진다.
    suspend fun readBenchmarkRows(): List<List<String>>

    companion object {
        // 미리보기/테스트 등에서 실제 시트 접근 없이 주입할 수 있는 기본 구현.
        val NoOp: BenchmarkSheetRepository = object : BenchmarkSheetRepository {
            override fun setAccount(accountName: String) {}
            override suspend fun readBenchmarkRows(): List<List<String>> = emptyList()
        }
    }
}
