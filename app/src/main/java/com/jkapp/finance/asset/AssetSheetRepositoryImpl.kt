package com.jkapp.finance.asset

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.SheetsScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetSheetRepositoryImpl(context: Context) : AssetSheetRepository {

    private val credential = GoogleAccountCredential.usingOAuth2(
        context.applicationContext,
        listOf(SheetsScopes.SPREADSHEETS_READONLY),
    )

    override fun setAccount(accountName: String) {
        credential.selectedAccountName = accountName
    }

    override suspend fun readAssetRows(): List<List<String>> = withContext(Dispatchers.IO) {
        if (credential.selectedAccountName == null) {
            throw AssetSheetAuthException(credential.newChooseAccountIntent())
        }
        try {
            val sheets = buildSheetsService()
            val header = readRange(sheets, cellRange(HEADER_ROW, HEADER_ROW)).firstOrNull().orEmpty()
            // 헤더가 없으면 시트가 비어 있거나 잘못된 것으로 보고 빈 목록을 반환한다.
            if (header.isEmpty()) return@withContext emptyList()

            val dataRows = collectPagedRows { startRow, endRow ->
                readRange(sheets, cellRange(startRow, endRow))
            }
            listOf(header) + dataRows
        } catch (e: UserRecoverableAuthIOException) {
            throw AssetSheetAuthException(e.intent)
        }
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

    // 시트 이름에 공백/하이픈이 있어 A1 표기에서 작은따옴표로 감싼다. 예: 'JK-APP raw'!A2:F101
    private fun cellRange(startRow: Int, endRow: Int): String =
        "'$SHEET_NAME'!$FIRST_COLUMN$startRow:$LAST_COLUMN$endRow"

    companion object {
        private const val SPREADSHEET_ID = "1jiaeZjgLAW5dJqsb-zet5ljAO-At2Sdd7-E-Vu1EAQk"
        private const val SHEET_NAME = "JK-APP raw"
        private const val FIRST_COLUMN = "A"
        private const val LAST_COLUMN = "F"
        private const val HEADER_ROW = 1
    }
}
