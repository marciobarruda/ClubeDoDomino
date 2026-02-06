package com.marcioarruda.clubedodomino.data.local

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("DominoClubPrefs", Context.MODE_PRIVATE)

    companion object {
        const val USER_ID_KEY = "user_id"
    }

    fun saveUser(userId: String) {
        val editor = prefs.edit()
        editor.putString(USER_ID_KEY, userId)
        editor.apply()
    }

    fun getUserId(): String? {
        return prefs.getString(USER_ID_KEY, null)
    }

    fun clearSession() {
        val editor = prefs.edit()
        editor.clear()
        editor.apply()
    }
}
