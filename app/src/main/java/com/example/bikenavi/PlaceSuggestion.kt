package com.example.bikenavi

import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.sug.SuggestionResult

/**
 * 地点搜索建议的数据模型
 */
data class PlaceSuggestion(
    val name: String,
    val district: String,
    val city: String,
    val pt: LatLng?,
) {
    val displayName: String get() = if (district.isNotBlank()) "$name（$district）" else name

    companion object {
        fun from(info: SuggestionResult.SuggestionInfo): PlaceSuggestion = PlaceSuggestion(
            name = info.key ?: "",
            district = info.district ?: "",
            city = info.city ?: "",
            pt = info.pt,
        )
    }
}
