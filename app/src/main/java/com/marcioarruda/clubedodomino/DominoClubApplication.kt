package com.marcioarruda.clubedodomino

import android.app.Application
import com.marcioarruda.clubedodomino.data.UserSessionManager

class DominoClubApplication : Application() {

    lateinit var sessionManager: UserSessionManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionManager = UserSessionManager(this)
    }

    companion object {
        lateinit var instance: DominoClubApplication
            private set
    }
}
