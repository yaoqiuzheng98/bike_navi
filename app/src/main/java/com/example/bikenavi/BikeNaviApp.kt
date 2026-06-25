package com.example.bikenavi

import android.app.Application
import android.util.Log
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings

class BikeNaviApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("BikeNaviApp", "onCreate start")
        try {
            // 地图 SDK 隐私合规（必须在构造 MapView 之前调用）
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
            // 搜索 SDK 隐私合规（必须在调用搜索接口之前调用）
            ServiceSettings.updatePrivacyShow(this, true, true)
            ServiceSettings.updatePrivacyAgree(this, true)
            Log.d("BikeNaviApp", "privacy init OK")
        } catch (e: Exception) {
            Log.e("BikeNaviApp", "init failed", e)
        }
    }
}
