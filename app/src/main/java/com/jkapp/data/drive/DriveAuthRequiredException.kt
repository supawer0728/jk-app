package com.jkapp.data.drive

import android.content.Intent

class DriveAuthRequiredException(val recoveryIntent: Intent) : Exception("Drive 접근 권한 동의가 필요합니다.")
