package com.jkapp.finance.investment

import android.content.Intent

// 시트 접근 동의가 아직 안 되어 있을 때, UI가 복구 인텐트(계정 선택/권한 동의 화면)를 실행하도록
// 던지는 예외. Drive의 DriveAuthRequiredException·벤치마크의 BenchmarkSheetAuthException과 동일한 패턴.
class InvestmentSheetAuthException(val recoveryIntent: Intent) : Exception("구글시트 접근 권한 동의가 필요합니다.")

// 한 명의(열 블록)에서 읽어온 원본 셀 행. rows의 0번째는 헤더 행, 이후는 데이터 행이다.
data class InvestmentSheetBlock(val owner: String, val rows: List<List<String>>)

// 고정된 원본 구글시트(JK-APP raw)에서 명의별 투자 종목 블록을 읽어오는 저장소.
// 앱은 시트를 읽기만 하므로 읽기 전용 scope만 사용한다(doc/adr/73 참고).
interface InvestmentSheetRepository {
    fun setAccount(accountName: String)

    // 명의별 열 블록(전지훈 H:N, 권유경 P:V)을 각각 헤더 행 + 데이터 행 전체로 읽어온다.
    // 접근 동의가 필요하면 InvestmentSheetAuthException을 던진다.
    suspend fun readInvestmentBlocks(): List<InvestmentSheetBlock>

    companion object {
        // 미리보기/테스트 등에서 실제 시트 접근 없이 주입할 수 있는 기본 구현.
        val NoOp: InvestmentSheetRepository = object : InvestmentSheetRepository {
            override fun setAccount(accountName: String) {}
            override suspend fun readInvestmentBlocks(): List<InvestmentSheetBlock> = emptyList()
        }
    }
}
