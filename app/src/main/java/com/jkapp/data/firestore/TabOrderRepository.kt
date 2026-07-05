package com.jkapp.data.firestore

import kotlinx.coroutines.flow.Flow

interface TabOrderRepository {
    // uid에 저장된 문서가 없으면 null을 방출한다(아직 순서를 지정하지 않은 사용자와 구분하기 위함).
    fun observeTabOrder(uid: String): Flow<List<String>?>
    suspend fun saveTabOrder(uid: String, tabNames: List<String>)
}
