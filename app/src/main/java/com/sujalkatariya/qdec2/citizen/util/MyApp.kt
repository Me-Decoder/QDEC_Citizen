package com.sujalkatariya.qdec.citizen

import android.app.Application
import com.sujalkatariya.qdec2.citizen.util.CloudinaryManager

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 🔥 MUST INIT HERE

        CloudinaryManager.init(this)
    }
}