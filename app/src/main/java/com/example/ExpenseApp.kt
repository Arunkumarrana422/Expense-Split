package com.example

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class ExpenseApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:52643587669:android:55f7580c6b52c35c1a96dc")
                    .setApiKey("AIzaSyA9JFTO-kzwvccCvydATL-gqGgaqxo33wA")
                    .setProjectId("student-patnar")
                    .setDatabaseUrl("https://student-patnar-default-rtdb.firebaseio.com")
                    .setStorageBucket("student-patnar.firebasestorage.app")
                    .setGcmSenderId("52643587669")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.d("ExpenseApp", "FirebaseApp initialized with explicit options")
            } else {
                Log.d("ExpenseApp", "FirebaseApp already initialized")
            }
        } catch (e: Exception) {
            Log.e("ExpenseApp", "Failed to initialize FirebaseApp", e)
        }
    }
}
