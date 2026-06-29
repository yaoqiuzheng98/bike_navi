package com.example.bikenavi

import android.content.Context
import android.content.SharedPreferences

/**
 * 用户 ID 管理，用 SharedPreferences 持久化存储
 */
object UserIdManager {
    private const val PREFS_NAME = "bike_navi_prefs"
    private const val KEY_USER_ID = "user_id"

    private var prefs: SharedPreferences? = null
    private var cachedUserId: String? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cachedUserId = prefs?.getString(KEY_USER_ID, null)
    }

    /**
     * 获取已保存的 UserId，没有则返回 null
     */
    fun getUserId(): String? = cachedUserId

    /**
     * 保存 UserId
     */
    fun saveUserId(userId: String) {
        cachedUserId = userId
        prefs?.edit()?.putString(KEY_USER_ID, userId)?.apply()
    }

    /**
     * 是否已设置 UserId
     */
    fun hasUserId(): Boolean = !cachedUserId.isNullOrBlank()
}
