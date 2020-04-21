package com.soonsim.kktlogviewer

import android.content.Context

class KKTConfig(context: Context) {
    protected val prefs = context.getSharedPreferences("preference", Context.MODE_PRIVATE)

    companion object {
        fun newInstance(context: Context) = KKTConfig(context)
    }

    var lastQueryText: String
        get() = prefs.getString("lastQueryText", "")!!
        set(lastQueryText) = prefs.edit().putString("lastQueryText", lastQueryText).apply()

    var lastViewedDate: Long
        get() = prefs.getLong("lastViewedDate", 0)
        set(lastViewedDate) = prefs.edit().putLong("lastViewedDate", lastViewedDate).apply()

    var lastViewedPosition: Long
        get() = prefs.getLong("lastViewedPosition", 0)
        set(lastViewedPosition) = prefs.edit().putLong("lastViewedPosition", lastViewedPosition)
            .apply()

    var selectedItemPosition: MutableSet<String>?
        get() = prefs.getStringSet("selectedItemPosition", null)
        set(selectedItemPosition) = prefs.edit().putStringSet("selectedItemPosition", selectedItemPosition)
            .apply()

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