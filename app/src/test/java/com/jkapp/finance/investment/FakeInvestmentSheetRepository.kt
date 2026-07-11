package com.jkapp.finance.investment

import kotlinx.coroutines.awaitCancellation

class FakeInvestmentSheetRepository : InvestmentSheetRepository {

    // readInvestmentBlocks()가 돌려줄 명의별 블록(각 블록 rows의 0번째 = 헤더).
    var blocks: List<InvestmentSheetBlock> = emptyList()

    // 설정되어 있으면 readInvestmentBlocks()가 이 예외를 던진다(인증 필요/네트워크 오류 재현용).
    var error: Throwable? = null

    // true면 응답하지 않고 계속 대기한다(로딩 취소/타임아웃 재현용).
    var suspendIndefinitely = false

    var selectedAccount: String? = null
    var readCount = 0

    override fun setAccount(accountName: String) {
        selectedAccount = accountName
    }

    override suspend fun readInvestmentBlocks(): List<InvestmentSheetBlock> {
        readCount++
        error?.let { throw it }
        if (suspendIndefinitely) awaitCancellation()
        return blocks
    }
}
