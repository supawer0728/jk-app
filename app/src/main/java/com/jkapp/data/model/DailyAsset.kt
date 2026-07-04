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
)
