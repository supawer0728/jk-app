package com.jkapp.nav

import kotlinx.serialization.Serializable

@Serializable
data object SplashRoute

@Serializable
data object LoginRoute

@Serializable
data object HomeRoute

@Serializable
data class DiaryDetailRoute(val date: String)

@Serializable
data class DiaryFormRoute(val firestoreId: String? = null)

@Serializable
data object RecordTypeManagementRoute

// parentId가 있으면 자식(SUB) 추가 모드로 폼을 연다(제목·담당자·메모만 입력). 이슈 #88.
@Serializable
data class TodoFormRoute(val firestoreId: String? = null, val parentId: String? = null)

@Serializable
data object SettingsRoute

@Serializable
data object PortfolioRoute
