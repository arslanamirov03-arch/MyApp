package com.lexis.words.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.navigation.NavController
import com.lexis.words.BuildConfig
import com.lexis.words.ui.components.BackChevron
import com.lexis.words.ui.theme.Accent
import com.lexis.words.ui.theme.AccentTintBg
import com.lexis.words.ui.theme.AccentTintInk
import com.lexis.words.ui.theme.AccentTintMuted
import com.lexis.words.ui.theme.Ink
import com.lexis.words.ui.theme.NeutralBtnBg
import com.lexis.words.ui.theme.Nunito
import com.lexis.words.ui.theme.ScreenBg
import com.lexis.words.ui.theme.TextMuted2
import com.lexis.words.ui.theme.TextMuted3

@Composable
fun AboutScreen(nav: NavController) {
    val context = LocalContext.current
    var showPasswords by remember { mutableStateOf(false) }

    fun copySigningInfo() {
        val cm = context.getSystemService<ClipboardManager>() ?: return
        val text = buildString {
            appendLine("Файл ключа: ${BuildConfig.SIGNING_KEYSTORE_NAME}")
            appendLine("Key alias: ${BuildConfig.SIGNING_KEY_ALIAS}")
            appendLine("Store password: ${BuildConfig.SIGNING_STORE_PASSWORD}")
            appendLine("Key password: ${BuildConfig.SIGNING_KEY_PASSWORD}")
            appendLine("Package name: ${BuildConfig.APPLICATION_ID}")
            append("SHA-256: ${BuildConfig.SIGNING_SHA256}")
        }
        cm.setPrimaryClip(ClipData.newPlainText("Lexis signing", text))
    }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 62.dp, start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BackChevron(onClick = { nav.popBackStack() })
                    Text("О приложении", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Ink)
                }
                Spacer(Modifier.height(20.dp))

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Accent), contentAlignment = Alignment.Center) {
                        Text("L", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color.White)
                    }
                    Column {
                        Text("Lexis", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ink)
                        Text("Версия ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE}) · Android", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, color = TextMuted3)
                    }
                }
                Spacer(Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)) {
                    Text("ПОДПИСЬ СБОРКИ", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 11.5.sp, color = TextMuted3, letterSpacing = 1.sp)
                    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1EAE0)).padding(horizontal = 9.dp, vertical = 4.dp)) {
                        Text("ТОЛЬКО ЧТЕНИЕ", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, color = Color(0xFF7A6B58))
                    }
                }

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White)) {
                    SigningRow("Файл ключа", BuildConfig.SIGNING_KEYSTORE_NAME, mono = true)
                    Divider()
                    SigningRow("Key alias", BuildConfig.SIGNING_KEY_ALIAS, mono = true)
                    Divider()
                    SigningRow("Store password", if (showPasswords) BuildConfig.SIGNING_STORE_PASSWORD else "••••••••••••", mono = true)
                    Divider()
                    SigningRow("Key password", if (showPasswords) BuildConfig.SIGNING_KEY_PASSWORD else "••••••••••••", mono = true)
                    Divider()
                    SigningRow("Package name", BuildConfig.APPLICATION_ID, mono = true)
                    Divider()
                    SigningRow("SHA-256", BuildConfig.SIGNING_SHA256, mono = true, last = true)
                }
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(NeutralBtnBg)
                            .clickable { showPasswords = !showPasswords }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text(if (showPasswords) "Скрыть пароли" else "Показать пароли", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = Color(0xFF7A6B58)) }
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(NeutralBtnBg)
                            .clickable { copySigningInfo() }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Скопировать", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = Color(0xFF7A6B58)) }
                }
                Spacer(Modifier.height(14.dp))

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AccentTintBg).padding(16.dp)) {
                    Text("Эти данные менять нельзя", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = AccentTintInk)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Обновления собираются тем же ключом и тем же package name, поэтому приложение обновляется поверх старого и все слова, картинки и прогресс остаются на месте. Смена ключа или имени пакета сделает обновление невозможным.",
                        fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = AccentTintMuted, lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.height(14.dp))

                if (!BuildConfig.HAS_REAL_SIGNING) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFDEBF2)).padding(16.dp)) {
                        Text("Локальная сборка", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Color(0xFFC63069))
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Это сборка без релизного ключа — данные выше не настоящие. Финальный APK подписывается настоящим ключом в GitHub Actions.",
                            fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color(0xFFC63069), lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFFAF5EE)))

@Composable
private fun SigningRow(label: String, value: String, mono: Boolean = false, last: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(17.dp, 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextMuted2, modifier = Modifier.weight(1f))
        Text(
            value, fontFamily = if (mono) FontFamily.Monospace else Nunito,
            fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, color = Ink
        )
    }
}
