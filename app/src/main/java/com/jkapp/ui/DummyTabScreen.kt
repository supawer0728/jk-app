package com.jkapp.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

// 탭 재배열 기능을 테스트하기 위한 더미 탭(DM1~DM3)의 임시 화면.
@Composable
fun DummyTabScreen(@StringRes labelRes: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(stringResource(labelRes))
    }
}
