package com.arslan.hoerzeit

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.arslan.hoerzeit.data.Notif
import com.arslan.hoerzeit.data.Repo
import com.arslan.hoerzeit.ui.C
import com.arslan.hoerzeit.ui.Celebration
import com.arslan.hoerzeit.ui.CelebrationOverlay
import com.arslan.hoerzeit.ui.HistoryScreen
import com.arslan.hoerzeit.ui.HoerzeitTheme
import com.arslan.hoerzeit.ui.LivingBackground
import com.arslan.hoerzeit.ui.ManualEntrySheet
import com.arslan.hoerzeit.ui.TodayScreen
import androidx.compose.animation.core.animateFloatAsState

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = Repo.get(applicationContext)
        Notif.ensureChannel(this)

        setContent {
            HoerzeitTheme {
                AppRoot(repo)
            }
        }
    }
}

@Composable
private fun AppRoot(repo: Repo) {
    val context = LocalContext.current
    val sessions by repo.sessions.collectAsState()
    val activeStart by repo.activeStart.collectAsState()

    // Пересчитываем прогресс и при возврате в приложение — вдруг наступил новый день.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
                repo.activeStart.value?.let { Notif.showRunning(context, it) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val progress = remember(sessions, resumeTick) { repo.progress(sessions) }

    var tab by remember { mutableIntStateOf(0) }
    var showManual by remember { mutableStateOf(false) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) repo.activeStart.value?.let { Notif.showRunning(context, it) }
    }

    fun askNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize()) {
        LivingBackground()

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Header(tab = tab, onTab = { tab = it })

            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val forward = targetState > initialState
                    val offset = if (forward) 90 else -90
                    (slideInHorizontally(tween(220)) { offset } + fadeIn(tween(180)))
                        .togetherWith(slideOutHorizontally(tween(180)) { -offset } + fadeOut(tween(140)))
                },
                label = "screens",
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) { current ->
                when (current) {
                    0 -> TodayScreen(
                        progress = progress,
                        activeStart = activeStart,
                        onStart = {
                            askNotificationPermissionIfNeeded()
                            repo.start()
                            repo.activeStart.value?.let { Notif.showRunning(context, it) }
                        },
                        onStop = {
                            val session = repo.stop()
                            Notif.clear(context)
                            if (session != null) {
                                celebration = Celebration(session.durationMs, repo.progress())
                            } else {
                                Toast.makeText(
                                    context,
                                    "Сессия слишком короткая — не записал",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onManual = { showManual = true },
                        modifier = Modifier.fillMaxSize()
                    )

                    else -> HistoryScreen(
                        sessions = sessions,
                        onDelete = { repo.remove(it) },
                        onManual = { showManual = true },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        CelebrationOverlay(data = celebration, onDismiss = { celebration = null })
    }

    if (showManual) {
        ManualEntrySheet(
            onDismiss = { showManual = false },
            onSave = { start, end ->
                showManual = false
                repo.add(start, end)
                celebration = Celebration(end - start, repo.progress())
            }
        )
    }
}

@Composable
private fun Header(tab: Int, onTab: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "Hörzeit",
                fontSize = 27.sp,
                fontWeight = FontWeight.SemiBold,
                color = C.Ink,
                letterSpacing = (-0.8).sp
            )
            Spacer(Modifier.padding(horizontal = 5.dp))
            Text(
                "Deutsch hören · 60 ч",
                style = MaterialTheme.typography.bodyMedium,
                color = C.Muted,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        SegmentedTabs(tab = tab, onTab = onTab)
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SegmentedTabs(tab: Int, onTab: (Int) -> Unit) {
    val shift by animateFloatAsState(
        targetValue = tab.toFloat(),
        animationSpec = tween(220),
        label = "tabs"
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.5f)
                .fillMaxHeight()
                .graphicsLayer { translationX = shift * size.width }
                .clip(RoundedCornerShape(11.dp))
                .background(C.Clay)
        )
        Row(Modifier.fillMaxSize()) {
            TabLabel("Сегодня", selected = tab == 0, modifier = Modifier.weight(1f)) { onTab(0) }
            TabLabel("История", selected = tab == 1, modifier = Modifier.weight(1f)) { onTab(1) }
        }
    }
}

@Composable
private fun TabLabel(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else C.InkSoft
        )
    }
}
