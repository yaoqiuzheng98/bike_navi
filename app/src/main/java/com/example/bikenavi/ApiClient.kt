package com.example.bikenavi

import android.util.Log
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * 后端 API 客户端，用 HttpURLConnection 请求后端接口
 */
object ApiClient {
    // 后端地址（电脑局域网 IP + 端口）
    private const val BASE_URL = "http://192.168.3.175:9999"

    /**
     * 获取所有地点标记，同步调用，需在子线程执行
     */
    fun fetchPoints(): List<BikePoint> {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/point")
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.requestMethod = "GET"
            // 带上 X-User-Id 请求头
            UserIdManager.getUserId()?.let {
                conn.setRequestProperty("X-User-Id", it)
            }

            val code = conn.responseCode
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
