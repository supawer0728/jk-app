package com.jkapp.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.jkapp.R
import kotlin.math.roundToInt

private val TAB_ITEM_SIZE = 64.dp
private const val TAB_GRID_COLUMNS = 5

@Composable
fun TabOrderPanel(
    tabs: List<MainTab>,
    isEditMode: Boolean,
    onToggleEditMode: () -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onSelectTab: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingTabName by remember { mutableStateOf<String?>(null) }
    val rows = (tabs.size + TAB_GRID_COLUMNS - 1) / TAB_GRID_COLUMNS

    Column(
        // 편집 모드일 때 화면(패널) 어디를 탭해도 위치 수정을 확정하고 편집 모드를 종료한다.
        modifier = modifier.pointerInput(isEditMode) {
            if (!isEditMode) return@pointerInput
            detectTapGestures(onTap = { onToggleEditMode() })
        }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(TAB_GRID_COLUMNS),
            modifier = Modifier
                .fillMaxWidth()
                .height(TAB_ITEM_SIZE * rows),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            userScrollEnabled = false,
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.name }) { index, tab ->
                // 드래그 중인 아이템은 animateItem()을 적용하지 않는다. 적용하면 손가락을 따라가는
                // graphicsLayer translation과 레이아웃 슬롯 애니메이션이 겹쳐 흔들리듯 보인다.
                val itemModifier = if (draggingTabName == tab.name) Modifier else Modifier.animateItem()
                ReorderableTabIcon(
                    tab = tab,
                    index = index,
                    tabCount = tabs.size,
                    columns = TAB_GRID_COLUMNS,
                    isEditMode = isEditMode,
                    onMove = onMove,
                    onEnterEditMode = onToggleEditMode,
                    onDraggingChanged = { dragging -> draggingTabName = if (dragging) tab.name else null },
                    onClick = { onSelectTab(tab) },
                    modifier = itemModifier,
                )
            }
        }
        // 슬라이드업 시 하단 공백이 부족하다는 피드백에 따라 탭 행 높이의 절반을 여백으로 추가한다.
        Spacer(modifier = Modifier.height(TAB_ITEM_SIZE / 2))
    }
}

@Composable
private fun ReorderableTabIcon(
    tab: MainTab,
    index: Int,
    tabCount: Int,
    columns: Int,
    isEditMode: Boolean,
    onMove: (from: Int, to: Int) -> Unit,
    onEnterEditMode: () -> Unit,
    onDraggingChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 드래그 코루틴은 아이템 이동으로 index가 바뀌어도 재시작되지 않으므로,
    // rememberUpdatedState로 항상 최신 index/tabCount/onMove를 읽는다.
    val currentIndex by rememberUpdatedState(index)
    val currentTabCount by rememberUpdatedState(tabCount)
    val currentOnMove by rememberUpdatedState(onMove)
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "tabJiggle")
    val jiggleAngle by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            // tab.ordinal로 지연을 살짝 다르게 줘서 모든 아이콘이 완전히 같은 박자로 움직이지 않도록 한다.
            // 재배열 중 바뀌는 index 대신 안정적인 tab.ordinal을 키로 써서 드래그 중 애니메이션이 재시작되지 않는다.
            animation = tween(durationMillis = 160, delayMillis = (tab.ordinal % 3) * 50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tabJiggleAngle",
    )

    fun endDrag() {
        offsetX = 0f
        offsetY = 0f
        onDraggingChanged(false)
    }

    val baseModifier = modifier
        .size(TAB_ITEM_SIZE)
        .graphicsLayer {
            translationX = offsetX
            translationY = offsetY
            // 편집 모드임을 알 수 있도록 드래그 중인 아이템뿐 아니라 모든 아이템이 흔들린다.
            rotationZ = if (isEditMode) jiggleAngle else 0f
        }

    val interactiveModifier = if (isEditMode) {
        baseModifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { onDraggingChanged(true) },
                onDragEnd = { endDrag() },
                onDragCancel = { endDrag() },
                onDrag = { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y
                    val itemWidthPx = size.width.toFloat()
                    val itemHeightPx = size.height.toFloat()
                    if (itemWidthPx <= 0f || itemHeightPx <= 0f) return@detectDragGestures
                    val colShift = (offsetX / itemWidthPx).roundToInt()
                    val rowShift = (offsetY / itemHeightPx).roundToInt()
                    if (colShift != 0 || rowShift != 0) {
                        val newIndex = (currentIndex + rowShift * columns + colShift)
                            .coerceIn(0, currentTabCount - 1)
                        if (newIndex != currentIndex) {
                            currentOnMove(currentIndex, newIndex)
                            // deltaIndex가 음수일 때 %, /는 0을 향해 잘려서 대각선 드래그에서 보정이
                            // 어긋난다(예: -3 % 4 == -3). floor 기반 mod/div로 정확히 소비한 행/열만큼만 보정한다.
                            val deltaIndex = newIndex - currentIndex
                            offsetX -= deltaIndex.mod(columns) * itemWidthPx
                            offsetY -= deltaIndex.floorDiv(columns) * itemHeightPx
                        }
                    }
                    // 격자 경계에서 이동이 막혀도 손가락을 계속 끌면 오프셋이 무한히 쌓이지 않도록 제한한다.
                    offsetX = offsetX.coerceIn(-itemWidthPx * 1.5f, itemWidthPx * 1.5f)
                    offsetY = offsetY.coerceIn(-itemHeightPx * 1.5f, itemHeightPx * 1.5f)
                },
            )
        }
    } else {
        // 짧게 탭하면 해당 탭으로 이동하고, 길게 누르면 편집(재배열) 모드로 진입한다.
        baseModifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { onClick() },
                onLongPress = { onEnterEditMode() },
            )
        }
    }

    Column(
        modifier = interactiveModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = tab.icon, contentDescription = stringResource(tab.labelRes))
        Text(text = stringResource(tab.labelRes), style = MaterialTheme.typography.labelSmall)
    }
}
