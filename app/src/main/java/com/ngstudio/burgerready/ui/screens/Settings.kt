package com.ngstudio.burgerready.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ngstudio.burgerready.App
import com.ngstudio.burgerready.BuildConfig
import com.ngstudio.burgerready.data.Achievements
import com.ngstudio.burgerready.data.Cards
import com.ngstudio.burgerready.ui.kit.ArtImage
import com.ngstudio.burgerready.ui.kit.ConfettiBurst
import com.ngstudio.burgerready.ui.kit.GIcon
import com.ngstudio.burgerready.ui.kit.GameButton
import com.ngstudio.burgerready.ui.kit.GameDialog
import com.ngstudio.burgerready.ui.kit.GameIcon
import com.ngstudio.burgerready.ui.kit.OutlinedText
import com.ngstudio.burgerready.ui.kit.Panel
import com.ngstudio.burgerready.ui.kit.ProgressBar3D
import com.ngstudio.burgerready.ui.kit.Ribbon
import com.ngstudio.burgerready.ui.kit.RoundButton
import com.ngstudio.burgerready.ui.kit.formatNum
import com.ngstudio.burgerready.ui.theme.BV
import com.ngstudio.burgerready.ui.theme.Type

@Composable
fun ScreenFrame(title: String, onBack: () -> Unit, color: Color = BV.Teal, dark: Color = BV.TealDark, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFD9A0), BV.Cream)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundButton(color = BV.Tomato, size = 46.dp, sound = "ui_back", onClick = onBack) { OutlinedText("‹", size = 30.sp, stroke = 1.5.dp) }
                Spacer(Modifier.width(8.dp))
                Ribbon(title, Modifier.weight(1f), color = color, dark = dark, size = if (title.length > 12) 16.sp else 22.sp)
                Spacer(Modifier.width(54.dp))
            }
            Box(Modifier.weight(1f)) { content() }
        }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, onLegal: (String) -> Unit, onReplayTutorial: () -> Unit) {
    val d = App.repo.state.value
    val context = LocalContext.current
    var confirmReset by remember { mutableIntStateOf(0) }
    ScreenFrame("SETTINGS", onBack) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Audio & Feel", style = Type.Heading)
                    Spacer(Modifier.height(8.dp))
                    SettingToggle(GIcon.MUSIC, "Music", d.music) { App.repo.update { it.copy(music = !it.music) }; App.applySettings(); if (App.repo.data.music) App.audio.playMusic("menu_theme") }
                    VolumeSlider(d.musicVolume) { v -> App.repo.update { it.copy(musicVolume = v) }; App.applySettings() }
                    SettingToggle(GIcon.SOUND, "Sound Effects", d.sfx) { App.repo.update { it.copy(sfx = !it.sfx) }; App.applySettings() }
                    VolumeSlider(d.sfxVolume) { v -> App.repo.update { it.copy(sfxVolume = v) }; App.applySettings() }
                    SettingToggle(GIcon.VIBRATE, "Haptics", d.haptics) { App.repo.update { it.copy(haptics = !it.haptics) }; App.applySettings(); App.haptic(2) }
                }
            }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Graphics", style = Type.Heading)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("auto" to "Auto", "low" to "Low", "medium" to "Med", "high" to "High").forEach { (k, label) ->
                            GameButton(label, Modifier.weight(1f), color = if (d.quality == k) BV.Mint else BV.Cream, dark = if (d.quality == k) BV.MintDark else BV.CreamDark,
                                height = 42.dp, textSize = 15.sp) { App.repo.update { it.copy(quality = k) } }
                        }
                    }
                    Text("Lower quality saves battery on older phones.", style = Type.Small, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Game", style = Type.Heading)
                    Spacer(Modifier.height(8.dp))
                    GameButton("HOW TO PLAY", Modifier.fillMaxWidth(), color = BV.Teal, dark = BV.TealDark, height = 48.dp, textSize = 18.sp) { onReplayTutorial() }
                    Spacer(Modifier.height(8.dp))
                    GameButton("SHARE THE GAME", Modifier.fillMaxWidth(), color = BV.Mustard, dark = BV.MustardDark, height = 48.dp, textSize = 18.sp) {
                        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "I'm building a burger empire in Bun Voyage! 🍔 https://play.google.com/store/apps/details?id=${context.packageName}") }
                        context.startActivity(Intent.createChooser(i, "Share"))
                    }
                    Spacer(Modifier.height(8.dp))
                    GameButton("RATE US", Modifier.fillMaxWidth(), color = BV.Pink, dark = Color(0xFFE0668E), height = 48.dp, textSize = 18.sp) {
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))) } catch (e: Exception) { }
                    }
                }
            }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Legal", style = Type.Heading)
                    Spacer(Modifier.height(8.dp))
                    GameButton("TERMS OF SERVICE", Modifier.fillMaxWidth(), color = BV.Cream, dark = BV.CreamDark, height = 50.dp, textSize = 18.sp, icon = { GameIcon(GIcon.DOC, 26.dp) }) { onLegal("terms") }
                    Spacer(Modifier.height(8.dp))
                    GameButton("PRIVACY POLICY", Modifier.fillMaxWidth(), color = BV.Cream, dark = BV.CreamDark, height = 50.dp, textSize = 18.sp, icon = { GameIcon(GIcon.SHIELD, 26.dp) }) { onLegal("privacy") }
                }
            }
            if (BuildConfig.DEBUG_TOOLS) item {
                Panel(Modifier.fillMaxWidth()) {
                    Text("Debug tools (debug build only)", style = Type.Heading.copy(color = BV.TomatoDark))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GameButton("+10 LVL", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 42.dp, textSize = 14.sp) { App.repo.update { it.copy(highestUnlocked = minOf(10_000, it.highestUnlocked + 10)) } }
                        GameButton("+100 LVL", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 42.dp, textSize = 14.sp) { App.repo.update { it.copy(highestUnlocked = minOf(10_000, it.highestUnlocked + 100)) } }
                        GameButton("+50K $", Modifier.weight(1f), color = BV.Lilac, dark = Color(0xFF8E73E0), height = 42.dp, textSize = 14.sp) { App.repo.update { it.copy(bucks = it.bucks + 50_000, gems = it.gems + 200) } }
                    }
                    Text("Unlocked up to level ${d.highestUnlocked}", style = Type.Small)
                }
            }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    GameButton("RESET PROGRESS", Modifier.fillMaxWidth(), color = BV.Tomato, dark = BV.TomatoDark, height = 46.dp, textSize = 17.sp) { confirmReset = 1 }
                    Spacer(Modifier.height(10.dp))
                    Text("Bun Voyage! v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " · debug" else ""}", style = Type.Small, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    Text("Made with love, buns and code.", style = Type.Small, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
            }
        }
    }
    if (confirmReset > 0) GameDialog({ confirmReset = 0 }) {
        DialogCard(if (confirmReset == 1) "RESET?" else "REALLY?", { confirmReset = 0 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (confirmReset == 1) "This erases all levels, Bucks, stores and items." else "This cannot be undone. Start over from Level 1?", style = Type.Body.copy(textAlign = TextAlign.Center))
                Spacer(Modifier.height(12.dp))
                GameButton(if (confirmReset == 1) "YES, RESET" else "ERASE EVERYTHING", Modifier.fillMaxWidth(), color = BV.Tomato, dark = BV.TomatoDark) {
                    if (confirmReset == 1) confirmReset = 2 else { App.repo.resetProgress(); confirmReset = 0 }
                }
                Spacer(Modifier.height(8.dp))
                GameButton("CANCEL", Modifier.fillMaxWidth(), color = BV.Mint, dark = BV.MintDark, height = 46.dp, textSize = 18.sp) { confirmReset = 0 }
            }
        }
    }
}

@Composable
private fun SettingToggle(icon: GIcon, label: String, on: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        GameIcon(icon, 30.dp); Spacer(Modifier.width(10.dp))
        Text(label, style = Type.Body.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
        GameButton(if (on) "ON" else "OFF", Modifier.width(84.dp), color = if (on) BV.Mint else Color(0xFFCFC5BA), dark = if (on) BV.MintDark else Color(0xFF9E958C), height = 40.dp, textSize = 16.sp, sound = "ui_toggle") { onToggle() }
    }
}

@Composable
private fun VolumeSlider(value: Float, onChange: (Float) -> Unit) {
    Slider(value, onValueChange = onChange, modifier = Modifier.fillMaxWidth().padding(start = 40.dp),
        colors = SliderDefaults.colors(thumbColor = BV.Tomato, activeTrackColor = BV.Mustard, inactiveTrackColor = BV.CreamDark))
}

/** Terms / Privacy: a new screen showing the game-themed HTML bundled in assets/legal. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LegalScreen(doc: String, onBack: () -> Unit) {
    val context = LocalContext.current
    ScreenFrame(if (doc == "terms") "TERMS OF SERVICE" else "PRIVACY POLICY", onBack, color = BV.Mustard, dark = BV.MustardDark) {
        Box(Modifier.fillMaxSize().padding(10.dp).clip(RoundedCornerShape(20.dp)).border(3.dp, BV.Cocoa, RoundedCornerShape(20.dp))) {
            AndroidView({ ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = true
                    settings.allowContentAccess = false
                    settings.domStorageEnabled = false
                    setBackgroundColor(android.graphics.Color.parseColor("#FFF3DC"))
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val url = request.url
                            if (url.scheme == "mailto") { try { context.startActivity(Intent(Intent.ACTION_SENDTO, url)) } catch (e: Exception) { }; return true }
                            return url.scheme != "file"
                        }
                    }
                    loadUrl("file:///android_asset/legal/$doc.html")
                }
            }, Modifier.fillMaxSize())
        }
    }
}

// ============================================================================================ Recipe Book
@Composable
fun RecipeBookScreen(onBack: () -> Unit) {
    val d = App.repo.state.value
    var confetti by remember { mutableIntStateOf(0) }
    ScreenFrame("RECIPE BOOK", onBack, color = BV.Teal) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                val got = Cards.ALL.count { it.id in d.cards }
                Panel(Modifier.fillMaxWidth()) {
                    Text("Collected $got / ${Cards.ALL.size}", style = Type.Heading)
                    ProgressBar3D(got.toFloat() / Cards.ALL.size, Modifier.fillMaxWidth().padding(top = 6.dp), color = BV.Teal)
                    Text("Complete a set to earn gems!", style = Type.Small)
                }
            }
            items(Cards.SETS) { set ->
                val cards = Cards.ALL.filter { it.set == set }
                val complete = cards.all { it.id in d.cards }
                val claimed = set in d.setsClaimed
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(set, style = Type.Heading, modifier = Modifier.weight(1f))
                        Text("${cards.count { it.id in d.cards }}/${cards.size}", style = Type.Body)
                    }
                    Spacer(Modifier.height(8.dp))
                    cards.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { c ->
                                val have = c.id in d.cards
                                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (have) Color.White else Color(0xFFE4DAD0)).border(2.dp, if (have) BV.Mustard else BV.CreamDark, RoundedCornerShape(12.dp)).padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                        if (have) ArtImage(if (c.id.startsWith("store_")) c.id else "card_${c.id}", Modifier.size(44.dp)) { GameIcon(GIcon.STAR, 34.dp) } else GameIcon(GIcon.LOCK, 26.dp)
                                    }
                                    Text(if (have) c.name else "???", style = Type.Small.copy(fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 2)
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    if (complete && !claimed) GameButton("CLAIM ${Cards.setReward(set)} GEMS", Modifier.fillMaxWidth(), height = 46.dp, textSize = 17.sp, sound = "ui_reward") { App.repo.claimSet(set); confetti++ }
                    else if (claimed) Text("Set complete ✓", style = Type.Body.copy(color = BV.MintDark))
                    else Text(cards.firstOrNull { it.id !in d.cards }?.hint?.let { "Next: $it" } ?: "", style = Type.Small)
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}

// ============================================================================================ Achievements
@Composable
fun AchievementsScreen(onBack: () -> Unit) {
    App.repo.state.value
    var confetti by remember { mutableIntStateOf(0) }
    ScreenFrame("TROPHIES", onBack, color = BV.Mustard, dark = BV.MustardDark) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Achievements.TRACKS) { t ->
                val value = App.repo.achievementValue(t.key)
                val claimed = App.repo.data.achievementsClaimed
                val nextTier = t.tiers.indices.firstOrNull { Achievements.id(t.key, it) !in claimed } ?: -1
                Panel(Modifier.fillMaxWidth(), padding = 12.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GameIcon(GIcon.TROPHY, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name + if (nextTier >= 0) " ${"I".repeat(nextTier + 1).replace("IIII", "IV").replace("IIIII", "V")}" else " ★", style = Type.Heading.copy(fontSize = 17.sp))
                            val goal = if (nextTier >= 0) t.tiers[nextTier] else t.tiers.last()
                            ProgressBar3D(value.toFloat() / goal, Modifier.fillMaxWidth().padding(top = 4.dp), height = 14.dp, color = BV.Mustard)
                            Text("${formatNum(minOf(value, goal))} / ${formatNum(goal)}", style = Type.Small)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (nextTier >= 0) GameButton("${Achievements.gems(nextTier)}💎", Modifier.width(84.dp), enabled = value >= t.tiers[nextTier], height = 42.dp, textSize = 16.sp, sound = "ui_reward") {
                            App.repo.claimAchievement(t, nextTier); confetti++
                        } else Text("DONE", style = Type.Heading.copy(color = BV.MintDark, fontSize = 16.sp))
                    }
                }
            }
        }
        ConfettiBurst(confetti, Modifier.fillMaxSize())
    }
}
