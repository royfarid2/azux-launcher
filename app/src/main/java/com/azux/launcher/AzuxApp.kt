package com.azux.launcher

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azux.launcher.ui.AzuxGray
import com.azux.launcher.ui.AzuxIce
import com.azux.launcher.ui.AzuxWhite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private val BgDark = Color(0xFF141414)
private val BarColor = Color(0xFF1E1E1E)
private val CardC = Color(0xFF262626)
private val RightPanel = Color(0xFF1B1B1B)
private val BtnGray = Color(0xFF3A3A3A)
private val TextDim = Color(0xFFB4B4B4)

private enum class Screen { Main, Versions, Settings }

@Composable
private fun panelColors() = CardDefaults.cardColors(containerColor = CardC, contentColor = AzuxWhite)

@Composable
fun AzuxApp() {
    Surface(Modifier.fillMaxSize(), color = BgDark, contentColor = AzuxWhite) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (Azux.username.isBlank()) NameGate() else Root()
        }
    }
}

// ---------- First launch: ask for the player name ----------
@Composable
fun NameGate() {
    var name by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    fun submit() {
        val n = name.trim()
        if (Azux.validName(n)) Azux.saveName(n) else err = "3-16 characters: letters, numbers or _ only"
    }
    Row(
        Modifier.fillMaxSize().padding(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text("AZUX", fontSize = 64.sp, fontWeight = FontWeight.Black, letterSpacing = 12.sp, color = AzuxIce)
            Text("YOUR MINECRAFT. YOUR WORLD. YOUR AZUX.", fontSize = 12.sp, letterSpacing = 2.sp, color = AzuxGray)
        }
        Column(Modifier.weight(1f)) {
            Text("Welcome", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Enter your player name to get started.", color = AzuxGray)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(16); err = null },
                singleLine = true,
                label = { Text("Player name") },
                isError = err != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth()
            )
            val e = err
            if (e != null) Text(e, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { submit() },
                colors = ButtonDefaults.buttonColors(containerColor = BtnGray, contentColor = AzuxWhite),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Enter", fontWeight = FontWeight.Bold) }
        }
    }
}

// ---------- Root: top bar + screens ----------
@Composable
private fun Root() {
    var screen by remember { mutableStateOf(Screen.Main) }
    BackHandler(enabled = screen != Screen.Main) { screen = Screen.Main }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            screen = screen,
            onBack = { screen = Screen.Main },
            onDownloads = { screen = Screen.Versions },
            onSettings = { screen = Screen.Settings }
        )
        val st = Azux.dl
        if (st != null && !st.finished && st.error == null) {
            val p = if (st.total > 0) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().height(3.dp))
        } else {
            Spacer(Modifier.height(3.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (screen) {
                Screen.Main -> MainScreen(openVersions = { screen = Screen.Versions })
                Screen.Versions -> Box(Modifier.fillMaxSize().padding(16.dp)) { VersionsScreen() }
                Screen.Settings -> Box(Modifier.fillMaxSize().padding(16.dp)) { SettingsScreen() }
            }
        }
    }
}

@Composable
private fun TopBar(screen: Screen, onBack: () -> Unit, onDownloads: () -> Unit, onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(BarColor).height(56.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (screen != Screen.Main) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Text(
            when (screen) { Screen.Main -> "Azux Launcher"; Screen.Versions -> "Versions"; Screen.Settings -> "Settings" },
            fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
        )
        if (screen == Screen.Main) {
            IconButton(onClick = onDownloads) { Icon(Icons.Filled.Download, contentDescription = "Versions", modifier = Modifier.size(28.dp)) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings", modifier = Modifier.size(28.dp)) }
        }
    }
}

// ---------- Main screen: menu list (left) + player panel (right) ----------
@Composable
private fun MainScreen(openVersions: () -> Unit) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showAbout by remember { mutableStateOf(false) }
    var showDir by remember { mutableStateOf(false) }
    var launchMsg by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(2.2f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MenuCard(Icons.Filled.Info, "About Azux Launcher") { showAbout = true }
            MenuCard(Icons.Filled.Download, "Manage versions") { openVersions() }
            MenuCard(Icons.Filled.FolderOpen, "Open main directory") { showDir = true }
            MenuCard(Icons.Filled.Share, "Share log files") { shareLogs(ctx) }
            Azux.dl?.let { DownloadCard(it) }
            Azux.loadError?.let { Text("Offline: $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().background(RightPanel).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            SteveHead(92.dp)
            Spacer(Modifier.height(14.dp))
            Text(Azux.username, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextDim)
            Text("Offline Account", fontSize = 15.sp, color = TextDim)
            Spacer(Modifier.weight(1f))
            val v = Azux.selected
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(CardC), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.SportsEsports, contentDescription = null, tint = TextDim)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (v.isBlank()) "No version selected" else v, fontSize = 15.sp, color = TextDim)
                    Text(
                        when {
                            v.isBlank() -> "Tap the gear to choose"
                            v in Azux.installed -> "Installed"
                            else -> "Not downloaded"
                        },
                        fontSize = 13.sp, color = TextDim
                    )
                }
                IconButton(onClick = openVersions) { Icon(Icons.Filled.Settings, contentDescription = "Choose version", tint = AzuxWhite) }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    launchMsg = when {
                        v.isBlank() -> "Pick a version first. Open Versions, download one and select it." to true
                        v !in Azux.installed -> "Version $v is not downloaded yet." to true
                        else -> ("Version $v is installed and verified (game, libraries and assets are on this device).\n\n" +
                            "The Java runtime and renderer that actually run Minecraft on Android are not connected yet, " +
                            "so the game cannot start from here.") to false
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BtnGray, contentColor = AzuxWhite),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Launch Game", fontWeight = FontWeight.Medium, fontSize = 16.sp) }
        }
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("Azux Launcher") },
            text = { Text("Version 0.3.0\nYOUR MINECRAFT. YOUR WORLD. YOUR AZUX.\n\nVersion manager and downloads are built in. The game engine is not connected yet.") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } }
        )
    }
    if (showDir) {
        val path = Azux.root.absolutePath
        AlertDialog(
            onDismissRequest = { showDir = false },
            title = { Text("Main directory") },
            text = { Text(path) },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(path)); showDir = false }) { Text("Copy path") } },
            dismissButton = { TextButton(onClick = { showDir = false }) { Text("Close") } }
        )
    }
    val d = launchMsg
    if (d != null) {
        AlertDialog(
            onDismissRequest = { launchMsg = null },
            title = { Text("Launch Game") },
            text = { Text(d.first) },
            confirmButton = {
                TextButton(onClick = { launchMsg = null; if (d.second) openVersions() }) { Text(if (d.second) "Versions" else "OK") }
            },
            dismissButton = if (d.second) ({ TextButton(onClick = { launchMsg = null }) { Text("Close") } }) else null
        )
    }
}

@Composable
private fun MenuCard(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardC).clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(22.dp))
        Text(text, fontSize = 18.sp)
    }
}

private fun shareLogs(ctx: Context) {
    val text = Azux.logText().ifBlank { "Azux Launcher log is empty." }
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Azux Launcher log")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    ctx.startActivity(Intent.createChooser(i, "Share log files"))
}

// Default "Steve"-style head drawn with pixels (offline account has no skin)
private val headRows = listOf(
    "HHHHHHHH",
    "HHHHHHHH",
    "HSSSSSSH",
    "SSSSSSSS",
    "SWBSSBWS",
    "SSSNNSSS",
    "SMMMMMMS",
    "SMMSSMMS"
)

private fun headColor(c: Char): Color = when (c) {
    'H' -> Color(0xFF3A2412)
    'W' -> Color(0xFFFFFFFF)
    'B' -> Color(0xFF4B3EA8)
    'N' -> Color(0xFF8C5E43)
    'M' -> Color(0xFF6B4430)
    else -> Color(0xFFB98A68)
}

@Composable
private fun SteveHead(size: Dp) {
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / 8f
        for (y in 0 until 8) for (x in 0 until 8) {
            drawRect(
                color = headColor(headRows[y][x]),
                topLeft = Offset(x * cell, y * cell),
                size = Size(cell + 0.6f, cell + 0.6f)
            )
        }
    }
}

// ---------- Download progress card ----------
@Composable
fun DownloadCard(st: DlState) {
    val err = st.error
    Card(Modifier.fillMaxWidth(), colors = panelColors()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Minecraft ${st.id}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (err != null || st.finished) {
                    TextButton(onClick = { Azux.dismiss() }) { Text("Dismiss") }
                } else {
                    TextButton(onClick = { Azux.cancel() }) { Text("Cancel") }
                }
            }
            when {
                err != null -> {
                    Text("Failed: $err", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    Text("Press Download again to resume. Finished files are kept.", color = AzuxGray, fontSize = 12.sp)
                }
                st.finished -> Text("Installed and verified", color = AzuxIce)
                else -> {
                    val p = if (st.total > 0) (st.done.toFloat() / st.total).coerceIn(0f, 1f) else 0f
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    val eta = if (st.speed > 0 && st.total > st.done) {
                        val s = (st.total - st.done) / st.speed
                        " | ${s / 60}:${"%02d".format(s % 60)} left"
                    } else ""
                    Text(
                        "${st.phase} | ${(p * 100).toInt()}% | ${fmtBytes(st.done)} / ${fmtBytes(st.total)} | ${fmtBytes(st.speed)}/s$eta",
                        color = AzuxGray, fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ---------- Versions ----------
@Composable
fun VersionsScreen() {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("Release") }
    val chipColors = FilterChipDefaults.filterChipColors(selectedContainerColor = BtnGray, selectedLabelColor = AzuxWhite)
    val list = Azux.versions.filter { v ->
        val okType = when (filter) {
            "Release" -> v.type == "release"
            "Snapshot" -> v.type == "snapshot"
            "Old" -> v.type.startsWith("old")
            "Installed" -> v.id in Azux.installed
            else -> true
        }
        okType && v.id.contains(query.trim(), ignoreCase = true)
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search versions") }, modifier = Modifier.weight(1f)
            )
            if (Azux.loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            else IconButton(onClick = { Azux.refreshManifest() }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Release", "Snapshot", "Old", "Installed", "All").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) }, colors = chipColors)
            }
        }
        Azux.loadError?.let { Text("Could not refresh: $it", color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Azux.dl?.let { DownloadCard(it) }
        if (list.isEmpty() && !Azux.loading) {
            Text(if (Azux.versions.isEmpty()) "No versions yet. Check your internet and press refresh." else "Nothing matches.", color = AzuxGray)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.id }) { v -> VersionRow(v) }
        }
    }
}

@Composable
fun VersionRow(v: McVersion) {
    val isInstalled = v.id in Azux.installed
    val active = Azux.dl
    val downloadingThis = active != null && active.id == v.id && !active.finished && active.error == null
    var confirmDelete by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth(), colors = panelColors()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(v.id, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${v.type} | ${v.date}", color = AzuxGray, fontSize = 12.sp)
            }
            when {
                isInstalled -> {
                    if (Azux.selected == v.id) {
                        Text("SELECTED", color = AzuxIce, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    } else {
                        OutlinedButton(onClick = { Azux.select(v.id) }) { Text("Select") }
                    }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete") }
                }
                downloadingThis -> Text("Downloading...", color = AzuxGray)
                else -> Button(
                    onClick = { Azux.install(v) }, enabled = !Azux.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = BtnGray, contentColor = AzuxWhite)
                ) { Text("Download") }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${v.id}?") },
            text = { Text("The version files will be removed from this device.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; Azux.delete(v.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ---------- Settings ----------
@Composable
fun SettingsScreen() {
    var name by remember { mutableStateOf(Azux.username) }
    var msg by remember { mutableStateOf<String?>(null) }
    var used by remember { mutableStateOf<Long?>(null) }
    var ram by remember { mutableStateOf(Azux.ramMb.toFloat()) }
    LaunchedEffect(Azux.dl?.finished, Azux.installed.size) {
        used = withContext(Dispatchers.IO) { Azux.storageUsed() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(Modifier.fillMaxWidth(), colors = panelColors()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Account (offline)", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(16); msg = null }, singleLine = true,
                    label = { Text("Player name") }, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            val n = name.trim()
                            if (Azux.validName(n)) { Azux.saveName(n); msg = "Saved" } else msg = "3-16 characters: letters, numbers or _ only"
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BtnGray, contentColor = AzuxWhite)
                    ) { Text("Save") }
                    msg?.let { Text(it, color = AzuxGray, fontSize = 12.sp) }
                }
                Text("UUID: ${Azux.uuid}", color = AzuxGray, fontSize = 11.sp)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = panelColors()) {
            Column(Modifier.padding(16.dp)) {
                Text("Memory (RAM)", fontWeight = FontWeight.Bold)
                Text("${(ram / 256f).roundToInt() * 256} MB", color = AzuxIce)
                Slider(
                    value = ram, onValueChange = { ram = it },
                    onValueChangeFinished = { Azux.saveRam((ram / 256f).roundToInt() * 256) },
                    valueRange = 1024f..4096f, steps = 11
                )
                Text("Saved now, applied once the game engine is connected.", color = AzuxGray, fontSize = 11.sp)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = panelColors()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Storage", fontWeight = FontWeight.Bold)
                Text(Azux.root.absolutePath, color = AzuxGray, fontSize = 11.sp)
                Text("Used: ${used?.let { fmtBytes(it) } ?: "..."}", color = AzuxGray)
                OutlinedButton(onClick = { Azux.clearCache(); msg = "Version list cache cleared" }) { Text("Clear version list cache") }
            }
        }
        Text("Azux Launcher 0.3.0", color = AzuxGray, fontSize = 11.sp)
    }
}
