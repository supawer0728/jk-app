package com.jkapp.settings

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jkapp.R
import com.jkapp.common.MainTab
import com.jkapp.common.TabOrderViewModel
import com.jkapp.haptic.LocalHapticController

private val ROW_HORIZONTAL_PADDING = 16.dp
private val ROW_VERTICAL_PADDING = 8.dp
private val HANDLE_SPACING = 12.dp
private const val DRAGGING_SCALE = 1.03f
private const val DRAGGING_ELEVATION_DP = 8f
private const val RESTING_ELEVATION_DP = 0f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabOrderEditScreen(
    viewModel: TabOrderViewModel,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) {
        viewModel.beginEdit()
    }

    val editOrder by viewModel.editTabOrder.collectAsStateWithLifecycle()
    val currentOrder = editOrder ?: return

    var draggingTabName by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_order_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.cancelEdit()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = {
                    viewModel.cancelEdit()
                    onBack()
                }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = {
                    viewModel.applyEdit()
                    onBack()
                }) {
                    Text(stringResource(R.string.apply))
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            itemsIndexed(currentOrder, key = { _, tab -> tab.name }) { index, tab ->
                // 드래그 중인 아이템은 animateItem()을 적용하지 않는다. 손가락을 따라가는
                // graphicsLayer translation과 레이아웃 슬롯 애니메이션이 겹쳐 흔들리듯 보인다.
                val itemModifier = if (draggingTabName == tab.name) Modifier else Modifier.animateItem()
                ReorderableTabRow(
                    tab = tab,
                    index = index,
                    itemCount = currentOrder.size,
                    isDragging = draggingTabName == tab.name,
                    onMove = viewModel::moveTab,
                    onDraggingChanged = { dragging -> draggingTabName = if (dragging) tab.name else null },
                    modifier = itemModifier,
                )
            }
        }
    }
}

@Composable
private fun ReorderableTabRow(
    tab: MainTab,
    index: Int,
    itemCount: Int,
    isDragging: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 드래그 코루틴은 아이템 이동으로 index가 바뀌어도 재시작되지 않으므로,
    // rememberUpdatedState로 항상 최신 index/itemCount/onMove를 읽는다.
    val currentIndex by rememberUpdatedState(index)
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentOnMove by rememberUpdatedState(onMove)
    val haptic = LocalHapticController.current
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun endDrag() {
        offsetY = 0f
        onDraggingChanged(false)
    }

    Surface(
        tonalElevation = if (isDragging) DRAGGING_ELEVATION_DP.dp else RESTING_ELEVATION_DP.dp,
        shadowElevation = if (isDragging) DRAGGING_ELEVATION_DP.dp else RESTING_ELEVATION_DP.dp,
        color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationY = offsetY
                if (isDragging) {
                    scaleX = DRAGGING_SCALE
                    scaleY = DRAGGING_SCALE
                }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        onDraggingChanged(true)
                        haptic?.tick()
                    },
                    onDragEnd = { endDrag() },
                    onDragCancel = { endDrag() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetY += dragAmount.y
                        val itemHeightPx = size.height.toFloat()
                        if (itemHeightPx <= 0f) return@detectDragGestures
                        val targetIndex = computeTargetIndex(currentIndex, offsetY, itemHeightPx, currentItemCount)
                        if (targetIndex != currentIndex) {
                            val shift = targetIndex - currentIndex
                            currentOnMove(currentIndex, targetIndex)
                            offsetY = consumeOffsetAfterMove(offsetY, shift, itemHeightPx)
                            haptic?.tick()
                        }
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ROW_HORIZONTAL_PADDING, vertical = ROW_VERTICAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(tab.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(HANDLE_SPACING))
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = stringResource(R.string.tab_order_drag_handle),
            )
        }
    }
}
