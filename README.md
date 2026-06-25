# BikeNavi - 骑行导航 App

基于高德地图 SDK 的 Android 骑行导航应用，使用 Jetpack Compose 构建 UI。

## 功能

- 自动定位当前位置作为起点
- 搜索终点（高德 Inputtips）
- 骑行路线规划与显示
- Turn-by-turn 语音导航（高德 AMapNaviView）

## 技术栈

- Kotlin + Jetpack Compose
- 高德地图导航合包 AAR（V11.2.000：3D地图 + 导航 + 定位 + 搜索）
- 仅支持 arm64-v8a

## 配置

1. 在 [高德开放平台](https://console.amap.com/dev/key/app) 申请 Android AK
2. 在高德控制台绑定包名 `com.example.bikenavi` 和 debug/release SHA1
3. 把 AK 填入 `app/src/main/AndroidManifest.xml` 的 `com.amap.api.v2.apikey`
4. 把高德导航合包 AAR 放到 `app/libs/amap_aar/` 目录
5. 在 `app/build.gradle.kts` 里引用 AAR 文件名

## 踩坑记录

从百度地图 SDK 迁移到高德地图 SDK 过程中遇到的一系列问题及解决方案。

### 1. 地图白屏不显示

**原因**：高德 SDK 8.1.0+ 要求隐私合规，且 `MapView` 必须调用 `onCreate()`。

**解决**：
- 在 `Application.onCreate()` 里调用 `MapsInitializer.updatePrivacyShow/Agree`（地图 SDK）和 `ServiceSettings.updatePrivacyShow/Agree`（搜索 SDK）
- 在 Compose 的 `DisposableEffect` 里调用 `mapView.onCreate(null)`

```kotlin
// Application
MapsInitializer.updatePrivacyShow(this, true, true)
MapsInitializer.updatePrivacyAgree(this, true)
ServiceSettings.updatePrivacyShow(this, true, true)
ServiceSettings.updatePrivacyAgree(this, true)

// MapScreen
DisposableEffect(Unit) {
    mapView.onCreate(null)
    onDispose { }
}
```

### 2. 搜索无结果（Inputtips 崩溃）

**原因**：`Inputtips(context, query)` 的 context 传了 null。

**解决**：用 Compose 的 `LocalContext.current` 获取真实 Context。

```kotlin
val context = LocalContext.current
val tips = Inputtips(context, query)
```

### 3. 导航页 Resources$NotFoundException + 静态地图

**原因**：Maven 上的合包 jar 不含 res 资源文件，`AMapNaviView` 内置 UI（dimen/layout/drawable）找不到。

**解决**：从高德官网下载 AAR 包（含 res 资源和 so 库），放到 `app/libs/` 目录用 `implementation(files(...))` 引用。

```
app/libs/amap_aar/AMap3DMap_11.2.000_AMapNavi_11.2.000_AMapSearch_9.8.0_AMapLocation_11.2.000_20260603.aar
```

```kotlin
implementation(files("libs/amap_aar/AMap3DMap_11.2.000_..._.aar"))
```

### 4. 导航页 SecurityException: WAKE_LOCK

**原因**：高德导航 SDK 初始化时需要 `WAKE_LOCK` 权限保持屏幕常亮。

**解决**：在 `AndroidManifest.xml` 添加权限。

```xml
<uses-permission android:name="android.permission.WAKE_LOCK" />
```

### 5. 导航页 SecurityException: ACCESS_FINE_LOCATION

**原因**：导航 SDK 需要 GPS 定位权限，但运行时没动态申请。

**解决**：在 MapScreen 启动时用 `rememberLauncherForActivityResult` 动态申请定位权限。

```kotlin
val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { }
DisposableEffect(Unit) {
    if (need) permissionLauncher.launch(arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ))
    onDispose { }
}
```

### 6. 导航页返回键无反应

**原因**：没实现 `AMapNaviViewListener` 的返回回调。

**解决**：实现 `onNaviBackClick()` 和 `onNaviCancel()` 调用 `finish()`。

```kotlin
override fun onNaviBackClick(): Boolean { finish(); return true }
override fun onNaviCancel() { finish() }
```

### 7. 导航页只有箭头没有路线（核心坑）

**原因**：高德 AMapNaviView V11.x 的骑行导航**不会自动绘制路线**。`AMapNaviViewOptions.isAutoDrawRoute = true` 对骑行导航无效。官方 demo（RideRouteCalculateActivity）用的是老版本 SDK（10.0.600 jar）可以自动画线，但新版 AAR V11.2.000 骑行导航需要**手动用 `RouteOverLay` 画路线**。

**网上资料情况**：网上没有直接说"AMapNaviView V11 骑行导航不自动画路线"的博客或问答。但官方文档和更新日志里有线索：

- [骑行/步行路线规划文档](https://lbs.amap.com/api/android-navi-sdk/guide/route-plan/ride-route-plan) 的 `onCalculateRouteSuccess` 示例里写了两种处理方式："获取路线数据对象，进行规划路线的显示" 或 "直接开启导航"——暗示骑步行需要自己画线。
- 同一页的"骑步行导航适配问题"明确说："驾车升级自定义view看齐了组件，骑步行仍是历史实现"。
- [更新日志](https://lbs.amap.com/api/android-navi-sdk/changelog) V10.0.900（2024-11-06）写"骑步行图面内容全面对齐导航组件"——说明 V10.0.900 之前骑步行的图面渲染是旧的"历史实现"，跟驾车不一样，`isAutoDrawRoute` 对骑步行无效。
- [AMapNaviViewOptions 文档](http://a.amap.com/lbs/static/unzip/Android_Navi_Doc/com/amap/api/navi/AMapNaviViewOptions.html) 的 `setNaviType` 注释说："如果需要使用骑、步行模式，请在初始化 AMapNaviView 之前通过 `AMapNavi.setIsNaviTravelView(boolean)` 进行设置"。
- 开源项目 [JustLikeDidiNavi](https://github.com/yisingle/JustLikeDidiNavi) 就是手动用 `RouteOverLay` 画路线的，说明社区开发者早就知道骑步行要手动画线。

**解决**：在 `onCalculateRouteSuccess` 回调里，手动用 `RouteOverLay` 在 `AMapNaviView.map` 上画路线，然后再 `startNavi`。

```kotlin
override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) {
    val routeIds = result?.routeid
    if (routeIds != null && routeIds.isNotEmpty()) {
        drawRouteOverLay(routeIds[0])
    }
    mAMapNavi.startNavi(NaviType.GPS)
}

private fun drawRouteOverLay(routeId: Int) {
    val path = mAMapNavi.naviPaths[routeId] ?: return
    routeOverLay?.removeFromMap()
    routeOverLay?.destroy()
    routeOverLay = RouteOverLay(mAMapNaviView.map, path, this)
    routeOverLay?.setTrafficLine(true)
    routeOverLay?.addToMap()
    routeOverLay?.setTransparency(1.0f)
}
```

### 8. V11.x AMapNaviViewListener 新增回调

**原因**：V11.2.000 的 `AMapNaviViewListener` 接口新增了 11 个抽象方法，不实现会编译失败。

**解决**：补全所有新回调（空实现即可）：

```kotlin
override fun onStopSpeaking() {}
override fun onViewTypeChanged(pageType: AmapPageType?) {}
override fun onAMapNaviViewExit() {}
override fun onStrategyChanged(strategy: Int) {}
override fun onBroadcastModeChanged(mode: Int) {}
override fun onDayAndNightModeChanged(mode: Int) {}
override fun onScaleAutoChanged(isAuto: Boolean) {}
override fun onListenToVoiceDuringCallChanged(changed: Boolean) {}
override fun onControlMusicVolumeModeChanged(mode: Int) {}
override fun onEagleChanged(isEagle: Boolean) {}
override fun onNaviRouteHighlightChange(id: Long, type: Int) {}
```

### 9. 算路时机

**原因**：在 `onCreate` 里直接调 `calculateRideRoute` 时，`AMapNavi` 可能还没初始化完成。

**解决**：在 `onInitNaviSuccess` 回调里才算路。

```kotlin
override fun onInitNaviSuccess() {
    mAMapNavi.calculateRideRoute(startLatLng, endLatLng)
}
```

### 10. V11.2.000 不支持 32 位

**原因**：AAR 包只含 arm64-v8a 的 so 库。

**解决**：`build.gradle.kts` 里 `abiFilters` 只保留 `arm64-v8a`。

```kotlin
ndk {
    abiFilters += listOf("arm64-v8a")
}
```
