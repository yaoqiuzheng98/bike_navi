package com.example.bikenavi

import android.app.Application
import com.baidu.mapapi.SDKInitializer

class BikeNaviApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 隐私合规接口，必须在 initialize 之前调用
        SDKInitializer.setAgreePrivacy(this, true)
        // 初始化地图 SDK
        SDKInitializer.initialize(this)
    }
}
