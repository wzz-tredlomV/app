
package com.example.game2048.ui



import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Surface

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp



@Composable

fun Tile(value: Int) {

    val bg = tileColor(value)

    Surface(

        color = bg,

        shape = RoundedCornerShape(8.dp),

        shadowElevation = if (value == 0) 0.dp else 4.dp,

        modifier = Modifier

            .size(64.dp)

    ) {

        Box(contentAlignment = Alignment.Center) {

            if (value != 0) {

                Text(

                    text = value.toString(),

                    color = if (value <= 4) Color(0xFF776E65) else Color.White,

                    fontSize = (if (value < 100) 20 else if (value < 1000) 18 else 14).sp

                )

            }

        }

    }

}



private fun tileColor(value: Int): Color {

    return when (value) {

        0 -> Color(0xFFCDC1B4)

        2 -> Color(0xFFEEE4DA)

        4 -> Color(0xFFEDE0C8)

        8 -> Color(0xFFF2B179)

        16 -> Color(0xFFF59563)

        32 -> Color(0xFFF67C5F)

        64 -> Color(0xFFF65E3B)

        128 -> Color(0xFFEDCF72)

        256 -> Color(0xFFEDCC61)

        512 -> Color(0xFFEDC850)

        1024 -> Color(0xFFEDC53F)

        2048 -> Color(0xFFEDC22E)

        else -> Color(0xFF3C3A32)

    }

}

