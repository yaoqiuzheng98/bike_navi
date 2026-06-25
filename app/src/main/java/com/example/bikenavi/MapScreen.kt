package com.example.bikenavi

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baidu.mapapi.map.BaiduMap
import com.baidu.mapapi.map.BitmapDescriptor
import com.baidu.mapapi.map.BitmapDescriptorFactory
import com.baidu.mapapi.map.MapStatusUpdateFactory
import com.baidu.mapapi.map.MapView
import com.baidu.mapapi.map.MarkerOptions
import com.baidu.mapapi.map.OverlayOptions
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.route.BikingRouteLine
import com.baidu.mapapi.search.route.BikingRoutePlanOption
import com.baidu.mapapi.search.route.BikingRouteResult
import com.baidu.mapapi.search.route.DrivingRouteResult
import com.baidu.mapapi.search.route.IndoorRouteResult
import com.baidu.mapapi.search.route.IntegralRouteResult
import com.baidu.mapapi.search.route.MassTransitRouteResult
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener
import com.baidu.mapapi.search.route.PlanNode
import com.baidu.mapapi.search.route.RoutePlanSearch
import com.baidu.mapapi.search.route.TransitRouteResult
import com.baidu.mapapi.search.route.WalkingRouteResult

/**
 * 主界面：搜索区 + 地图 + 操作按钮
 */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var startPt by remember { mutableStateOf<LatLng?>(null) }
    var endPt by remember { mutableStateOf<LatLng?>(null) }

    // 地图引用
    val mapView = remember { MapView(context) }
    val baiduMap = remember { mapView.map }

    // 路线检索
    val routeSearch = remember { RoutePlanSearch.newInstance() }
    val routeListener = remember {
        object : OnGetRoutePlanResultListener {
            override fun onGetBikingRouteResult(result: BikingRouteResult?) {
                if (result == null || result.routeLines.isNullOrEmpty()) {
                    Toast.makeText(context, "未检索到骑行路线", Toast.LENGTH_SHORT).show()
                    return
                }
                val line: BikingRouteLine = result.routeLines[0]
                baiduMap.clear()
                startPt?.let { addMarker(baiduMap, it, true) }
                endPt?.let { addMarker(baiduMap, it, false) }
                drawBikingRoute(baiduMap, line)
                Toast.makeText(context, "路线规划成功，距离 ${line.distance} 米", Toast.LENGTH_SHORT).show()
            }

            override fun onGetDrivingRouteResult(result: DrivingRouteResult?) {}
            override fun onGetTransitRouteResult(result: TransitRouteResult?) {}
            override fun onGetWalkingRouteResult(result: WalkingRouteResult?) {}
            override fun onGetIndoorRouteResult(result: IndoorRouteResult?) {}
            override fun onGetMassTransitRouteResult(result: MassTransitRouteResult?) {}
            override fun onGetIntegralRouteResult(result: IntegralRouteResult?) {}
        }
    }

    DisposableEffect(Unit) {
        routeSearch.setOnGetRoutePlanResultListener(routeListener)
        onDispose {
            routeSearch.destroy()
        }
    }

    // MapView 生命周期绑定
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> mapView.onResume()
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                androidx.lifecycle.Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 搜索区 —— 顶部加状态栏内边距，避免刘海屏遮挡
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlaceSearchField(
                    label = "起点",
                    onSelected = { sug ->
                        startPt = sug.pt
                        sug.pt?.let {
                            moveMap(baiduMap, it)
                            baiduMap.clear()
                            addMarker(baiduMap, it, true)
                            endPt?.let { e -> addMarker(baiduMap, e, false) }
                        }
                    },
                )
                PlaceSearchField(
                    label = "终点",
                    onSelected = { sug ->
                        endPt = sug.pt
                        sug.pt?.let {
                            moveMap(baiduMap, it)
                            baiduMap.clear()
                            startPt?.let { s -> addMarker(baiduMap, s, true) }
                            addMarker(baiduMap, it, false)
                        }
                    },
                )
            }

            // 地图
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 操作按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val s = startPt
                        val e = endPt
                        if (s == null || e == null) {
                            Toast.makeText(context, "请先选择起点和终点", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        baiduMap.clear()
                        addMarker(baiduMap, s, true)
                        addMarker(baiduMap, e, false)
                        routeSearch.bikingSearch(
                            BikingRoutePlanOption()
                                .from(PlanNode.withLocation(s))
                                .to(PlanNode.withLocation(e))
                                .ridingType(0)
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("查看路线") }

                Button(
                    onClick = {
                        val s = startPt
                        val e = endPt
                        if (s == null || e == null) {
                            Toast.makeText(context, "请先选择起点和终点", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val intent = Intent(context, BikeNaviPrepareActivity::class.java).apply {
                            putExtra("startLat", s.latitude)
                            putExtra("startLng", s.longitude)
                            putExtra("endLat", e.latitude)
                            putExtra("endLng", e.longitude)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("开始导航") }
            }
        }
    }
}

private fun moveMap(baiduMap: BaiduMap, pt: LatLng) {
    val update = MapStatusUpdateFactory.newLatLngZoom(pt, 15f)
    baiduMap.animateMapStatus(update)
}

private fun addMarker(baiduMap: BaiduMap, pt: LatLng?, isStart: Boolean) {
    if (pt == null) return
    val bd = makeCircleBitmap(isStart)
    val option: OverlayOptions = MarkerOptions()
        .position(pt)
        .icon(bd)
        .zIndex(5)
    baiduMap.addOverlay(option)
}

/** 生成一个简单的圆形 marker BitmapDescriptor */
private fun makeCircleBitmap(isStart: Boolean): BitmapDescriptor {
    val size = 80
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isStart) Color.GREEN else Color.RED
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 2f, size / 3f, paint)
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 6f
    canvas.drawCircle(size / 2f, size / 3f, size / 3f, paint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

/**
 * 手动绘制骑行路线（用 Polyline），不依赖 BikingRouteOverlay。
 * 从 BikingRouteLine 中提取所有经过点。
 */
private fun drawBikingRoute(baiduMap: BaiduMap, line: BikingRouteLine) {
    val allPoints = mutableListOf<LatLng>()
    for (step in line.allStep) {
        allPoints.addAll(step.wayPoints)
    }
    if (allPoints.size < 2) return

    val polyline = com.baidu.mapapi.map.PolylineOptions()
        .points(allPoints)
        .width(12f)
        .color(0xFF2196F3.toInt())
    baiduMap.addOverlay(polyline)

    // 缩放到路线范围
    val boundsBuilder = com.baidu.mapapi.model.LatLngBounds.Builder()
    for (pt in allPoints) {
        boundsBuilder.include(pt)
    }
    baiduMap.animateMapStatus(MapStatusUpdateFactory.newLatLngBounds(boundsBuilder.build()))
}
