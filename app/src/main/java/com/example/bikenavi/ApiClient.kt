package com.example.bikenavi

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 后端 API 客户端，用 HttpURLConnection 请求后端接口
 */
object ApiClient {
    // 后端地址（电脑局域网 IP + 端口）
    private const val BASE_URL = "http://192.168.3.175:9999"

    // APP 版本号（从 BuildConfig 读取）
    private val appVersion: String
        get() = BuildConfig.VERSION_NAME

    /**
     * 需要更新时的信息
     */
    data class UpdateInfo(val message: String)

    /**
     * 获取所有地点标记，同步调用，需在子线程执行
     * @return 点位列表；如果版本过低返回 null（调用方应检查 updateInfo）
     */
    fun fetchPoints(): List<BikePoint>? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/point")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            // 带上 X-User-Id 和 X-App-Version 请求头
            UserIdManager.getUserId()?.let {
                conn.setRequestProperty("X-User-Id", it)
            }
            conn.setRequestProperty("X-App-Version", appVersion)

            val code = conn.responseCode
            if (code == 409) {
                // 版本过低，读取更新内容
                val body = conn.errorStream?.bufferedReader()?.use { it.readText() }
                if (body != null) {
                    val json = JSONObject(body)
                    updateInfo = UpdateInfo(json.optString("message", "请更新到最新版本"))
                }
                return null
            }
            if (code != 200) {
                Log.e("ApiClient", "fetchPoints HTTP $code")
                return emptyList()
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parsePoints(JSONArray(body))
        } catch (e: Exception) {
            Log.e("ApiClient", "fetchPoints failed", e)
            return emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    // 最近一次的更新提示信息
    @Volatile
    var updateInfo: UpdateInfo? = null

    private fun parsePoints(arr: JSONArray): List<BikePoint> {
        val list = mutableListOf<BikePoint>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                BikePoint(
                    id = obj.optLong("id"),
                    latitude = obj.getDouble("latitude"),
                    longitude = obj.getDouble("longitude"),
                    name = obj.optString("name"),
                )
            )
        }
        return list
    }
}
