package com.jkapp.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 다중선택 삭제 상태 홀더.
 *
 * 투자종목·벤치마크·TODO 세 화면이 공유하는 선택 모드 상태를 관리한다.
 * - [isSelectionMode]: 선택 모드 활성 여부.
 * - [selectedIds]: 현재 선택된 항목 ID Set.
 * - [toggle]: 항목 ID의 선택 여부를 토글한다.
 * - [exit]: 선택 모드를 종료하고 선택을 초기화한다.
 *
 * @param T 항목 ID 타입 (String, Pair<String,T> 등 화면별로 다름).
 *
 * '전체 삭제'는 각 화면 고유 동작이므로 이 홀더에 포함하지 않는다.
 */
class MultiDeleteState<T> {
    var isSelectionMode by mutableStateOf(false)
        private set
    var selectedIds by mutableStateOf<Set<T>>(emptySet())
        private set

    fun enter() {
        isSelectionMode = true
        selectedIds = emptySet()
    }

    fun toggle(id: T) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }

    fun exit() {
        isSelectionMode = false
        selectedIds = emptySet()
    }
}

/**
 * 다중선택 삭제 모드의 하단 바.
 *
 * "선택 삭제"·"취소" 버튼만 포함한다. '전체 삭제'는 각 화면에서 추가한다.
 *
 * @param selectedCount 현재 선택된 항목 수.
 * @param onDeleteSelected 선택 삭제 버튼 클릭 핸들러.
 * @param onCancel 취소 버튼 클릭 핸들러.
 * @param modifier 외부에서 전달받는 Modifier.
 */
@Composable
fun MultiDeleteBar(
    selectedCount: Int,
    onDeleteSelected: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onDeleteSelected,
            enabled = selectedCount > 0,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.weight(1f),
        ) {
            Text(if (selectedCount > 0) "선택 삭제 ($selectedCount)" else "선택 삭제")
        }
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.weight(1f),
        ) {
            Text("취소")
        }
    }
}
