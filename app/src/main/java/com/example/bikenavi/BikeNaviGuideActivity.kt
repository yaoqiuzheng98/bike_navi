package com.example.bikenavi

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.example.bikenavi.R
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amap.api.maps.AMapException
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.BitmapDescriptorFactory
import android.graphics.BitmapFactory
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import com.amap.api.navi.AMapNavi
import com.amap.api.navi.AMapNaviListener
import com.amap.api.navi.AMapNaviView
import com.amap.api.navi.AMapNaviViewListener
import com.amap.api.navi.AmapPageType
import com.amap.api.navi.enums.NaviType
import com.amap.api.navi.model.AMapCalcRouteResult
import com.amap.api.navi.model.AMapLaneInfo
import com.amap.api.navi.model.AMapModelCross
import com.amap.api.navi.model.AMapNaviCameraInfo
import com.amap.api.navi.model.AMapNaviCross
import com.amap.api.navi.model.AMapNaviLocation
import com.amap.api.navi.model.AMapNaviRouteNotifyData
import com.amap.api.navi.model.AMapNaviTrafficFacilityInfo
import com.amap.api.navi.model.AMapServiceAreaInfo
import com.amap.api.navi.model.AimLessModeCongestionInfo
import com.amap.api.navi.model.AimLessModeStat
import com.amap.api.navi.model.NaviInfo
import com.amap.api.navi.model.NaviLatLng
import com.amap.api.navi.view.RouteOverLay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 骑行导航诱导页：参照高德官方 demo 的 RideRouteCalculateActivity 模式。
 * 关键：算路在 onInitNaviSuccess 回调里调用，不在 onCreate 里直接调。
 */
class BikeNaviGuideActivity : AppCompatActivity(), AMapNaviListener, AMapNaviViewListener {

    private lateinit var mAMapNaviView: AMapNaviView
    private lateinit var mAMapNavi: AMapNavi
    private var startLatLng: NaviLatLng? = null
    private var endLatLng: NaviLatLng? = null
    private var routeOverLay: RouteOverLay? = null
    private var btnRecenter: Button? = null
    private var alertOverlay: View? = null
    private var isEmulatorMode = false
    // 当前导航位置（用于回中）
    private var currentNaviLocation: AMapNaviLocation? = null
    // 5秒无操作自动回中
    private val handler = Handler(Looper.getMainLooper())
    private val recenterRunnable = Runnable { recenterToNavi() }
    // 标记点播报
    private var bikePoints: List<BikePoint> = emptyList()
    private val passedPointIds = mutableSetOf<Long>()     // 已路过的点
    private val announcingPointIds = mutableSetOf<Long>() // 正在播报的点
    private val lastAnnounceDistanceMap = mutableMapOf<Long, Int>() // 上次播报时的距离（米）
    private val minDistanceMap = mutableMapOf<Long, Double>() // 每个点记录过的最近距离
    private var tts: TextToSpeech? = null
    // 播报距离阈值（米）
    private val ANNOUNCE_DISTANCE = 500.0
    // 路过判定：曾经接近到此距离内，且开始远离即算路过
    private val PASSED_DISTANCE = 150.0
    // 每 N 米播报一次
    private val ANNOUNCE_STEP = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sLat = intent.getDoubleExtra("startLat", 0.0)
        val sLng = intent.getDoubleExtra("startLng", 0.0)
        val eLat = intent.getDoubleExtra("endLat", 0.0)
        val eLng = intent.getDoubleExtra("endLng", 0.0)
        startLatLng = NaviLatLng(sLat, sLng)
        endLatLng = NaviLatLng(eLat, eLng)
        isEmulatorMode = intent.getBooleanExtra("emulator", false)

        // 1. 先获取 AMapNavi 实例并添加监听（参照 demo BaseActivity）
        try {
            mAMapNavi = AMapNavi.getInstance(applicationContext)
            mAMapNavi.addAMapNaviListener(this)
            mAMapNavi.setUseInnerVoice(true, true)
        } catch (e: AMapException) {
            e.printStackTrace()
        }

        // 2. 设置布局并初始化 AMapNaviView
        setContentView(R.layout.activity_navi_guide)
        mAMapNaviView = findViewById(R.id.navi_view)
        // 使用 AmapPageType.NAVI 明确指定为导航界面（已算路场景）
        mAMapNaviView.onCreate(savedInstanceState, this, AmapPageType.NAVI)
        mAMapNaviView.setAMapNaviViewListener(this)

        // 3. 回到导航按钮
        btnRecenter = findViewById(R.id.btn_recenter)
        btnRecenter?.setOnClickListener {
            recenterToNavi()
        }

        // 4. 红色闪烁预警遮罩
        alertOverlay = findViewById(R.id.alert_overlay)

        // 5. 地图触摸监听：用户滑动地图后，5秒无操作自动回中
        mAMapNaviView.map.setOnMapTouchListener {
            // 用户操作了地图，显示回中按钮，重置5秒计时
            btnRecenter?.visibility = View.VISIBLE
            handler.removeCallbacks(recenterRunnable)
            handler.postDelayed(recenterRunnable, 5000)
        }

        // 6. 初始化 TTS 语音播报
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                Log.d("BikeNaviGuide", "TTS 初始化成功")
            } else {
                Log.e("BikeNaviGuide", "TTS 初始化失败 status=$status")
            }
        }
    }

    /**
     * 语音播报标记点
     */
    private fun speak(text: String) {
        startAlertFlash()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "point_announce_${System.currentTimeMillis()}")
    }

    /**
     * 开始红色边框闪烁
     */
    private fun startAlertFlash() {
        alertOverlay?.let { overlay ->
            overlay.visibility = View.VISIBLE
            val anim = AnimationUtils.loadAnimation(this, R.anim.alert_flash)
            overlay.startAnimation(anim)
        }
    }

    /**
     * 停止红色边框闪烁
     */
    private fun stopAlertFlash() {
        alertOverlay?.let { overlay ->
            overlay.clearAnimation()
            overlay.visibility = View.GONE
        }
    }

    /**
     * 检查附近标记点：
     * - 进入 500 米范围 → 播报一次
     * - 之后每接近 100 米 → 播报一次
     * - 曾经接近到 150 米内且开始远离 → 算路过，停止播报
     * - 远离超过 500 米 → 停止播报
     */
    private fun checkNearbyPoints(lat: Double, lng: Double) {
        val current = LatLng(lat, lng)
        for (p in bikePoints) {
            if (p.id in passedPointIds) continue
            val d = distanceMeters(current, p.latLng)
            val dMeters = d.toInt()
            val minD = minDistanceMap[p.id] ?: Double.MAX_VALUE

            if (d <= ANNOUNCE_DISTANCE) {
                // 记录最近距离
                if (d < minD) {
                    minDistanceMap[p.id] = d
                }
                // 曾经接近到 PASSED_DISTANCE 内，且开始远离 → 算路过
                if (minD <= PASSED_DISTANCE && d > minD + 10) {
                    passedPointIds.add(p.id)
                    announcingPointIds.remove(p.id)
                    lastAnnounceDistanceMap.remove(p.id)
                    minDistanceMap.remove(p.id)
                    if (announcingPointIds.isEmpty()) stopAlertFlash()
                    Log.d("BikeNaviGuide", "已路过: ${p.name} (最近${minD.toInt()}米)")
                    continue
                }
                // 判断是否需要播报：首次进入 或 距离每减少 100 米
                val lastD = lastAnnounceDistanceMap[p.id]
                if (lastD == null) {
                    // 首次进入 500 米范围
                    lastAnnounceDistanceMap[p.id] = dMeters
                    announcingPointIds.add(p.id)
                    speak("前方${dMeters}米，即将经过${p.name}")
                    Log.d("BikeNaviGuide", "播报: ${p.name} (${dMeters}米)")
                } else if (dMeters <= lastD - ANNOUNCE_STEP) {
                    // 又近了 100 米
                    lastAnnounceDistanceMap[p.id] = dMeters
                    speak("前方${dMeters}米，即将经过${p.name}")
                    Log.d("BikeNaviGuide", "播报: ${p.name} (${dMeters}米)")
                }
            } else {
                // 远离超过 500 米，停止播报
                announcingPointIds.remove(p.id)
                lastAnnounceDistanceMap.remove(p.id)
                minDistanceMap.remove(p.id)
                if (announcingPointIds.isEmpty()) stopAlertFlash()
            }
        }
    }

    /**
     * Haversine 距离公式（米）
     */
    private fun distanceMeters(a: LatLng, b: LatLng): Double {
        val r = 6371000.0
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLng = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * asin(sqrt(h))
    }

    /**
     * 回到当前导航位置（回中）
     */
    private fun recenterToNavi() {
        val loc = currentNaviLocation
        if (loc != null) {
            val coord = loc.coord
            if (coord != null) {
                val latLng = LatLng(coord.latitude, coord.longitude)
                mAMapNaviView.map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(latLng, 18f)
                )
            }
        }
        btnRecenter?.visibility = View.GONE
        handler.removeCallbacks(recenterRunnable)
    }

    // ===== AMapNaviListener 关键回调 =====

    override fun onInitNaviSuccess() {
        Log.d("BikeNaviGuide", "onInitNaviSuccess -> 开始算路")
        // 算路在 onInitNaviSuccess 里调用！
        mAMapNavi.calculateRideRoute(startLatLng, endLatLng)
    }

    override fun onInitNaviFailure() {
        Log.d("BikeNaviGuide", "onInitNaviFailure")
        runOnUiThread { Toast.makeText(this, "导航引擎初始化失败", Toast.LENGTH_LONG).show() }
    }

    override fun onCalculateRouteSuccess(ids: IntArray?) {
        Log.d("BikeNaviGuide", "onCalculateRouteSuccess ids")
    }

    override fun onCalculateRouteFailure(errorCode: Int) {
        Log.d("BikeNaviGuide", "onCalculateRouteFailure code=$errorCode")
        runOnUiThread {
            Toast.makeText(this, "路线规划失败，错误码: $errorCode", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCalculateRouteSuccess(result: AMapCalcRouteResult?) {
        Log.d("BikeNaviGuide", "onCalculateRouteSuccess result")
        // 手动用 RouteOverLay 画路线（参照 JustLikeDidiNavi 项目）
        val routeIds = result?.routeid
        if (routeIds != null && routeIds.isNotEmpty()) {
            drawRouteOverLay(routeIds[0])
        }
        // 算路成功后开始导航
        if (isEmulatorMode) {
            mAMapNavi.startNavi(NaviType.EMULATOR)
        } else {
            mAMapNavi.startNavi(NaviType.GPS)
        }
    }

    /**
     * 手动在 NaviView 的地图上画路线
     */
    private fun drawRouteOverLay(routeId: Int) {
        try {
            val naviPaths = mAMapNavi.naviPaths
            val path = naviPaths[routeId] ?: return
            // 先清除旧路线
            routeOverLay?.removeFromMap()
            routeOverLay?.destroy()
            // 用 RouteOverLay 画路线
            routeOverLay = RouteOverLay(mAMapNaviView.map, path, this)
            routeOverLay?.setTrafficLine(true)
            routeOverLay?.addToMap()
            routeOverLay?.setTransparency(1.0f)
            Log.d("BikeNaviGuide", "drawRouteOverLay done, routeId=$routeId")
        } catch (e: Exception) {
            Log.e("BikeNaviGuide", "drawRouteOverLay failed", e)
        }
    }

    override fun onCalculateRouteFailure(result: AMapCalcRouteResult?) {
        Log.d("BikeNaviGuide", "onCalculateRouteFailure result code=${result?.errorCode}")
        runOnUiThread {
            Toast.makeText(this, "路线规划失败: ${result?.errorDescription}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStartNavi(naviType: Int) {
        Log.d("BikeNaviGuide", "onStartNavi type=$naviType")
    }
    override fun onTrafficStatusUpdate() {}
    override fun onLocationChange(location: AMapNaviLocation?) {
        currentNaviLocation = location
        // 检查附近标记点并播报
        val coord = location?.coord
        if (coord != null && bikePoints.isNotEmpty()) {
            checkNearbyPoints(coord.latitude, coord.longitude)
        }
    }
    override fun onArriveDestination() {
        Toast.makeText(this, "已到达目的地", Toast.LENGTH_LONG).show()
    }
    override fun onArrivedWayPoint(wayID: Int) {}
    override fun onGetNavigationText(text: String?) {}
    override fun onGetNavigationText(type: Int, text: String?) {}
    override fun onReCalculateRouteForYaw() {}
    override fun onReCalculateRouteForTrafficJam() {}
    override fun onNaviInfoUpdate(naviInfo: NaviInfo?) {}
    override fun onEndEmulatorNavi() {}
    override fun onGpsOpenStatus(gpsOpen: Boolean) {}
    override fun onNaviRouteNotify(routeNotifyData: AMapNaviRouteNotifyData?) {}
    override fun onServiceAreaUpdate(infos: Array<out AMapServiceAreaInfo>?) {}
    override fun onPlayRing(ringType: Int) {}
    override fun onGpsSignalWeak(gpsSignalWeak: Boolean) {}
    override fun updateCameraInfo(cameraInfos: Array<out AMapNaviCameraInfo>?) {}
    override fun updateIntervalCameraInfo(startCamera: AMapNaviCameraInfo?, endCamera: AMapNaviCameraInfo?, status: Int) {}
    override fun showCross(cross: AMapNaviCross?) {}
    override fun hideCross() {}
    override fun showModeCross(modeCross: AMapModelCross?) {}
    override fun hideModeCross() {}
    override fun showLaneInfo(laneInfos: Array<out AMapLaneInfo>?, laneRecommended: ByteArray?, laneBackRecommended: ByteArray?) {}
    override fun showLaneInfo(laneInfo: AMapLaneInfo?) {}
    override fun hideLaneInfo() {}
    override fun notifyParallelRoad(parallelRoadType: Int) {}
    override fun OnUpdateTrafficFacility(trafficFacilityInfos: Array<out AMapNaviTrafficFacilityInfo>?) {}
    override fun OnUpdateTrafficFacility(trafficFacilityInfo: AMapNaviTrafficFacilityInfo?) {}
    override fun updateAimlessModeStatistics(stat: AimLessModeStat?) {}
    override fun updateAimlessModeCongestionInfo(info: AimLessModeCongestionInfo?) {}

    // ===== AMapNaviViewListener =====

    override fun onNaviCancel() {
        Log.d("BikeNaviGuide", "onNaviCancel")
        finish()
    }

    override fun onNaviBackClick(): Boolean {
        Log.d("BikeNaviGuide", "onNaviBackClick")
        finish()
        return true
    }

    override fun onNaviSetting() {}
    override fun onNaviMapMode(mapMode: Int) {}
    override fun onNaviTurnClick() {}
    override fun onNextRoadClick() {}
    override fun onScanViewButtonClick() {}
    override fun onLockMap(lockMap: Boolean) {}
    override fun onNaviViewLoaded() {
        Log.d("BikeNaviGuide", "onNaviViewLoaded")
        // 导航地图加载完成后，加载后端点位并标记到地图上
        loadBikePoints()
    }

    /**
     * 从后端加载点位并标记到导航地图上
     */
    private fun loadBikePoints() {
        GlobalScope.launch(Dispatchers.Main) {
            val points = withContext(Dispatchers.IO) { ApiClient.fetchPoints() }
            if (points == null) {
                Log.w("BikeNaviGuide", "版本过低，无法加载点位")
                return@launch
            }
            bikePoints = points
            if (points.isNotEmpty()) {
                val original = BitmapFactory.decodeResource(resources, R.drawable.camera)
                val scaled = android.graphics.Bitmap.createScaledBitmap(original!!, 60, 60, true)
                val icon = BitmapDescriptorFactory.fromBitmap(scaled)
                for (p in points) {
                    mAMapNaviView.map.addMarker(
                        MarkerOptions()
                            .position(p.latLng)
                            .title(p.name)
                            .icon(icon)
                    )
                }
                Log.d("BikeNaviGuide", "已标记 ${points.size} 个点位，播报就绪")
            }
        }
    }
    override fun onMapTypeChanged(mapType: Int) {}
    override fun onNaviViewShowMode(showMode: Int) {}
    // V11.x 新增回调
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

    // ===== 生命周期 =====

    override fun onResume() {
        super.onResume()
        if (::mAMapNaviView.isInitialized) mAMapNaviView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::mAMapNaviView.isInitialized) mAMapNaviView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(recenterRunnable)
        announcingPointIds.clear()
        stopAlertFlash()
        tts?.stop()
        tts?.shutdown()
        routeOverLay?.removeFromMap()
        routeOverLay?.destroy()
        if (::mAMapNaviView.isInitialized) mAMapNaviView.onDestroy()
        if (::mAMapNavi.isInitialized) {
            mAMapNavi.stopNavi()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mAMapNaviView.isInitialized) mAMapNaviView.onSaveInstanceState(outState)
    }
}
