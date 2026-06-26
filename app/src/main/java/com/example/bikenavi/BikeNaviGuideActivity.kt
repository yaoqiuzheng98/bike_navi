package com.example.bikenavi

import android.os.Bundle
import com.example.bikenavi.R
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.AMapException
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sLat = intent.getDoubleExtra("startLat", 0.0)
        val sLng = intent.getDoubleExtra("startLng", 0.0)
        val eLat = intent.getDoubleExtra("endLat", 0.0)
        val eLng = intent.getDoubleExtra("endLng", 0.0)
        startLatLng = NaviLatLng(sLat, sLng)
        endLatLng = NaviLatLng(eLat, eLng)

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
        // 开始导航
        mAMapNavi.startNavi(NaviType.GPS)
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
    override fun onLocationChange(location: AMapNaviLocation?) {}
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
            if (points.isNotEmpty()) {
                for (p in points) {
                    mAMapNaviView.map.addMarker(
                        MarkerOptions()
                            .position(p.latLng)
                            .title(p.name)
                    )
                }
                Log.d("BikeNaviGuide", "已标记 ${points.size} 个点位")
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
