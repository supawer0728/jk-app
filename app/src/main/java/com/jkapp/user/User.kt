package com.jkapp.user

data class User(
    val email: String,
    val displayName: String,
    val pushToken: PushToken? = null,
    val lastLoginAt: Long? = null,
    val preference: UserPreference = UserPreference(),
)
