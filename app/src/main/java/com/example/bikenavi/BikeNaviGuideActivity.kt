package com.example.bikenavi

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.bikenavi.BikeNavigateHelper
import com.baidu.mapapi.bikenavi.adapter.IBRouteGuidanceListener
import com.baidu.mapapi.bikenavi.model.BikeRouteDetailInfo
import com.baidu.mapapi.bikenavi.model.IBRouteIconInfo
import com.baidu.mapapi.walknavi.model.RouteGuideKind

/**
 * 骑行导航诱导页：展示导航 View，执行 turn-by-turn 导航
 */
class BikeNaviGuideActivity : AppCompatActivity() {

    private lateinit var naviHelper: BikeNavigateHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        naviHelper = BikeNavigateHelper.getInstance()

        // 获取诱导页面地图展示 View
        val view: View? = naviHelper.onCreate(this)
        if (view != null) {
            setContentView(view)
        } else {
            Toast.makeText(this, "导航视图创建失败", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // 设置诱导监听
        naviHelper.setRouteGuidanceListener(this, object : IBRouteGuidanceListener {
            override fun onRouteGuideIconInfoUpdate(iconInfo: IBRouteIconInfo?) {}
            override fun onRouteGuideIconUpdate(icon: Drawable?) {}
            override fun onRouteGuideKind(routeGuideKind: RouteGuideKind?) {}
            override fun onRoadGuideTextUpdate(charSequence: CharSequence?, charSequence1: CharSequence?) {}
            override fun onRemainDistanceUpdate(charSequence: CharSequence?) {}
            override fun onRemainTimeUpdate(charSequence: CharSequence?) {}
            override fun onGpsStatusChange(charSequence: CharSequence?, drawable: Drawable?) {}
            override fun onRouteFarAway(charSequence: CharSequence?, drawable: Drawable?) {}
            override fun onRoutePlanYawing(charSequence: CharSequence?, drawable: Drawable?) {}
            override fun onReRouteComplete() {}
            override fun onArriveDest() {
                Toast.makeText(this@BikeNaviGuideActivity, "已到达目的地", Toast.LENGTH_LONG).show()
            }
            override fun onVibrate() {}
            override fun onGetRouteDetailInfo(bikeRouteDetailInfo: BikeRouteDetailInfo?) {}
            override fun onNaviLocationUpdate() {}
        })

        // 开始导航
        naviHelper.startBikeNavi(this)
    }

    override fun onResume() {
        super.onResume()
        if (::naviHelper.isInitialized) naviHelper.resume()
    }

    override fun onPause() {
        super.onPause()
        if (::naviHelper.isInitialized) naviHelper.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::naviHelper.isInitialized) naviHelper.quit()
    }
}
