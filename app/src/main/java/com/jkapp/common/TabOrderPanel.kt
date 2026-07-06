package com.jkapp.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.jkapp.R
import com.jkapp.haptic.LocalHapticController
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val TAB_ITEM_SIZE = 64.dp

// 드래그 오프셋으로 계산된 행/열 이동량을 실제 그리드 내 새 인덱스로 변환한다.
// 격자 경계를 벗어나면 가장 가까운 유효 인덱스(0 또는 마지막 인덱스)로 고정된다. 마지막 줄이
// 꽉 차지 않은 경우(예: tabCount가 columns의 배수가 아님) 존재하지 않는 칸을 가리키는 이동은
// 마지막 인덱스로 스냅되며, 이는 의도된 동작이다.
internal fun computeNewIndex(currentIndex: Int, rowShift: Int, colShift: Int, columns: Int, tabCount: Int): Int =
    (currentIndex + rowShift * columns + colShift).coerceIn(0, tabCount - 1)

// 인덱스 이동이 일어난 뒤, 소비된 행/열 이동량만큼 누적 드래그 오프셋을 되돌린다.
// 여기 전달되는 rowShift/colShift는 반드시 computeActualShift로 구한 "실제 소비된" 이동량이어야
// 한다. 드래그가 요청한 rowShift/colShift를 그대로 쓰면, computeNewIndex의 coerceIn 클램핑으로
// 실제 이동량이 요청과 달라지는 경우(예: 마지막 줄이 꽉 차지 않아 열이 크게 바뀌는 경우) 오프셋이
// 잘못 보정된다.
internal fun computeOffsetAfterMove(
    offsetX: Float,
    offsetY: Float,
    rowShift: Int,
    colShift: Int,
    itemWidthPx: Float,
    itemHeightPx: Float,
): Pair<Float, Float> = (offsetX - colShift * itemWidthPx) to (offsetY - rowShift * itemHeightPx)

// computeNewIndex가 반환한 실제 newIndex를 기준으로 실제 소비된 행/열 이동량을 계산한다.
// currentIndex/newIndex는 항상 0 이상이므로 정수 나눗셈(/, %)만으로 안전하게 행/열을 구할 수 있고,
// 두 인덱스 각각의 행/열을 구한 뒤 차이를 내는 방식이라 deltaIndex를 직접 floorDiv/floorMod하는
// 것과 달리 colShift가 음수인 경우에도 부호가 뒤집히지 않는다.
internal fun computeActualShift(currentIndex: Int, newIndex: Int, columns: Int): Pair<Int, Int> {
    val rowShift = newIndex / columns - currentIndex / columns
    val colShift = newIndex % columns - currentIndex % columns
    return rowShift to colShift
}

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
    val rows = (tabs.size + MAIN_TAB_ROW_SIZE - 1) / MAIN_TAB_ROW_SIZE
    val haptic = LocalHapticController.current

    // 편집 모드에 머무르는 동안 재배열 가능 상태임을 계속 알리기 위해 1초마다 짧은 햅틱을 울린다.
    // isEditMode가 false가 되면 LaunchedEffect가 취소되어 자동으로 멈춘다.
    LaunchedEffect(isEditMode) {
        if (!isEditMode) return@LaunchedEffect
        while (true) {
            delay(1_000)
            haptic?.tick()
        }
    }

    Column(
        modifier = modifier
            // 3버튼 내비게이션바처럼 시스템 내비게이션 바가 차지하는 영역과 아이콘이 겹치지 않도록
            // 패널 하단에 시스템 내비게이션 바 높이만큼 여백을 둔다. 축소된 NavigationBar는 자체적으로
            // 이 처리를 하지만, 이 패널은 커스텀 레이아웃이라 직접 적용해야 한다.
            .windowInsetsPadding(WindowInsets.navigationBars)
            // 편집 모드일 때 화면(패널) 어디를 탭해도 위치 수정을 확정하고 편집 모드를 종료한다.
            .pointerInput(isEditMode) {
                if (!isEditMode) return@pointerInput
                detectTapGestures(onTap = { onToggleEditMode() })
            }
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(MAIN_TAB_ROW_SIZE),
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
                    columns = MAIN_TAB_ROW_SIZE,
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
    val haptic = LocalHapticController.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // 편집(재배열) 모드 진입 애니메이션: 아이콘이 "정상 각도(0도) -> 좌측으로 기울임(-6도) ->
    // 정상 각도 -> 우측으로 기울임(+6도)"를 -6f~6f 사이에서 왕복(RepeatMode.Reverse)하며
    // 무한 반복(infiniteRepeatable)해 좌우로 까딱거리는 "흔들흔들"한 느낌을 만든다.
    // 이 반복은 isEditMode가 true인 동안 멈추지 않고 계속되며(아래 rotationZ 조건에서
    // isEditMode를 그대로 참조), 사용자가 화면을 탭해 편집 모드를 종료(isEditMode=false)하는
    // 순간에만 회전이 0도로 되돌아가 흔들림이 멈춘다. 애니메이션 값(jiggleAngle) 자체는
    // isEditMode와 무관하게 항상 갱신되고 있고, rotationZ에 적용할지 여부만 isEditMode로 판단한다.
    val infiniteTransition = rememberInfiniteTransition(label = "tabJiggle")
    val jiggleAngle by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 160, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            // tab.ordinal로 최초 시작 시점만 살짝 어긋나게 해 모든 아이콘이 완전히 같은 박자로
            // 움직이지 않도록 한다. tween(delayMillis)를 쓰면 반복마다 지연이 재적용돼 흔들림이
            // 주기적으로 멈춘 것처럼 보이므로, 최초 1회만 지연시키는 initialStartOffset을 사용한다.
            // 재배열 중 바뀌는 index 대신 안정적인 tab.ordinal을 키로 써서 드래그 중 애니메이션이 재시작되지 않는다.
            initialStartOffset = StartOffset((tab.ordinal % 3) * 50),
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
                        val newIndex = computeNewIndex(currentIndex, rowShift, colShift, columns, currentTabCount)
                        if (newIndex != currentIndex) {
                            haptic?.tick()
                            currentOnMove(currentIndex, newIndex)
                            val (actualRowShift, actualColShift) = computeActualShift(currentIndex, newIndex, columns)
                            val (adjustedX, adjustedY) = computeOffsetAfterMove(
                                offsetX, offsetY, actualRowShift, actualColShift, itemWidthPx, itemHeightPx,
                            )
                            offsetX = adjustedX
                            offsetY = adjustedY
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
