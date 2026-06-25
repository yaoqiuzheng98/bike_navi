package com.example.bikenavi

import com.amap.api.maps.model.LatLng
import com.amap.api.services.help.Tip

/**
 * 地点搜索建议的数据模型
 */
data class PlaceSuggestion(
    val name: String,
    val district: String,
    val pt: LatLng?,
) {
    val displayName: String get() = if (district.isNotBlank()) "$name（$district）" else name

    companion object {
        fun from(tip: Tip): PlaceSuggestion = PlaceSuggestion(
            name = tip.name ?: "",
            district = tip.district ?: "",
            pt = tip.point?.let { LatLng(it.latitude, it.longitude) },
        )
    }
}
