package com.albasel.tawsilaapp.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    modifier: Modifier = Modifier,
    items: List<String>,
    initialIndex: Int = 0,
    onIndexSelected: (Int) -> Unit,
    itemHeight: Dp = 23.dp,
    visibleItemsCount: Int = 3,
    activeColor: Color = Color(0xFFC5A059),
    inactiveColor: Color = Color.Gray,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    val lazyListState = rememberLazyListState(initialIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState)

    LaunchedEffect(initialIndex) {
        if (lazyListState.firstVisibleItemIndex != initialIndex) {
            lazyListState.scrollToItem(initialIndex)
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index ->
                if (index < items.size) {
                    onIndexSelected(index)
                }
            }
    }

    Box(
        modifier = modifier.height(itemHeight * visibleItemsCount),
        contentAlignment = Alignment.Center
    ) {
        // Selection Highlight (Center Lines)
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(activeColor.copy(alpha = 0.2f)))
            Spacer(Modifier.height(itemHeight))
            Box(Modifier.fillMaxWidth().height(1.dp).background(activeColor.copy(alpha = 0.2f)))
        }

        LazyColumn(
            state = lazyListState,
            flingBehavior = snapFlingBehavior,
            contentPadding = PaddingValues(vertical = itemHeight * (visibleItemsCount / 2)),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(items.size) { index ->
                val isSelected = lazyListState.firstVisibleItemIndex == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = items[index],
                        fontSize = if (isSelected) (fontSize.value + 2).sp else fontSize,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                        color = if (isSelected) activeColor else inactiveColor.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
