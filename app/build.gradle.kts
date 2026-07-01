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

    signingConfigs {
        create("release") {
            storeFile = file("../bike-navi.jks")
            storePassword = "bike123456"
            keyAlias = "bike-navi"
            keyPassword = "bike123456"
        }
    }

    defaultConfig {
        applicationId = "com.example.bikenavi"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        multiDexEnabled = true
        ndk {
            // 高德 SDK V11.2.000 仅支持 arm64-v8a
            abiFilters += listOf("arm64-v8a")
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 自定义 APK 文件名
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            if (output is com.android.build.api.variant.impl.VariantOutputImpl) {
                output.outputFileName.set("骑行日记.apk")
            }
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // 高德导航合包 AAR（V11.2.000，导航 + 3D 地图 + 定位 + 搜索，含 UI 资源和 so 库）
    implementation(files("libs/amap_aar/AMap3DMap_11.2.000_AMapNavi_11.2.000_AMapSearch_9.8.0_AMapLocation_11.2.000_20260603.aar"))
    // AppCompat —— 导航 Activity 需要 AppCompat 主题
    implementation("androidx.appcompat:appcompat:1.7.0")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
