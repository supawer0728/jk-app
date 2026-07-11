package com.jkapp.finance.investment

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InvestmentSheetRepositoryImpl(context: Context) : InvestmentSheetRepository {

    private val credential = GoogleAccountCredential.usingOAuth2(
        context.applicationContext,
        listOf(SheetsScopes.SPREADSHEETS_READONLY),
    )

    override fun setAccount(accountName: String) {
        credential.selectedAccountName = accountName
    }

    override suspend fun readInvestmentBlocks(): List<InvestmentSheetBlock> = withContext(Dispatchers.IO) {
        if (credential.selectedAccountName == null) {
            throw InvestmentSheetAuthException(credential.newChooseAccountIntent())
        }
        try {
            val sheets = buildSheetsService()
            OWNER_BLOCKS.map { block -> readBlock(sheets, block) }
        } catch (e: UserRecoverableAuthIOException) {
            throw InvestmentSheetAuthException(e.intent)
        }
    }

    private suspend fun readBlock(sheets: Sheets, block: OwnerBlock): InvestmentSheetBlock {
        val header = readRange(sheets, block.cellRange(HEADER_ROW, HEADER_ROW)).firstOrNull().orEmpty()
        // 헤더가 없으면 해당 명의 블록이 비어 있거나 잘못된 것으로 보고 빈 블록을 반환한다.
        if (header.isEmpty()) return InvestmentSheetBlock(owner = block.owner, rows = emptyList())

        val dataRows = collectPagedRows { startRow, endRow ->
            readRange(sheets, block.cellRange(startRow, endRow))
        }
        return InvestmentSheetBlock(owner = block.owner, rows = listOf(header) + dataRows)
    }

    private fun buildSheetsService(): Sheets =
        Sheets.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential,
        ).setApplicationName("jkapp").build()

    private fun readRange(sheets: Sheets, range: String): List<List<String>> {
        val values = sheets.spreadsheets().values()
            .get(SPREADSHEET_ID, range)
            .execute()
            .getValues()
            ?: return emptyList()
        return values.map { row -> row.map { cell -> cell?.toString() ?: "" } }
    }

    // 명의별 열 블록: 전지훈(J) = H~N, 권유경(K) = P~V. 각각 헤더 1행 + 2행부터의 데이터로 구성된다.
    private data class OwnerBlock(val owner: String, val firstColumn: String, val lastColumn: String) {
        // 시트 이름에 공백/하이픈이 있어 A1 표기에서 작은따옴표로 감싼다. 예: 'JK-APP raw'!H2:N101
        fun cellRange(startRow: Int, endRow: Int): String =
            "'$SHEET_NAME'!$firstColumn$startRow:$lastColumn$endRow"
    }

    companion object {
        private const val SPREADSHEET_ID = "1jiaeZjgLAW5dJqsb-zet5ljAO-At2Sdd7-E-Vu1EAQk"
        private const val SHEET_NAME = "JK-APP raw"
        private const val HEADER_ROW = 1

        private val OWNER_BLOCKS = listOf(
            OwnerBlock(owner = "전지훈", firstColumn = "H", lastColumn = "N"),
            OwnerBlock(owner = "권유경", firstColumn = "P", lastColumn = "V"),
        )
    }
}
