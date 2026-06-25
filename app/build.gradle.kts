plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.bikenavi"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.bikenavi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 百度导航 SDK 方法数多，需 multidex
        multiDexEnabled = true
        ndk {
            // 百度 SDK 只提供 arm 架构 .so
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    packaging {
        jniLibs {
            pickFirsts.add("**/libBaiduMapSDK_*.so")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 百度地图：步骑行导航组件（含 Map 核心 + bwnavi 引擎，保留其 a.a.a.a.a.a 类）
    implementation("com.baidu.lbsyun:BaiduMapSDK_Map-BWNavi:8.1.0")
    // 检索组件（Sug 建议、路线规划）—— 本地去重版：移除了与 bwnavi 重复的 a.a.a.a.a.a
    implementation(files("libs/BaiduMapSDK_Search-8.1.0.aar"))
    // 工具组件
    implementation("com.baidu.lbsyun:BaiduMapSDK_Util:8.1.0")
    // 导航 TTS 语音播报
    implementation("com.baidu.lbsyun:NaviTts:3.2.13")
    // AppCompat —— 导航 Activity 需要 AppCompat 主题
    implementation("androidx.appcompat:appcompat:1.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}