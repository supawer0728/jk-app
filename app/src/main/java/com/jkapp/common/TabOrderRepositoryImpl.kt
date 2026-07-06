package com.jkapp.common

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow

class TabOrderRepositoryImpl(
    private val db: FirebaseFirestore = AppFirestore.instance,
) : TabOrderRepository {

    private val tabOrdersRef = db.collection(COLLECTION_TAB_ORDERS)

    override fun observeTabOrder(uid: String): Flow<List<String>?> =
        tabOrdersRef.document(uid).snapshotFlow { snapshot ->
            (snapshot?.get(FIELD_TABS) as? List<*>)?.filterIsInstance<String>()
        }

    override suspend fun saveTabOrder(uid: String, tabNames: List<String>) {
        tabOrdersRef.document(uid).set(mapOf(FIELD_TABS to tabNames)).await()
    }

    companion object {
        private const val COLLECTION_TAB_ORDERS = "tab-orders"
        private const val FIELD_TABS = "tabs"
    }
}
