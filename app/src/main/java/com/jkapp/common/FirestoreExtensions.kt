package com.jkapp.common

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine

// Firestore Task를 suspend 함수로 감싸는 공통 패턴. 여러 Repository가 각자 구현하던
// suspendCancellableCoroutine + addOnSuccessListener/addOnFailureListener 골격을 통합한다.
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
}

// Firestore 컬렉션/쿼리의 addSnapshotListener + awaitClose 골격을 Flow로 감싸는 공통 패턴.
fun <T> Query.snapshotFlow(transform: (QuerySnapshot?) -> T): Flow<T> = callbackFlow {
    val listener = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        trySend(transform(snapshot))
    }
    awaitClose { listener.remove() }
}

// 단일 문서의 addSnapshotListener + awaitClose 골격을 Flow로 감싸는 공통 패턴.
fun <T> DocumentReference.snapshotFlow(transform: (DocumentSnapshot?) -> T): Flow<T> = callbackFlow {
    val listener = addSnapshotListener { snapshot, error ->
        if (error != null) {
            close(error)
            return@addSnapshotListener
        }
        trySend(transform(snapshot))
    }
    awaitClose { listener.remove() }
}
