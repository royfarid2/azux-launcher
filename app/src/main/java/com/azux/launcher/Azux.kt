package com.azux.launcher

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

data class McVersion(val id: String, val type: String, val url: String, val date: String)

class DlState(val id: String) {
    var phase by mutableStateOf("Preparing")
    var done by mutableStateOf(0L)
    var total by mutableStateOf(0L)
    var speed by mutableStateOf(0L)
    var error by mutableStateOf<String?>(null)
    var finished by mutableStateOf(false)
    var job: Job? = null
}

private class DlTask(val url: String, val file: File, val sha1: String, val size: Long)

fun fmtBytes(b: Long): String = when {
    b >= 1073741824L -> "%.2f GB".format(b / 1073741824.0)
    b >= 1048576L -> "%.1f MB".format(b / 1048576.0)
    b >= 1024L -> "${b / 1024} KB"
    else -> "$b B"
}

object Azux {
    private const val MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private lateinit var prefs: SharedPreferences
    lateinit var root: File
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var username by mutableStateOf("")
    var selected by mutableStateOf("")
    var ramMb by mutableStateOf(2048)
    var versions by mutableStateOf<List<McVersion>>(emptyList())
    var latestRelease by mutableStateOf("")
    var loading by mutableStateOf(false)
    var loadError by mutableStateOf<String?>(null)
    val installed = mutableStateListOf<String>()
    var dl by mutableStateOf<DlState?>(null)

    val uuid: String
        get() = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray(Charsets.UTF_8)).toString()

    val busy: Boolean
        get() = dl?.let { !it.finished && it.error == null } == true

    fun init(ctx: Context) {
        prefs = ctx.getSharedPreferences("azux", Context.MODE_PRIVATE)
        root = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "azux").also { it.mkdirs() }
        username = prefs.getString("name", "") ?: ""
        selected = prefs.getString("selected", "") ?: ""
        ramMb = prefs.getInt("ram", 2048)
        installed.clear()
        File(root, "versions").listFiles()
            ?.filter { File(it, ".complete").exists() }
            ?.sortedBy { it.name }
            ?.forEach { installed.add(it.name) }
        val cache = File(root, "manifest.json")
        if (cache.exists()) runCatching { parseManifest(cache.readText()) }
        refreshManifest()
    }

    fun validName(n: String) = Regex("^[A-Za-z0-9_]{3,16}$").matches(n)

    fun saveName(n: String) {
        username = n
        prefs.edit().putString("name", n).apply()
    }

    fun select(id: String) {
        selected = id
        prefs.edit().putString("selected", id).apply()
    }

    fun saveRam(mb: Int) {
        ramMb = mb
        prefs.edit().putInt("ram", mb).apply()
    }

    fun log(msg: String) {
        runCatching {
            val t = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())
            File(root, "launcher.log").appendText("$t  $msg\n")
        }
    }

    fun logText(): String = runCatching { File(root, "launcher.log").readText().takeLast(30000) }.getOrDefault("")

    fun clearCache() {
        File(root, "manifest.json").delete()
    }

    fun storageUsed(): Long =
        root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun refreshManifest() {
        if (loading) return
        loading = true
        loadError = null
        scope.launch {
            try {
                val text = httpText(MANIFEST_URL)
                File(root, "manifest.json").writeText(text)
                parseManifest(text)
            } catch (e: Exception) {
                loadError = e.message ?: "Network error"
                log("Version list refresh failed: $loadError")
            } finally {
                loading = false
            }
        }
    }

    private fun parseManifest(text: String) {
        val o = JSONObject(text)
        latestRelease = o.getJSONObject("latest").getString("release")
        val arr = o.getJSONArray("versions")
        val list = ArrayList<McVersion>(arr.length())
        for (i in 0 until arr.length()) {
            val v = arr.getJSONObject(i)
            list.add(McVersion(v.getString("id"), v.getString("type"), v.getString("url"), v.optString("releaseTime").take(10)))
        }
        versions = list
    }

    fun install(v: McVersion) {
        if (busy) return
        val st = DlState(v.id)
        dl = st
        log("Download started: ${v.id}")
        st.job = scope.launch {
            try {
                runInstall(v, st)
                if (v.id !in installed) installed.add(v.id)
                if (selected.isBlank()) select(v.id)
                st.finished = true
                log("Download finished: ${v.id}")
                delay(3000)
                if (dl === st) dl = null
            } catch (e: CancellationException) {
                if (dl === st) dl = null
                throw e
            } catch (e: Exception) {
                st.error = e.message ?: e.javaClass.simpleName
                log("Download failed: ${v.id} - ${st.error}")
            }
        }
    }

    fun cancel() {
        dl?.job?.cancel()
        dl = null
    }

    fun dismiss() {
        dl = null
    }

    fun delete(id: String) {
        scope.launch {
            File(root, "versions/$id").deleteRecursively()
            installed.remove(id)
            if (selected == id) select("")
        }
    }

    private suspend fun runInstall(v: McVersion, st: DlState) {
        val vdir = File(root, "versions/${v.id}")
        vdir.mkdirs()
        File(vdir, ".complete").delete()

        st.phase = "Reading version data"
        val jsonText = httpText(v.url)
        File(vdir, "${v.id}.json").writeText(jsonText)
        val j = JSONObject(jsonText)

        val tasks = ArrayList<DlTask>()
        val client = j.getJSONObject("downloads").getJSONObject("client")
        tasks.add(DlTask(client.getString("url"), File(vdir, "${v.id}.jar"), client.getString("sha1"), client.getLong("size")))

        val libs = j.optJSONArray("libraries")
        if (libs != null) {
            for (i in 0 until libs.length()) {
                val lib = libs.getJSONObject(i)
                if (!allowed(lib)) continue
                val art = lib.optJSONObject("downloads")?.optJSONObject("artifact") ?: continue
                val url = art.optString("url")
                if (url.isBlank()) continue
                tasks.add(DlTask(url, File(root, "libraries/" + art.getString("path")), art.optString("sha1"), art.optLong("size")))
            }
        }

        st.phase = "Reading asset index"
        val ai = j.getJSONObject("assetIndex")
        val idxFile = File(root, "assets/indexes/${ai.getString("id")}.json")
        idxFile.parentFile?.mkdirs()
        val idxText = httpText(ai.getString("url"))
        idxFile.writeText(idxText)
        val objs = JSONObject(idxText).getJSONObject("objects")
        val seen = HashSet<String>()
        for (name in objs.keys()) {
            val o = objs.getJSONObject(name)
            val h = o.getString("hash")
            if (seen.add(h)) {
                val p = h.substring(0, 2)
                tasks.add(DlTask("https://resources.download.minecraft.net/$p/$h", File(root, "assets/objects/$p/$h"), h, o.getLong("size")))
            }
        }

        st.total = tasks.sumOf { it.size }
        st.phase = "Downloading"
        val counter = AtomicLong(0)
        coroutineScope {
            val ticker = launch {
                var last = 0L
                var lastT = System.nanoTime()
                while (isActive) {
                    delay(400)
                    val d = counter.get()
                    val now = System.nanoTime()
                    st.speed = ((d - last) * 1_000_000_000.0 / (now - lastT)).toLong().coerceAtLeast(0)
                    st.done = d
                    last = d
                    lastT = now
                }
            }
            val sem = Semaphore(8)
            tasks.map { t -> async { sem.withPermit { fetchFile(t, counter) } } }.awaitAll()
            ticker.cancel()
        }
        st.done = st.total
        File(vdir, ".complete").writeText("ok")
    }

    private fun allowed(lib: JSONObject): Boolean {
        val rules = lib.optJSONArray("rules") ?: return true
        var allow = false
        for (i in 0 until rules.length()) {
            val r = rules.getJSONObject(i)
            val os = r.optJSONObject("os")
            if (os == null || os.optString("name") == "linux") {
                allow = r.getString("action") == "allow"
            }
        }
        return allow
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "AzuxLauncher/0.2")
        return c
    }

    private fun httpText(url: String): String {
        val c = open(url)
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun sha1(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { ins ->
            val b = ByteArray(65536)
            while (true) {
                val n = ins.read(b)
                if (n < 0) break
                md.update(b, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun fetchFile(t: DlTask, counter: AtomicLong) {
        val dest = t.file
        if (dest.exists() && dest.length() == t.size) {
            counter.addAndGet(t.size)
            return
        }
        dest.parentFile?.mkdirs()
        var attempt = 0
        while (true) {
            val part = File(dest.path + ".part")
            var added = 0L
            try {
                val c = open(t.url)
                try {
                    if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode} for ${dest.name}")
                    c.inputStream.use { ins ->
                        part.outputStream().use { out ->
                            val buf = ByteArray(32768)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val n = ins.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                counter.addAndGet(n.toLong())
                                added += n
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
                if (t.sha1.isNotEmpty() && sha1(part) != t.sha1) throw IOException("Checksum mismatch: ${dest.name}")
                dest.delete()
                if (!part.renameTo(dest)) throw IOException("Could not save ${dest.name}")
                return
            } catch (e: CancellationException) {
                counter.addAndGet(-added)
                part.delete()
                throw e
            } catch (e: Exception) {
                counter.addAndGet(-added)
                part.delete()
                attempt++
                if (attempt >= 3) throw e
                delay(1000)
            }
        }
    }
}
