package com.example.bikenavi

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.OutlinedTextField
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
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptor
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.RouteSearch

/**
 * 主界面：城市+终点搜索 + 地图 + 操作按钮
 * 起点自动使用当前定位，只需搜索终点。
 */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 当前定位作为起点
    var startPt by remember { mutableStateOf<LatLng?>(null) }
    var endPt by remember { mutableStateOf<LatLng?>(null) }
    var city by remember { mutableStateOf("广州") }
    var locatedCity by remember { mutableStateOf("") }

    // 运行时申请定位权限
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        if (granted) {
            // 权限授予后开始定位
            startLocation(context) { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                startPt = latLng
                locatedCity = loc.city ?: ""
                if (locatedCity.isNotBlank()) city = locatedCity
                Log.d("MapScreen", "定位成功: ${loc.latitude},${loc.longitude} city=${loc.city}")
            }
        }
    }
    DisposableEffect(Unit) {
        val perms = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        val need = perms.any {
            context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (need) permissionLauncher.launch(perms)
        else {
            // 已有权限，直接定位
            startLocation(context) { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                startPt = latLng
                locatedCity = loc.city ?: ""
                if (locatedCity.isNotBlank()) city = locatedCity
                Log.d("MapScreen", "定位成功: ${loc.latitude},${loc.longitude} city=${loc.city}")
            }
        }
        onDispose { }
    }

    // 地图引用
    val mapView = remember { MapView(context) }
    val aMap = remember { mapView.map }

    // MapView 必须调用 onCreate 才能显示地图
    DisposableEffect(Unit) {
        mapView.onCreate(null)
        // 显示定位蓝点
        val myLocationStyle = MyLocationStyle()
        myLocationStyle.showMyLocation(true)
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)
        aMap.myLocationStyle = myLocationStyle
        aMap.isMyLocationEnabled = true
        onDispose { }
    }

    // 路线检索
    val routeSearch = remember { RouteSearch(context) }
    val routeListener = remember {
        object : RouteSearch.OnRouteSearchListener {
            override fun onRideRouteSearched(result: com.amap.api.services.route.RideRouteResult?, code: Int) {
                if (code != 1000 || result == null || result.paths.isNullOrEmpty()) {
                    Toast.makeText(context, "未检索到骑行路线", Toast.LENGTH_SHORT).show()
                    return
                }
                val path = result.paths[0]
                aMap.clear()
                startPt?.let { addMarker(aMap, it, true) }
                endPt?.let { addMarker(aMap, it, false) }
                drawRideRoute(aMap, path)
                Toast.makeText(context, "路线规划成功，距离 ${path.distance} 米", Toast.LENGTH_SHORT).show()
            }
            override fun onDriveRouteSearched(result: com.amap.api.services.route.DriveRouteResult?, code: Int) {}
            override fun onWalkRouteSearched(result: com.amap.api.services.route.WalkRouteResult?, code: Int) {}
            override fun onBusRouteSearched(result: com.amap.api.services.route.BusRouteResult?, code: Int) {}
        }
    }

    DisposableEffect(Unit) {
        routeSearch.setRouteSearchListener(routeListener)
        onDispose { }
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
            // 搜索区
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 显示当前定位状态
                Text(
                    text = if (startPt != null) "当前定位：${locatedCity.ifBlank { "已定位" }}" else "正在定位中...",
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text("城市") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PlaceSearchField(
                    label = "终点",
                    city = city,
                    onSelected = { sug ->
                        endPt = sug.pt
                        sug.pt?.let {
                            moveMap(aMap, it)
                            aMap.clear()
                            startPt?.let { s -> addMarker(aMap, s, true) }
                            addMarker(aMap, it, false)
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
                        if (s == null) {
                            Toast.makeText(context, "尚未定位到当前位置", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        if (e == null) {
                            Toast.makeText(context, "请先选择终点", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        aMap.clear()
                        addMarker(aMap, s, true)
                        addMarker(aMap, e, false)
                        val from = LatLonPoint(s.latitude, s.longitude)
                        val to = LatLonPoint(e.latitude, e.longitude)
                        val query = RouteSearch.RideRouteQuery(RouteSearch.FromAndTo(from, to))
                        routeSearch.calculateRideRouteAsyn(query)
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("查看路线") }

                Button(
                    onClick = {
                        val s = startPt
                        val e = endPt
                        if (s == null) {
                            Toast.makeText(context, "尚未定位到当前位置", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (e == null) {
                            Toast.makeText(context, "请先选择终点", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val intent = Intent(context, BikeNaviGuideActivity::class.java).apply {
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

/**
 * 启动高德单次定位
 */
private fun startLocation(context: android.content.Context, onLocated: (AMapLocation) -> Unit) {
    try {
        val locationClient = AMapLocationClient(context)
        val option = AMapLocationClientOption()
        // 单次定位
        option.isOnceLocation = true
        // 高精度模式
        option.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
        locationClient.setLocationOption(option)
        locationClient.setLocationListener { loc ->
            if (loc != null && loc.errorCode == 0) {
                onLocated(loc)
            } else {
                Log.e("MapScreen", "定位失败 code=${loc?.errorCode} info=${loc?.errorInfo}")
            }
            locationClient.stopLocation()
            locationClient.onDestroy()
        }
        locationClient.startLocation()
    } catch (e: Exception) {
        Log.e("MapScreen", "定位初始化失败", e)
    }
}

private fun moveMap(aMap: AMap, pt: LatLng) {
    aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pt, 15f))
}

private fun addMarker(aMap: AMap, pt: LatLng?, isStart: Boolean) {
    if (pt == null) return
    val bd = makeCircleBitmap(isStart)
    aMap.addMarker(
        MarkerOptions()
            .position(pt)
            .icon(bd)
            .draggable(false)
    )
}

private fun makeCircleBitmap(isStart: Boolean): BitmapDescriptor {
    val size = 80
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isStart) Color.GREEN else Color.RED
        style = Paint.Style.FILL
    }
    canvas.drawCircle(size / 2f, size / 3f, size / 3f, paint)
    paint.color = Color.WHITE
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 6f
    canvas.drawCircle(size / 2f, size / 3f, size / 3f, paint)
    return BitmapDescriptorFactory.fromBitmap(bmp)
}

/**
 * 绘制骑行路线
 */
private fun drawRideRoute(aMap: AMap, path: com.amap.api.services.route.RidePath) {
    val allPoints = mutableListOf<LatLng>()
    for (step in path.steps) {
        for (point in step.polyline) {
            allPoints.add(LatLng(point.latitude, point.longitude))
        }
    }
    if (allPoints.size < 2) return

    aMap.addPolyline(
        PolylineOptions()
            .addAll(allPoints)
            .width(18f)
            .color(0xFF2196F3.toInt())
    )

    val boundsBuilder = LatLngBounds.Builder()
    for (pt in allPoints) {
        boundsBuilder.include(pt)
    }
    aMap.animateCamera(
        CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 200)
    )
}
