package com.jkapp.common

import com.google.firebase.firestore.FirebaseFirestore

// Repository들이 각자 FirebaseFirestore.getInstance()를 호출하는 대신 이 공유 인스턴스를
// 주입받게 하여, JkApp에서 구성한 설정(캐시 등)을 바꿀 때 고칠 지점을 한 곳으로 모은다.
object AppFirestore {
    val instance: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
}
