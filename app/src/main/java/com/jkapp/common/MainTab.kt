package com.jkapp.common

import androidx.annotation.StringRes
import com.jkapp.R

enum class MainTab(@StringRes val labelRes: Int) {
    HOME(R.string.tab_home),
    ASSET(R.string.tab_asset),
    DIARY(R.string.tab_diary),
    TODO(R.string.tab_todo),
    CALENDAR(R.string.tab_calendar),
}
