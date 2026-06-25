package com.example.bikenavi

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.baidu.mapapi.bikenavi.BikeNavigateHelper
import com.baidu.mapapi.bikenavi.adapter.IBEngineInitListener
import com.baidu.mapapi.bikenavi.adapter.IBRoutePlanListener
import com.baidu.mapapi.bikenavi.model.BikeRoutePlanError
import com.baidu.mapapi.bikenavi.params.BikeNaviLaunchParam
import com.baidu.mapapi.bikenavi.params.BikeRouteNodeInfo
import com.baidu.mapapi.model.LatLng

/**
 * 骑行导航准备页：初始化导航引擎 → 发起算路 → 成功后跳转诱导页
 */
class BikeNaviPrepareActivity : AppCompatActivity() {

    private var startPt: LatLng? = null
    private var endPt: LatLng? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sLat = intent.getDoubleExtra("startLat", 0.0)
        val sLng = intent.getDoubleExtra("startLng", 0.0)
        val eLat = intent.getDoubleExtra("endLat", 0.0)
        val eLng = intent.getDoubleExtra("endLng", 0.0)
        startPt = LatLng(sLat, sLng)
        endPt = LatLng(eLat, eLng)

        // 简单加载 UI
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        container.addView(ProgressBar(this))
        container.addView(TextView(this).apply {
            text = "正在初始化导航引擎…"
            setPadding(0, 24, 0, 0)
            gravity = Gravity.CENTER
        })
        setContentView(container)

        initNaviEngine()
    }

    private fun initNaviEngine() {
        BikeNavigateHelper.getInstance().initNaviEngine(this, object : IBEngineInitListener {
            override fun engineInitSuccess() {
                runOnUiThread { routePlan() }
            }

            override fun engineInitFail() {
                runOnUiThread {
                    Toast.makeText(this@BikeNaviPrepareActivity, "导航引擎初始化失败", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        })
    }

    private fun routePlan() {
        val startNode = BikeRouteNodeInfo().apply { location = startPt }
        val endNode = BikeRouteNodeInfo().apply { location = endPt }
        val param = BikeNaviLaunchParam()
            .startNodeInfo(startNode)
            .endNodeInfo(endNode)
            .vehicle(0) // 0=普通骑行 1=电动车

        BikeNavigateHelper.getInstance().routePlanWithRouteNode(param, object : IBRoutePlanListener {
            override fun onRoutePlanStart() {
                runOnUiThread {
                    Toast.makeText(this@BikeNaviPrepareActivity, "正在规划路线…", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onRoutePlanSuccess() {
                runOnUiThread {
                    val intent = Intent(this@BikeNaviPrepareActivity, BikeNaviGuideActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }

            override fun onRoutePlanFail(error: BikeRoutePlanError?) {
                runOnUiThread {
                    Toast.makeText(
                        this@BikeNaviPrepareActivity,
                        "路线规划失败: ${error}",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        })
    }
}
