
package com.example.game2048.ui



import androidx.compose.material3.*

import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.Color



private val LightColors = lightColorScheme(

    primary = Color(0xFF776e65),

    onPrimary = Color.White,

    secondary = Color(0xFF8f7a66),

    background = Color(0xFFFAF8F1),

    surface = Color(0xFFFFFFFF),

    error = Color(0xFFB00020),

)



@Composable

fun My2048Theme(content: @Composable () -> Unit) {

    MaterialTheme(

        colorScheme = LightColors,

        typography = Typography(),

        content = content

    )

}

