package com.jkapp.common

sealed interface BottomTabItem {
    data class Content(val tab: MainTab) : BottomTabItem
    sealed interface Action : BottomTabItem {
        data object Logout : Action
        data object Settings : Action
    }
}
