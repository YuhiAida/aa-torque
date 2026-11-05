package com.aatorque.stats

import android.app.Application
import android.content.Context
import timber.log.Timber

class App : Application() {

    val logTree = CacheLogTree()

    override fun onCreate() {
        super.onCreate()
        Timber.plant(logTree)
        CrashDump(this, logTree).install()
        Thread { fixAndroid14Perms() }.start()
    }

    fun fixAndroid14Perms() {
        for (file in getDir("car_sdk_impl", Context.MODE_PRIVATE).listFiles() ?: emptyArray()) {
            if (file.isDirectory) {
                for (subfile in file.listFiles() ?: emptyArray()) {
                    Timber.i("Setting read only permission for $subfile")
                    subfile.setReadOnly()
                }
            }
            Timber.i("Setting read only permission for $file")
            file.setReadOnly()
        }
    }
}
