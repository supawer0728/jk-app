package com.jkapp.finance.benchmark


sealed interface BenchmarkUiState {
    data object Loading : BenchmarkUiState
    data class Success(val benchmarks: List<Benchmark>) : BenchmarkUiState
    data class Error(val message: String) : BenchmarkUiState
}

// 구글시트에서 가져오기 흐름의 상태. 버튼 클릭 → Loading → (성공) Preview → 확인 후 저장.
sealed interface BenchmarkSheetImportState {
    data object Idle : BenchmarkSheetImportState
    data object Loading : BenchmarkSheetImportState
    // 시트에서 읽어 파싱한 행들(성공/오류 혼재). 저장 전 미리보기로 보여준다.
    data class Preview(val rows: List<ParsedBenchmarkRow>) : BenchmarkSheetImportState
}
