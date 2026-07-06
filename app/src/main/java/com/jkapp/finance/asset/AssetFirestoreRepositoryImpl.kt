package com.jkapp.finance.asset

import android.util.Log
import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import com.jkapp.common.snapshotFlow
import kotlinx.coroutines.flow.Flow

class AssetFirestoreRepositoryImpl : AssetFirestoreRepository {

    private val db = AppFirestore.instance
    private val dailyAssetsRef = db.collection(COLLECTION_DAILY_ASSETS)

    override fun getDailyAssets(): Flow<List<DailyAsset>> = dailyAssetsRef.snapshotFlow { snapshot ->
        snapshot?.documents?.map { doc ->
            @Suppress("UNCHECKED_CAST")
            val assets = (doc.get(FIELD_ASSETS) as? List<Map<String, Any?>>)
                ?.mapNotNull { it.toAssetItem() } ?: emptyList()
            DailyAsset(
                firestoreId = doc.id,
                date = doc.getString(FIELD_DATE) ?: doc.id,
                assets = assets,
            )
        }?.sortedByDescending { it.date } ?: emptyList()
    }

    override suspend fun upsertDailyAsset(asset: DailyAsset) {
        dailyAssetsRef.document(asset.date).set(asset.toMap()).await()
    }

    override suspend fun deleteDailyAsset(date: String) {
        dailyAssetsRef.document(date).delete().await()
    }

    private fun DailyAsset.toMap() = mapOf(
        FIELD_DATE to date,
        FIELD_ASSETS to assets.map { it.toMap() },
    )

    private fun AssetItem.toMap(): Map<String, Any?> = mapOf(
        FIELD_ASSET_NAME to name,
        FIELD_ASSET_OWNER to owner,
        FIELD_ASSET_INSTITUTION to institution,
        FIELD_ASSET_ACCOUNT_NUMBER to accountNumber,
        FIELD_ASSET_CARD to card,
        FIELD_ASSET_AMOUNT to amount?.toPlainString(),
        FIELD_ASSET_HIDDEN to hidden,
    )

    // name/owner가 없는 항목은 걸러지는데, 이 문서를 이후 add/update/delete로 다시 저장하면
    // 걸러진 항목이 전체 upsert(set)로 인해 영구히 사라지므로 최소한 로그로 남긴다.
    private fun Map<String, Any?>.toAssetItem(): AssetItem? {
        val name = this[FIELD_ASSET_NAME] as? String
        val owner = this[FIELD_ASSET_OWNER] as? String
        if (name == null || owner == null) {
            Log.w(TAG, "daily-assets 문서에 name/owner가 없는 자산 항목이 있어 건너뜁니다: $this")
            return null
        }
        return AssetItem(
            name = name,
            owner = owner,
            institution = this[FIELD_ASSET_INSTITUTION] as? String,
            accountNumber = this[FIELD_ASSET_ACCOUNT_NUMBER] as? String,
            card = this[FIELD_ASSET_CARD] as? String,
            amount = (this[FIELD_ASSET_AMOUNT] as? String)?.toBigDecimalOrNull(),
            hidden = this[FIELD_ASSET_HIDDEN] as? Boolean ?: false,
        )
    }

    companion object {
        private const val TAG = "AssetFirestoreRepositoryImpl"

        private const val COLLECTION_DAILY_ASSETS = "daily-assets"

        private const val FIELD_DATE = "date"
        private const val FIELD_ASSETS = "assets"
        private const val FIELD_ASSET_NAME = "name"
        private const val FIELD_ASSET_OWNER = "owner"
        private const val FIELD_ASSET_INSTITUTION = "institution"
        private const val FIELD_ASSET_ACCOUNT_NUMBER = "accountNumber"
        private const val FIELD_ASSET_CARD = "card"
        private const val FIELD_ASSET_AMOUNT = "amount"
        private const val FIELD_ASSET_HIDDEN = "hidden"
    }
}
