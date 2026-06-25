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
import com.amap.api.services.help.Inputtips
import com.amap.api.services.help.InputtipsQuery
import com.amap.api.services.help.Tip

/**
 * 地点搜索输入框组件，输入时自动调用高德 Inputtips 显示下拉建议。
 *
 * @param label 输入框标签（如 "起点" / "终点"）
 * @param city  限定搜索城市
 * @param onSelected  选中某个建议后回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceSearchField(
    label: String,
    city: String,
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
                if (input.isNotBlank()) {
                    // 高德 Inputtips 搜索
                    val query = InputtipsQuery(input, city)
                    val tips = Inputtips(context, query)
                    tips.setInputtipsListener { list, code ->
                        Log.d("PlaceSearchField", "Inputtips callback code=$code size=${list?.size}")
                        val filtered = list
                            ?.filter { it.point != null }
                            ?.map { PlaceSuggestion.from(it) }
                            .orEmpty()
                        suggestions = filtered
                        expanded = filtered.isNotEmpty()
                    }
                    tips.requestInputtipsAsyn()
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
