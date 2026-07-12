package com.jkapp.finance.investment

import android.util.Log
import com.jkapp.common.AppFirestore
import com.jkapp.common.await
import com.jkapp.common.snapshotFlow
import kotlinx.coroutines.flow.Flow

class PortfolioFirestoreRepositoryImpl : PortfolioFirestoreRepository {

    private val db = AppFirestore.instance
    private val portfoliosRef = db.collection(COLLECTION_PORTFOLIOS)

    override fun getPortfolios(): Flow<List<Portfolio>> = portfoliosRef.snapshotFlow { snapshot ->
        snapshot?.documents?.mapNotNull { doc ->
            // 요소 단위로 방어적 캐스팅한다(문서 내 잘못된 원소가 섞여도 전체가 깨지지 않도록).
            val groups = (doc.get(FIELD_GROUPS) as? List<*>)
                ?.mapNotNull { element ->
                    @Suppress("UNCHECKED_CAST")
                    (element as? Map<String, Any?>)?.toPortfolioGroup()
                } ?: emptyList()
            val name = doc.getString(FIELD_NAME) ?: return@mapNotNull null
            Portfolio(
                firestoreId = doc.id,
                name = name,
                groups = groups,
            )
        } ?: emptyList()
    }

    // 새 문서이면 Firestore 자동 생성 ID를 반환하고, 기존 문서이면 기존 ID를 반환한다.
    override suspend fun upsertPortfolio(portfolio: Portfolio): String {
        val ref = if (portfolio.firestoreId != null) {
            portfoliosRef.document(portfolio.firestoreId)
        } else {
            portfoliosRef.document()
        }
        ref.set(portfolio.toMap()).await()
        return ref.id
    }

    override suspend fun deletePortfolio(firestoreId: String) {
        portfoliosRef.document(firestoreId).delete().await()
    }

    private fun Portfolio.toMap() = mapOf(
        FIELD_NAME to name,
        FIELD_GROUPS to groups.map { it.toMap() },
    )

    private fun PortfolioGroup.toMap(): Map<String, Any?> = mapOf(
        FIELD_GROUP_NAME to name,
        FIELD_GROUP_OWNERS to owners,
        FIELD_GROUP_ACCOUNTS to accounts,
        FIELD_GROUP_CATEGORIES to categories,
        FIELD_GROUP_STOCK_NAMES to stockNames,
        FIELD_GROUP_TARGET_RATIO to targetRatio.toLong(),
    )

    private fun Map<String, Any?>.toPortfolioGroup(): PortfolioGroup? {
        val name = this[FIELD_GROUP_NAME] as? String ?: run {
            Log.w(TAG, "portfolios 문서에 그룹 이름이 없어 건너뜁니다: $this")
            return null
        }
        @Suppress("UNCHECKED_CAST")
        val owners = (this[FIELD_GROUP_OWNERS] as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val accounts = (this[FIELD_GROUP_ACCOUNTS] as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val categories = this[FIELD_GROUP_CATEGORIES] as? List<String>
        @Suppress("UNCHECKED_CAST")
        val stockNames = this[FIELD_GROUP_STOCK_NAMES] as? List<String>
        val targetRatio = (this[FIELD_GROUP_TARGET_RATIO] as? Long)?.toInt() ?: 0
        return PortfolioGroup(
            name = name,
            owners = owners,
            accounts = accounts,
            categories = categories,
            stockNames = stockNames,
            targetRatio = targetRatio,
        )
    }

    companion object {
        private const val TAG = "PortfolioFirestoreRepositoryImpl"

        private const val COLLECTION_PORTFOLIOS = "portfolios"

        private const val FIELD_NAME = "name"
        private const val FIELD_GROUPS = "groups"
        private const val FIELD_GROUP_NAME = "name"
        private const val FIELD_GROUP_OWNERS = "owners"
        private const val FIELD_GROUP_ACCOUNTS = "accounts"
        private const val FIELD_GROUP_CATEGORIES = "categories"
        private const val FIELD_GROUP_STOCK_NAMES = "stockNames"
        private const val FIELD_GROUP_TARGET_RATIO = "targetRatio"
    }
}
