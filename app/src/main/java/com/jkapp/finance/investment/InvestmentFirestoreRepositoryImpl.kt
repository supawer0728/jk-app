package com.jkapp.finance.investment

import android.util.Log
import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import com.jkapp.common.snapshotFlow
import kotlinx.coroutines.flow.Flow

class InvestmentFirestoreRepositoryImpl : InvestmentFirestoreRepository {

    private val db = AppFirestore.instance
    private val dailyAssetInvestmentsRef = db.collection(COLLECTION_DAILY_ASSET_INVESTMENTS)

    override fun getDailyAssetInvestments(): Flow<List<DailyAssetInvestment>> = dailyAssetInvestmentsRef.snapshotFlow { snapshot ->
        snapshot?.documents?.map { doc ->
            @Suppress("UNCHECKED_CAST")
            val items = (doc.get(FIELD_INVESTMENTS) as? List<Map<String, Any?>>)
                ?.mapNotNull { it.toInvestmentItem() } ?: emptyList()
            DailyAssetInvestment(
                firestoreId = doc.id,
                date = doc.getString(FIELD_DATE) ?: doc.id.substringBefore("_"),
                owner = doc.getString(FIELD_OWNER) ?: doc.id.substringAfter("_"),
                investments = items,
            )
        }?.sortedByDescending { it.date } ?: emptyList()
    }

    override suspend fun upsertDailyAssetInvestment(investment: DailyAssetInvestment) {
        dailyAssetInvestmentsRef.document(investmentDocId(investment.date, investment.owner)).set(investment.toMap()).await()
    }

    override suspend fun deleteDailyAssetInvestment(date: String, owner: String) {
        dailyAssetInvestmentsRef.document(investmentDocId(date, owner)).delete().await()
    }

    private fun investmentDocId(date: String, owner: String) = "${date}_$owner"

    private fun DailyAssetInvestment.toMap() = mapOf(
        FIELD_DATE to date,
        FIELD_OWNER to owner,
        FIELD_INVESTMENTS to investments.map { it.toMap() },
    )

    private fun InvestmentItem.toMap(): Map<String, Any?> = mapOf(
        FIELD_INVESTMENT_ASSET_NAME to assetName,
        FIELD_INVESTMENT_CATEGORY to category,
        FIELD_INVESTMENT_NAME to investmentName,
        FIELD_INVESTMENT_PRICE_PER_SHARE to pricePerShare.toPlainString(),
        FIELD_INVESTMENT_VALUATION_AMOUNT to valuationAmount.toPlainString(),
        FIELD_INVESTMENT_QUANTITY to quantity.toPlainString(),
        FIELD_INVESTMENT_PURCHASE_AMOUNT to purchaseAmount.amount.toPlainString(),
        FIELD_INVESTMENT_PURCHASE_AMOUNT_CURRENCY to purchaseAmount.currency,
    )

    // 필드가 하나라도 없거나 파싱에 실패하면 잘못된 값을 보여주는 대신 건너뛰고 로그를 남긴다.
    // 이 문서를 이후 add/update/delete로 다시 저장하면 걸러진 항목이 전체 upsert(set)로 인해
    // 영구히 사라지므로 최소한 로그로 남긴다.
    private fun Map<String, Any?>.toInvestmentItem(): InvestmentItem? {
        val assetName = this[FIELD_INVESTMENT_ASSET_NAME] as? String
        val category = this[FIELD_INVESTMENT_CATEGORY] as? String
        val investmentName = this[FIELD_INVESTMENT_NAME] as? String
        val pricePerShare = (this[FIELD_INVESTMENT_PRICE_PER_SHARE] as? String)?.toBigDecimalOrNull()
        val valuationAmount = (this[FIELD_INVESTMENT_VALUATION_AMOUNT] as? String)?.toBigDecimalOrNull()
        val quantity = (this[FIELD_INVESTMENT_QUANTITY] as? String)?.toBigDecimalOrNull()
        val purchaseAmountValue = (this[FIELD_INVESTMENT_PURCHASE_AMOUNT] as? String)?.toBigDecimalOrNull()
        // purchaseAmountCurrency는 통화 구분을 추가하기 전에 저장된 기존 문서에는 없으므로 KRW로 간주한다.
        val purchaseAmountCurrency = this[FIELD_INVESTMENT_PURCHASE_AMOUNT_CURRENCY] as? String ?: "KRW"
        if (assetName == null || category == null || investmentName == null || pricePerShare == null ||
            valuationAmount == null || quantity == null || purchaseAmountValue == null
        ) {
            Log.w(TAG, "daily-asset-investments 문서에 필드가 누락된 투자 종목 항목이 있어 건너뜁니다: $this")
            return null
        }
        return InvestmentItem(
            assetName = assetName,
            category = category,
            investmentName = investmentName,
            pricePerShare = pricePerShare,
            valuationAmount = valuationAmount,
            quantity = quantity,
            purchaseAmount = CurrencyAmount(currency = purchaseAmountCurrency, amount = purchaseAmountValue),
        )
    }

    companion object {
        private const val TAG = "InvestmentFirestoreRepositoryImpl"

        private const val COLLECTION_DAILY_ASSET_INVESTMENTS = "daily-asset-investments"

        private const val FIELD_DATE = "date"
        private const val FIELD_OWNER = "owner"
        private const val FIELD_INVESTMENTS = "investments"
        private const val FIELD_INVESTMENT_ASSET_NAME = "assetName"
        private const val FIELD_INVESTMENT_CATEGORY = "category"
        private const val FIELD_INVESTMENT_NAME = "investmentName"
        private const val FIELD_INVESTMENT_PRICE_PER_SHARE = "pricePerShare"
        private const val FIELD_INVESTMENT_VALUATION_AMOUNT = "valuationAmount"
        private const val FIELD_INVESTMENT_QUANTITY = "quantity"
        private const val FIELD_INVESTMENT_PURCHASE_AMOUNT = "purchaseAmount"
        private const val FIELD_INVESTMENT_PURCHASE_AMOUNT_CURRENCY = "purchaseAmountCurrency"
    }
}
