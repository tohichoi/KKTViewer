package com.soonsim.kktlogviewer

import android.content.Context
import android.preference.PreferenceManager

class KKTConfig(context: Context) {
    protected val prefs = context.getSharedPreferences("preference", Context.MODE_PRIVATE)

    companion object {
        fun newInstance(context: Context) = KKTConfig(context)
    }

    var lastSearchText: String
        get() = prefs.getString("queryLastText", "")!!
        set(queryLastText) = prefs.edit().putString(queryLastText, queryLastText).apply()

    var lastViewedDate: Long
        get() = prefs.getLong("lastViewedDate", 0)
        set(lastViewedDate) = prefs.edit().putLong("lastViewedDate", lastViewedDate).apply()

    var authorId: String
        get() = prefs.getString("authorId", "")!!
        set(authorId) = prefs.edit().putString("authorId", authorId).apply()

    var lastOpenedFile: String
        get() = prefs.getString("lastOpenedFile", "")!!
        set(lastOpenedFile) = prefs.edit().putString("lastOpenedFile", authorId).apply()

    var useDatabase: Boolean
        get() = prefs.getBoolean("useDatabase", false)
        set(dbCreated) = prefs.edit().putBoolean("useDatabase", dbCreated).apply()
}