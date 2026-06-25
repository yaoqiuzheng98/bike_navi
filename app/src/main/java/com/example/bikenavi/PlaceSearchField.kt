package com.example.bikenavi

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption

/**
 * 地点搜索输入框组件，输入时自动调用百度 Sug 检索显示下拉建议。
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

    // Sug 检索实例 —— 每个 PlaceSearchField 持有独立实例，避免回调互相覆盖
    val sugSearch = remember { SuggestionSearch.newInstance() }
    val listener = remember {
        object : OnGetSuggestionResultListener {
            override fun onGetSuggestionResult(result: SuggestionResult?) {
                val list = result?.allSuggestions
                    ?.filter { it.key != null && it.pt != null }
                    ?.map { PlaceSuggestion.from(it) }
                    .orEmpty()
                suggestions = list
                expanded = list.isNotEmpty()
            }
        }
    }

    LaunchedEffect(Unit) {
        sugSearch.setOnGetSuggestionResultListener(listener)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                if (input.isNotBlank()) {
                    sugSearch.requestSuggestion(
                        SuggestionSearchOption().keyword(input).city(city)
                    )
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
