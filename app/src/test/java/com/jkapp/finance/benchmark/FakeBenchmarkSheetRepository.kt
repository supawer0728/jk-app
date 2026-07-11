package com.jkapp.finance.benchmark

import kotlinx.coroutines.awaitCancellation

class FakeBenchmarkSheetRepository : BenchmarkSheetRepository {

    // readBenchmarkRows()가 돌려줄 셀 행 목록(0번째 = 헤더).
    var rows: List<List<String>> = emptyList()

    // 설정되어 있으면 readBenchmarkRows()가 이 예외를 던진다(인증 필요/네트워크 오류 재현용).
    var error: Throwable? = null

    // true면 응답하지 않고 계속 대기한다(로딩 취소/타임아웃 재현용).
    var suspendIndefinitely = false

    var selectedAccount: String? = null
    var readCount = 0

    override fun setAccount(accountName: String) {
        selectedAccount = accountName
    }

    override suspend fun readBenchmarkRows(): List<List<String>> {
        readCount++
        error?.let { throw it }
        if (suspendIndefinitely) awaitCancellation()
        return rows
    }
}
