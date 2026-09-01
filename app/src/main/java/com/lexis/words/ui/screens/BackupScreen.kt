package com.lexis.words.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lexis.words.AppViewModel
import com.lexis.words.ui.components.BackChevron
import com.lexis.words.ui.components.ToastHost
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

private data class BackupPart(val title: String, val desc: String, val size: String, val color: Color)

private val BACKUP_PARTS = listOf(
    BackupPart("Слова и переводы", "Все блоки и списки", "—", Accent),
    BackupPart("Картинки к словам", "В оригинальном размере", "—", Color(0xFFEC4C8C)),
    BackupPart("Прогресс повторений", "Ступень, дата последнего ответа, дата возврата, число ошибок", "—", Color(0xFF2E9BF0)),
    BackupPart("Структура", "Блоки, списки, цвета, обложки, порядок", "—", Color(0xFF10B393)),
    BackupPart("Настройки", "Интервалы, время 6:00, размер подхода, переключатели", "—", Color(0xFF6A5AE0)),
    BackupPart("Ключ подписи и данные сборки", "lexis-release.jks, alias, пароли, package name", "—", Ink),
    BackupPart("Исходный код приложения", "Полный код текущей версии", "—", Color(0xFF7A6B58)),
)

@Composable
fun BackupScreen(nav: NavController, vm: AppViewModel) {
    val context = LocalContext.current
    val toast by vm.toast.collectAsState()

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.restoreBackup(uri)
    }

    Box(Modifier.fillMaxSize().background(ScreenBg)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 62.dp, start = 18.dp, end = 18.dp, bottom = 40.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BackChevron(onClick = { nav.popBackStack() })
                    Text("Резервная копия", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 24.sp, color = Ink)
                }
                Spacer(Modifier.height(20.dp))

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ink).padding(20.dp)) {
                    Text("ОДИН ФАЙЛ · ВСЁ ЦЕЛИКОМ", fontFamily = Nunito, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), letterSpacing = 1.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("lexis-backup.lexis", fontFamily = Nunito, fontWeight = FontWeight.Black, fontSize = 22.sp, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Восстанавливает приложение полностью, без потери прогресса", fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.62f))
                }
                Spacer(Modifier.height(14.dp))

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White)) {
                    Text("Что внутри копии", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.5.sp, color = Ink, modifier = Modifier.padding(17.dp, 15.dp, 17.dp, 13.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFFAF5EE)))
                    BACKUP_PARTS.forEachIndexed { i, p ->
                        Row(Modifier.fillMaxWidth().padding(17.dp, 13.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.padding(top = 6.dp).size(9.dp).clip(RoundedCornerShape(5.dp)).background(p.color))
                            Column(Modifier.weight(1f)) {
                                Text(p.title, fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Ink)
                                Text(p.desc, fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = TextMuted2, lineHeight = 16.sp)
                            }
                        }
                        if (i != BACKUP_PARTS.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFFAF5EE)))
                    }
                }
                Spacer(Modifier.height(14.dp))

                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(AccentTintBg).padding(16.dp)) {
                    Text("По каждому слову сохраняется", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = AccentTintInk)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "блок и список · ступень интервала (1 / 3 / 7 / 14 / 30) · дата последнего повтора · сколько дней осталось · число верных и ошибок · картинка",
                        fontFamily = Nunito, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp, color = AccentTintMuted, lineHeight = 18.sp
                    )
                }
                Spacer(Modifier.height(14.dp))

                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Accent)
                        .clickable {
                            vm.createBackup { uri, _ ->
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/zip"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Сохранить резервную копию"))
                            }
                        }
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Скачать резервную копию", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color.White) }
                Spacer(Modifier.height(9.dp))
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(NeutralBtnBg)
                        .clickable { restoreLauncher.launch("*/*") }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Восстановить из копии", fontFamily = Nunito, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color(0xFF7A6B58)) }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp)) { ToastHost(toast) }
    }
}
