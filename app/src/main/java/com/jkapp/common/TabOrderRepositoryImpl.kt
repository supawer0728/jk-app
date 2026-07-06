package com.jkapp.common

import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

class TabOrderRepositoryImpl(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : TabOrderRepository {

    private val tabOrdersRef = db.collection(COLLECTION_TAB_ORDERS)

    override fun observeTabOrder(uid: String): Flow<List<String>?> = callbackFlow {
        val listener = tabOrdersRef.document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val tabs = (snapshot?.get(FIELD_TABS) as? List<*>)?.filterIsInstance<String>()
            trySend(tabs)
        }
        awaitClose { listener.remove() }
    }

    override suspend fun saveTabOrder(uid: String, tabNames: List<String>): Unit = suspendCancellableCoroutine { cont ->
        tabOrdersRef.document(uid).set(mapOf(FIELD_TABS to tabNames))
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    companion object {
        private const val COLLECTION_TAB_ORDERS = "tab-orders"
        private const val FIELD_TABS = "tabs"
    }
}
