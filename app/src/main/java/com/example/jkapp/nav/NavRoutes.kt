package com.example.jkapp.nav

import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object HomeRoute

@Serializable
data class DiaryDetailRoute(val date: String)

@Serializable
data class DiaryFormRoute(val date: String? = null)
