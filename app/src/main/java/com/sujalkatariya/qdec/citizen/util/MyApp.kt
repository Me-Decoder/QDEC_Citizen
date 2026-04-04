package com.sujalkatariya.qdec.citizen

import android.app.Application
import com.sujalkatariya.qdec.citizen.util.CloudinaryManager

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        CloudinaryManager.init(this)
    }
}