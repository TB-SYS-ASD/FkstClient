package com.tb.fkst

import android.app.Application
import com.tb.fkst.data.Repository

class FkstApp : Application() {

    lateinit var repo: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repo = Repository(this)
    }
}
