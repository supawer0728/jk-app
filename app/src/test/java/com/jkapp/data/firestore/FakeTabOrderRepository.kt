package com.jkapp.data.firestore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTabOrderRepository : TabOrderRepository {

    private val ordersByUid = mutableMapOf<String, MutableStateFlow<List<String>?>>()

    var saveTabOrderError: Throwable? = null
    var lastSavedUid: String? = null
    var lastSavedOrder: List<String>? = null

    fun setTabOrder(uid: String, order: List<String>?) {
        flowFor(uid).value = order
    }

    override fun observeTabOrder(uid: String): Flow<List<String>?> = flowFor(uid)

    override suspend fun saveTabOrder(uid: String, tabNames: List<String>) {
        saveTabOrderError?.let { throw it }
        lastSavedUid = uid
        lastSavedOrder = tabNames
        flowFor(uid).value = tabNames
    }

    private fun flowFor(uid: String) = ordersByUid.getOrPut(uid) { MutableStateFlow(null) }
}
