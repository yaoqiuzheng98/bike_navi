package com.example.bikenavi

import com.amap.api.maps.model.LatLng

/**
 * 后端 /point 接口返回的地点数据
 */
data class BikePoint(
    val id: Long,
    val latitude: Double,
    val longitude: Double,
    val name: String,
) {
    val latLng: LatLng get() = LatLng(latitude, longitude)
}
