package com.jkapp.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.jkapp.R

enum class MainTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Default.Home),
    ASSET(R.string.tab_asset, Icons.Default.AccountBalance),
    DIARY(R.string.tab_diary, Icons.AutoMirrored.Filled.MenuBook),
    TODO(R.string.tab_todo, Icons.Default.CheckCircle),
    CALENDAR(R.string.tab_calendar, Icons.Default.CalendarMonth),
    // 탭 재배열 기능을 테스트하기 위한 더미 탭 (5개를 넘는 탭 목록에서의 그리드/줄바꿈 동작 확인용).
    DM1(R.string.tab_dm1, Icons.Default.Star),
    DM2(R.string.tab_dm2, Icons.Default.Favorite),
    DM3(R.string.tab_dm3, Icons.Default.ThumbUp),
}
