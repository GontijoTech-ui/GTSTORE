package com.gtstore.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

private val GTStoreTypography = Typography()

@Composable
fun GTStoreTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        typography = GTStoreTypography,
        content = content
    )
}
