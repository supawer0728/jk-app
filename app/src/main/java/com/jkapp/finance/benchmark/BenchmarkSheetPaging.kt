package com.jkapp.finance.benchmark

// 시트의 첫 데이터 행(헤더가 1행이므로 2행)과 페이지 크기.
internal const val BENCHMARK_SHEET_FIRST_DATA_ROW = 2
internal const val BENCHMARK_SHEET_PAGE_SIZE = 100

// 데이터 행을 100행 단위로 끝까지 읽는다. readPage(startRow, endRow)는 해당 범위의 행을
// 반환하며, 한 페이지가 pageSize보다 적게 오면(시트 API가 데이터 없는 뒷부분을 생략) 마지막
// 페이지로 보고 멈춘다. 실제 시트 API 의존성 없이 단위 테스트할 수 있도록 순수 함수로 둔다.
//
// 전제: 데이터는 첫 행부터 빈 줄 없이 연속으로 채워져 있다(월별 벤치마크 데이터). 중간에
// pageSize를 초과하는 빈 행 구간이 있으면 그 뒤 데이터는 읽지 못하므로, 그런 시트에는 쓰지 않는다.
internal suspend fun collectPagedRows(
    pageSize: Int = BENCHMARK_SHEET_PAGE_SIZE,
    firstRow: Int = BENCHMARK_SHEET_FIRST_DATA_ROW,
    readPage: suspend (startRow: Int, endRow: Int) -> List<List<String>>,
): List<List<String>> {
    val all = mutableListOf<List<String>>()
    var start = firstRow
    while (true) {
        val end = start + pageSize - 1
        val page = readPage(start, end)
        all += page
        if (page.size < pageSize) break
        start += pageSize
    }
    return all
}
