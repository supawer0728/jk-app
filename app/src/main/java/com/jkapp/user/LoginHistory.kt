package com.jkapp.user

data class LoginDevice(
    val os: String = "Android",
    val osVersion: String,
    val deviceModel: String,
    val appVersion: String,
)

data class LoginHistory(
    val uid: String,
    val loginAt: Long,
    val device: LoginDevice,
)
