package com.example.bikenavi

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import com.amap.api.maps.model.LatLng
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.core.PoiItem
import com.amap.api.services.poisearch.PoiResult
import com.amap.api.services.poisearch.PoiSearch

/**
 * 地点搜索输入框组件，使用高德 PoiSearch 搜索，结果按距离当前位置从近到远排序。
 *
 * @param label 输入框标签
 * @param city  限定搜索城市
 * @param currentLocation 当前定位位置，用于按距离排序
 * @param onSelected  选中某个建议后回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchField(
    label: String,
    city: String,
    currentLocation: LatLng?,
    onSelected: (PlaceSuggestion) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<PlaceSuggestion>>(emptyList()) }
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                if (input.isNotBlank() && currentLocation != null) {
                    // 用 PoiSearch 关键字搜索，设置 location 让结果按距离排序
                    val query = PoiSearch.Query(input, "", city)
                    query.pageSize = 20
                    query.pageNum = 0
                    query.location = LatLonPoint(currentLocation.latitude, currentLocation.longitude)
                    val poiSearch = PoiSearch(context, query)
                    poiSearch.setOnPoiSearchListener(object : PoiSearch.OnPoiSearchListener {
                        override fun onPoiSearched(result: PoiResult?, code: Int) {
                            if (code != 1000 || result == null || result.pois.isNullOrEmpty()) {
                                Log.d("PlaceSearchField", "PoiSearch code=$code size=0")
                                suggestions = emptyList()
                                expanded = false
                                return
                            }
                            Log.d("PlaceSearchField", "PoiSearch code=$code size=${result.pois.size}")
                            val filtered = result.pois
                                .filter { it.latLonPoint != null }
                                .map { PlaceSuggestion.from(it) }
                            suggestions = filtered
                            expanded = filtered.isNotEmpty()
                        }

                        override fun onPoiItemSearched(item: PoiItem?, code: Int) {}
                    })
                    poiSearch.searchPOIAsyn()
                } else {
                    suggestions = emptyList()
                    expanded = false
                }
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            suggestions.forEach { sug ->
                DropdownMenuItem(
                    text = { Text(sug.displayName) },
                    onClick = {
                        text = sug.name
                        expanded = false
                        onSelected(sug)
                    },
                )
            }
        }
    }
}
