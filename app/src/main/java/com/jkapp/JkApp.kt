package com.jkapp

import android.app.Application
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.jkapp.common.AppFirestore

class JkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppFirestore.instance.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
    }
}
