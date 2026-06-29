package com.example.bikenavi

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.MyLocationStyle
import com.amap.api.maps.model.PolylineOptions
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.RouteSearch

/**
 * 主界面：终点搜索 + 地图 + 操作按钮
 * 起点自动使用当前定位，城市自动从定位获取。
 */
@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // UserId 弹窗
    var showUserIdDialog by remember { mutableStateOf(!UserIdManager.hasUserId()) }
    var userIdInput by remember { mutableStateOf("") }
    val isFirstTime = remember { !UserIdManager.hasUserId() }

    if (showUserIdDialog) {
        AlertDialog(
            onDismissRequest = {
                // 首次必须输入，已有 UserId 时可以取消
                if (!isFirstTime) showUserIdDialog = false
            },
            title = { Text(if (isFirstTime) "请输入用户ID" else "修改用户ID") },
            text = {
                Column {
                    if (isFirstTime) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "添加客服微信获取用户ID\n微信号：WATF0908",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("微信", "WATF0908"))
                                    Toast.makeText(context, "已复制微信号", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) { Text("复制微信号") }
                        }
                    }
                    OutlinedTextField(
                        value = userIdInput,
                        onValueChange = { userIdInput = it },
                        label = { Text("用户ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = userIdInput.isNotBlank(),
                    onClick = {
                        UserIdManager.saveUserId(userIdInput.trim())
                        showUserIdDialog = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                if (!isFirstTime) {
                    TextButton(onClick = { showUserIdDialog = false }) { Text("取消") }
                }
            },
        )
    }

    // 当前定位作为起点
    var startPt by remember { mutableStateOf<LatLng?>(null) }
    var endPt by remember { mutableStateOf<LatLng?>(null) }
    var city by remember { mutableStateOf("") }
    // 后端点位数据
    var bikePoints by remember { mutableStateOf<List<BikePoint>>(emptyList()) }
    // 起终点标记（清除时只移除这些，不影响后端点位标记）
    val routeMarkers = remember { mutableStateOf<List<Marker>>(emptyList()) }

    // 地图引用
    val mapView = remember { MapView(context) }
    val aMap = remember { mapView.map }

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
                // 只在拿到城市名时才更新，避免 GPS 定位覆盖网络定位的城市
                if (!loc.city.isNullOrBlank()) city = loc.city
                // 移动地图视角到当前位置
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                Log.d("MapScreen", "定位成功: ${loc.latitude},${loc.longitude} city=${loc.city}")
            }
        }
    }
    DisposableEffect(Unit) {
        // MapView 必须调用 onCreate 才能显示地图
        mapView.onCreate(null)
        // 显示定位蓝点（跟随模式，箭头朝向行进方向）
        val myLocationStyle = MyLocationStyle()
        myLocationStyle.showMyLocation(true)
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
        myLocationStyle.interval(2000)
        aMap.myLocationStyle = myLocationStyle
        aMap.isMyLocationEnabled = true
        // 隐藏 SDK 自带定位按钮（默认在右上角），改用自定义按钮放在右下角
        aMap.uiSettings.isMyLocationButtonEnabled = false
        // 隐藏缩放按钮，改用双指缩放手势
        aMap.uiSettings.isZoomControlsEnabled = false

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
                if (!loc.city.isNullOrBlank()) city = loc.city
                // 移动地图视角到当前位置
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                Log.d("MapScreen", "定位成功: ${loc.latitude},${loc.longitude} city=${loc.city}")
            }
        }
        onDispose { }
    }

    // 从后端加载点位并标记到地图上（UserId 变化时重新加载）
    val currentUserId = UserIdManager.getUserId()
    // 更新提示弹窗
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }

    LaunchedEffect(currentUserId) {
        if (currentUserId.isNullOrBlank()) return@LaunchedEffect
        // 只清除起终点标记，不清 aMap.clear() 以免清掉定位箭头
        routeMarkers.value.forEach { it.remove() }
        routeMarkers.value = emptyList()
        // 如果还没定位成功，重新触发定位
        if (startPt == null) {
            startLocation(context) { loc ->
                val latLng = LatLng(loc.latitude, loc.longitude)
                startPt = latLng
                if (!loc.city.isNullOrBlank()) city = loc.city
                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                Log.d("MapScreen", "重新定位成功: ${loc.latitude},${loc.longitude} city=${loc.city}")
            }
        }
        val points = withContext(Dispatchers.IO) { ApiClient.fetchPoints() }
        if (points == null) {
            // 版本过低，需要更新
            ApiClient.updateInfo?.let {
                updateMessage = it.message
                showUpdateDialog = true
            }
            return@LaunchedEffect
        }
        bikePoints = points
        if (points.isNotEmpty()) {
            for (p in points) {
                aMap.addMarker(
                    MarkerOptions()
                        .position(p.latLng)
                        .title(p.name)
                        .icon(makePointBitmap(context))
                )
            }
            Log.d("MapScreen", "已标记 ${points.size} 个点位")
        } else {
            Log.w("MapScreen", "未获取到后端点位")
        }
    }

    // 版本更新提示弹窗
    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { /* 不允许关闭 */ },
            title = { Text("发现新版本") },
            text = { Text(updateMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showUpdateDialog = false
                }) { Text("知道了") }
            },
        )
    }

    /**
     * 清除起终点标记和路线，不影响后端点位标记
     */
    fun clearRouteMarkers() {
        routeMarkers.value.forEach { it.remove() }
        routeMarkers.value = emptyList()
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
                clearRouteMarkers()
                val markers = mutableListOf<Marker>()
                startPt?.let { markers.add(addMarker(aMap, it, true)) }
                endPt?.let { markers.add(addMarker(aMap, it, false)) }
                routeMarkers.value = markers
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
                // 显示当前定位状态和用户ID
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (startPt != null) "当前定位：${city.ifBlank { "已定位" }}" else "正在定位中...",
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        UserIdManager.getUserId()?.let {
                            Text(
                                text = "用户：$it",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = { showUserIdDialog = true },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) { Text("修改") }
                    }
                }
                PlaceSearchField(
                    label = "终点",
                    city = city,
                    currentLocation = startPt,
                    onSelected = { sug ->
                        endPt = sug.pt
                        sug.pt?.let {
                            moveMap(aMap, it)
                            clearRouteMarkers()
                            val markers = mutableListOf<Marker>()
                            startPt?.let { s -> markers.add(addMarker(aMap, s, true)) }
                            markers.add(addMarker(aMap, it, false))
                            routeMarkers.value = markers
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
                // 自定义定位按钮（右下角，缩放按钮上方）
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp)
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.IconButton(
                        onClick = {
                            startPt?.let {
                                aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(it, 17f))
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "回到当前位置",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
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
                        clearRouteMarkers()
                        val markers = mutableListOf<Marker>()
                        markers.add(addMarker(aMap, s, true))
                        markers.add(addMarker(aMap, e, false))
                        routeMarkers.value = markers
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

                // 模拟导航按钮（隐藏，保留逻辑）
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
                            putExtra("emulator", true)
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(0.dp),
                ) { Text("模拟导航") }
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
        // 需要返回逆地理编码信息（城市、省、区、地址）
        option.isNeedAddress = true
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

private fun addMarker(aMap: AMap, pt: LatLng?, isStart: Boolean): Marker {
    val bd = makeCircleBitmap(isStart)
    return aMap.addMarker(
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
 * 后端点位的标记图标（蓝色小圆点）
 */
private fun makePointBitmap(context: android.content.Context): BitmapDescriptor {
    val original = android.graphics.BitmapFactory.decodeResource(context.resources, R.drawable.camera)
    val size = 60
    val scaled = android.graphics.Bitmap.createScaledBitmap(original!!, size, size, true)
    return BitmapDescriptorFactory.fromBitmap(scaled)
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
