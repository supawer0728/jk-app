package com.jkapp.finance.investment


sealed interface DailyAssetInvestmentUiState {
    data object Loading : DailyAssetInvestmentUiState
    data class Success(val investments: List<DailyAssetInvestment>) : DailyAssetInvestmentUiState
    data class Error(val message: String) : DailyAssetInvestmentUiState
}

// 한 명의 블록에서 시트를 읽어 파싱한 결과(성공/오류 혼재). 저장 전 미리보기로 명의별로 나열한다.
data class InvestmentSheetImportBlock(val owner: String, val rows: List<ParsedInvestmentRow>)

// 구글시트에서 가져오기 흐름의 상태. 버튼 클릭 → Loading → (성공) Preview → 확인 후 저장.
sealed interface InvestmentSheetImportState {
    data object Idle : InvestmentSheetImportState
    data object Loading : InvestmentSheetImportState
    // 두 명의(전지훈·권유경) 블록의 파싱 결과. 미리보기 다이얼로그가 명의별 섹션으로 보여준다.
    data class Preview(val blocks: List<InvestmentSheetImportBlock>) : InvestmentSheetImportState
}
