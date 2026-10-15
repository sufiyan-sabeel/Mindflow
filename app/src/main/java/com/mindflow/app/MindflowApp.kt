package com.mindflow.app

import android.app.Application
import com.mindflow.app.data.AppRepository

class MindflowApp : Application() {
    val repository: AppRepository by lazy { AppRepository(applicationContext) }
}
