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

enum class MainTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    HOME(R.string.tab_home, Icons.Default.Home),
    ASSET(R.string.tab_asset, Icons.Default.AccountBalance),
    DIARY(R.string.tab_diary, Icons.AutoMirrored.Filled.MenuBook),
    TODO(R.string.tab_todo, Icons.Default.CheckCircle),
    CALENDAR(R.string.tab_calendar, Icons.Default.CalendarMonth),
}
