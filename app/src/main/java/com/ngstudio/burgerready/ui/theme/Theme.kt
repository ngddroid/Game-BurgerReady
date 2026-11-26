package com.ngstudio.burgerready.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ngstudio.burgerready.R

object BV {
    val Mustard = Color(0xFFFFC23C)
    val MustardDark = Color(0xFFE09A12)
    val Tomato = Color(0xFFF2542D)
    val TomatoDark = Color(0xFFC23A1A)
    val Mint = Color(0xFF3DDC97)
    val MintDark = Color(0xFF1FAA6E)
    val Teal = Color(0xFF1FA6B8)
    val TealDark = Color(0xFF137A88)
    val Cream = Color(0xFFFFF3DC)
    val CreamDark = Color(0xFFF2DDB6)
    val Cocoa = Color(0xFF3B2A3A)
    val CocoaSoft = Color(0xFF6B5260)
    val Pink = Color(0xFFFF8FB1)
    val Lilac = Color(0xFFB79CFF)
    val Sky = Color(0xFF8FD8FF)
    val Gold = Color(0xFFFFCF3F)
    val Bun = Color(0xFFE9A04B)
    val White = Color(0xFFFFFFFF)
    val Shadow = Color(0x553B2A3A)
    val Gem = Color(0xFF2FD3C9)
    val Night = Color(0xFF2C3E66)
}

val Lilita = FontFamily(Font(R.font.lilita_one))
val Nunito = FontFamily(
    Font(R.font.nunito, FontWeight.Normal),
    Font(R.font.nunito, FontWeight.Bold),
    Font(R.font.nunito, FontWeight.ExtraBold),
)

object Type {
    val Display = TextStyle(fontFamily = Lilita, fontSize = 40.sp, color = BV.White)
    val Title = TextStyle(fontFamily = Lilita, fontSize = 28.sp, color = BV.Cocoa)
    val Heading = TextStyle(fontFamily = Lilita, fontSize = 22.sp, color = BV.Cocoa)
    val Button = TextStyle(fontFamily = Lilita, fontSize = 20.sp, color = BV.White)
    val Body = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = BV.Cocoa)
    val Small = TextStyle(fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BV.CocoaSoft)
    val Number = TextStyle(fontFamily = Lilita, fontSize = 18.sp, color = BV.White)
}

@Composable
fun BunVoyageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = BV.Tomato, secondary = BV.Teal, background = BV.Cream, surface = BV.Cream, onPrimary = BV.White),
        content = content,
    )
}
