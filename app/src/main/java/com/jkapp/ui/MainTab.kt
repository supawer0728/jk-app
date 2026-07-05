package com.jkapp.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import com.jkapp.R

// 하단바에 노출되는 탭 개수이자 스와이프 패널 그리드의 열 수. 하단바의 첫 줄과 패널의 첫 줄이
// 시각적으로 이어져야 하므로 두 값은 항상 같아야 해서 하나의 상수로 공유한다.
const val MAIN_TAB_ROW_SIZE = 5

enum class MainTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Default.Home),
    ASSET(R.string.tab_asset, Icons.Default.AccountBalance),
    DIARY(R.string.tab_diary, Icons.AutoMirrored.Filled.MenuBook),
    TODO(R.string.tab_todo, Icons.Default.CheckCircle),
    CALENDAR(R.string.tab_calendar, Icons.Default.CalendarMonth),
}
