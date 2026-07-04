package com.jkapp.data.model

import java.math.BigDecimal

data class DailyAsset(
    val firestoreId: String? = null,
    val date: String,
    val assets: List<AssetItem> = emptyList(),
)

data class AssetItem(
    val name: String,
    val owner: String,
    val institution: String? = null,
    val accountNumber: String? = null,
    val card: String? = null,
    val amount: BigDecimal? = null,
    val hidden: Boolean = false,
)

// 순자산 계산에서 기본적으로 제외되는 자산 이름(공용 계좌, 부동산, 보증금 등 개인 순자산과 무관한 항목).
val DEFAULT_HIDDEN_ASSET_NAMES: Set<String> = setOf(
    "공용 계좌",
    "부동산 계좌",
    "전세보증금",
    "전세보증금(K가족)",
    "K급여(계좌이체)",
    "K 용돈",
    "J대출이자",
    "J 용돈",
)
