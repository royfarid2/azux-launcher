package com.azux.launcher

import android.app.Application

class AzuxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Azux.init(this)
    }
}
