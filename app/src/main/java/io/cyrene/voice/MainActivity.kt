package io.cyrene.voice

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import io.cyrene.voice.core.LocalVoiceEngine
import io.cyrene.voice.core.VoicePackStore
import io.cyrene.voice.core.VoiceRole
import io.cyrene.voice.core.ReferenceAudio
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {
    private lateinit var store: VoicePackStore
    private val engine = LocalVoiceEngine()
    private val worker = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var pack: File? = null
    private var selected: VoiceRole? = null
    private var exportPack: File? = null
    private var pendingReference: File? = null
    private var busy = false
    private var destroyed = false
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var progress: SeekBar
    private lateinit var transport: TextView
    private lateinit var pause: Button
    private var audio: File? = null
    private val queuedAudio = java.util.ArrayDeque<File>()
    private val previewFiles = mutableListOf<File>()
    private var synthesisDone = true
    private var readingPaused = false
    private var playingPart = 0
    private val ticker = object : Runnable {
        override fun run() {
            player?.let { p ->
                progress.max = p.duration.coerceAtLeast(1)
                progress.progress = p.currentPosition
                transport.text = "第 ${playingPart.coerceAtLeast(1)} 段 · ${p.currentPosition / 1000}s / ${p.duration / 1000}s"
            }
            handler.postDelayed(this, 250)
        }
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        store = VoicePackStore(File(filesDir, "voice-packs"))
        window.statusBarColor = Color.rgb(245, 243, 250)
        window.navigationBarColor = Color.rgb(245, 243, 250)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(28, 36, 28, 40)
            setBackgroundColor(Color.rgb(245, 243, 250))
        }
        scroll.addView(root); setContentView(scroll)
        root.addView(label("CYRENE  /  VOICE STUDIO", 13))
        root.addView(label("让角色拥有声音", 28))
        root.addView(label("参考录音创建音色 · 手机离线合成 · 系统语音引擎", 15))
        status = label("请先导入 .cyvoice 基础模型包；应用不自动下载模型。", 14)
        root.addView(status)
        root.addView(button("导入 .cyvoice 音色包") { if (!busy) openDocument(10) })
        root.addView(button("切换音色包") {
            if (!busy) {
                val packs = store.packs()
                AlertDialog.Builder(this).setTitle("音色包").setItems(packs.map { store.manifest(it).optString("name", it.name) }.toTypedArray()) { _, i ->
                    stop(); pack = packs[i]; selected = store.roles(packs[i]).firstOrNull(); renderRoles(); prepareModel()
                }.show()
            }
        })
        root.addView(label("角色音色库", 22))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        root.addView(button("＋ 上传录音，创建音色") {
            if (!busy) { if (pack == null) toast("请先导入基础模型包") else openDocument(13) }
        })
        root.addView(label("建议用 2–6 秒的清晰单人短句（支持 1–30 秒 WAV / MP3 / M4A），无背景音乐，并准确填写原文。长录音可能降低克隆稳定性。", 13))
        root.addView(label("朗读与试听", 22))
        input = EditText(this).apply {
            hint = "输入要朗读的文字"; minLines = 3; maxLines = 8
            setText("你好，我是你的离线语音伙伴。即使没有网络，也能陪你阅读。")
            filters = arrayOf(android.text.InputFilter.LengthFilter(8000))
        }
        root.addView(input)
        root.addView(button("用选中角色朗读") { speak() })
        root.addView(button("试听原录音（对比语速与语调）") {
            if (!busy) {
                val p = pack; val r = selected
                if (p != null && r != null) {
                    stop(); queuedAudio.add(File(p, "references/${r.id}.wav")); playNext()
                }
            }
        })
        transport = label("0s / 0s", 14); root.addView(transport)
        progress = SeekBar(this)
        progress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(v: SeekBar?, n: Int, user: Boolean) { if (user) player?.seekTo(n) }
            override fun onStartTrackingTouch(v: SeekBar?) {}
            override fun onStopTrackingTouch(v: SeekBar?) {}
        })
        root.addView(progress)
        pause = button("暂停 / 继续") {
            readingPaused = !readingPaused
            if (readingPaused) { player?.pause(); status.text = "已暂停" }
            else { if (player != null) player?.start() else playNext(); status.text = if (player != null) "正在朗读" else "正在准备下一段…" }
        }
        root.addView(pause)
        root.addView(button("停止") { stop(); status.text = "已停止" })
        root.addView(button("导出朗读 WAV") { if (audio?.isFile == true && !busy) createDocument(12, "audio/wav", "cyrene-reading.wav") else toast("请先完成一次合成") })
        root.addView(button("导出通用 .cyvoice 音色包") {
            if (!busy) pack?.let { exportPack = it; createDocument(11, "application/octet-stream", "cyrene-voices.cyvoice") } ?: toast("请先导入音色包")
        })
        root.addView(button("将当前角色设为系统 TTS 默认音色") {
            val p = pack; val r = selected
            if (p != null && r != null) {
                getSharedPreferences("voice", MODE_PRIVATE).edit().putString("default", "${p.name}/${r.id}").apply()
                AlertDialog.Builder(this).setMessage("默认音色已保存。在系统文字转语音设置中选择 Cyrene Voice，再在阅读器或其他支持系统 TTS 的应用中选择本引擎。切换角色后，请让调用方重新连接引擎；部分应用会保留自己的音色选择。后台限制可能影响长时间合成。")
                    .setPositiveButton("打开系统设置") { _, _ -> runCatching { startActivity(Intent("com.android.settings.TTS_SETTINGS")) }.onFailure { toast("请手动打开系统的文字转语音设置") } }.setNegativeButton("稍后", null).show()
            } else toast("请先创建并选择一个克隆角色")
        })
        root.addView(label("音色包包含模型、参考录音和原文，请自行保管；可导入支持 .cyvoice 格式的应用。其他支持系统 TTS 的应用可直接调用本引擎，无需专用兼容程序。首次合成和长文可能需要等待。", 13))
        pack = store.packs().firstOrNull()
        selected = pack?.let { store.roles(it).firstOrNull() }
        if (pack != null) status.text = "0.2.0 · 完全本地合成 · 支持系统 TTS 与音色包导出。"
        renderRoles()
        prepareModel()
        handler.post(ticker)
    }

    private fun prepareModel() {
        val current = pack ?: return
        worker.execute { runCatching { engine.prepare(current) } }
    }

    private fun label(value: String, size: Int) = TextView(this).apply { text = value; textSize = size.toFloat(); setTextColor(Color.rgb(44, 36, 64)); setPadding(0, 14, 0, 12) }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; isAllCaps = false
        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(230, 221, 242))
        setTextColor(Color.rgb(65, 43, 95))
        setOnClickListener { action() }
    }
    private fun toast(message: String) { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun renderRoles() {
        list.removeAllViews()
        val current = pack ?: return
        store.roles(current).forEach { role ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12, 8, 12, 8) }
            row.addView(button("${if (selected?.id == role.id) "●" else "○"}  ${role.name}  ·  克隆音色  ·  ${role.speed}×") {
                if (!busy) { selected = role; renderRoles() }
            })
            val actions = LinearLayout(this)
            actions.addView(button("编辑") { if (!busy) editRole(role) })
            actions.addView(button("短句加速") { if (!busy) createShortReference(role) })
            actions.addView(button("删除") {
                if (!busy) AlertDialog.Builder(this).setMessage("删除角色「${role.name}」？").setNegativeButton("取消", null).setPositiveButton("删除") { _, _ ->
                    runCatching {
                        val next = store.roles(current).filter { it.id != role.id }
                        store.saveRoles(current, next)
                        File(current, "references/${role.id}.wav").delete()
                        if (selected?.id == role.id) selected = next.firstOrNull()
                        renderRoles()
                    }.onFailure { toast(it.message ?: "删除失败") }
                }.show()
            })
            row.addView(actions); list.addView(row)
        }
    }

    private fun editRole(existing: VoiceRole?) {
        val current = pack ?: return toast("请先导入音色包")
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        val name = EditText(this).apply { hint = "角色名称"; setText(existing?.name.orEmpty()); filters = arrayOf(android.text.InputFilter.LengthFilter(80)) }
        val transcript = EditText(this).apply { hint = "录音中实际说的话（请准确填写）"; minLines = 2; setText(existing?.referenceText.orEmpty()); filters = arrayOf(android.text.InputFilter.LengthFilter(1000)) }
        val rate = SeekBar(this).apply { max = 15; progress = (((existing?.speed ?: 1f) - .5f) * 10).toInt() }
        val rateText = label("参考语速 × ${existing?.speed ?: 1f}（1.0 跟随录音估计）", 16)
        rate.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(v: SeekBar?, n: Int, user: Boolean) { rateText.text = "参考语速 × ${(n + 5) / 10f}（1.0 跟随录音估计）" }
            override fun onStartTrackingTouch(v: SeekBar?) {}
            override fun onStopTrackingTouch(v: SeekBar?) {}
        })
        form.addView(name); form.addView(transcript); form.addView(rateText); form.addView(rate)
        form.addView(button("恢复参考语速") { rate.progress = 5 })
        val stable = android.widget.CheckBox(this).apply {
            text = "稳定语调（B 参数，可能增加等待）"
            isChecked = existing?.stableProsody ?: false
        }
        form.addView(stable)
        form.addView(label("录音决定音色与语调参考，准确原文帮助模型估计节奏。不同句子的语调无法逐字照搬；调用应用的语速和音高建议先保持 1.0。仅导入有权使用的录音。", 13))
        val dialog = AlertDialog.Builder(this).setTitle(if (existing == null) "创建角色" else "编辑角色").setView(form).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            runCatching {
                require(name.text.toString().isNotBlank() && transcript.text.toString().isNotBlank()) { "请填写角色名称和录音原文" }
                val role = VoiceRole(existing?.id ?: UUID.randomUUID().toString(), name.text.toString().trim(), transcript.text.toString().trim(), (rate.progress + 5) / 10f, stable.isChecked)
                if (existing == null) {
                    val source = pendingReference ?: error("请重新导入参考录音")
                    val destination = File(current, "references/${role.id}.wav")
                    destination.parentFile!!.mkdirs(); source.copyTo(destination)
                }
                val roles = store.roles(current).toMutableList()
                val index = roles.indexOfFirst { it.id == role.id }
                if (index >= 0) roles[index] = role else roles.add(role)
                store.saveRoles(current, roles); selected = role; renderRoles(); dialog.dismiss()
                pendingReference?.delete(); pendingReference = null
                status.text = "克隆角色已创建，请用新的句子试听效果。"
            }.onFailure { toast(it.message ?: "保存失败") }
        } }
        dialog.show()
    }

    private fun createShortReference(role: VoiceRole) {
        val current = pack ?: return
        val samples = runCatching { ReferenceAudio.read(File(current, "references/${role.id}.wav")) }
            .getOrElse { toast(it.message ?: "录音读取失败"); return }
        stop()
        val duration = samples.size / 24000f
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        form.addView(label("从 ${String.format(java.util.Locale.ROOT, "%.1f", duration)} 秒原录音中选 2–6 秒完整短句，减少每次合成的处理量。试听后填写选段实际说的话，另存为新角色。", 14))
        fun seconds(hintText: String, initial: String) = EditText(this).apply {
            hint = hintText; setText(initial)
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val from = seconds("开始秒数", "0")
        val to = seconds("结束秒数", String.format(java.util.Locale.ROOT, "%.2f", minOf(4f, duration)))
        form.addView(label("开始秒数", 14)); form.addView(from)
        form.addView(label("结束秒数", 14)); form.addView(to)
        fun clip(): FloatArray {
            val start = from.text.toString().toFloatOrNull() ?: error("请填写开始秒数")
            val end = to.text.toString().toFloatOrNull() ?: error("请填写结束秒数")
            require(start.isFinite() && end.isFinite() && start >= 0 && end <= duration + .005f && end - start in 1f..6f) { "选段需为 1–6 秒，且不能超出原录音" }
            val first = (start * 24000).toInt()
            val last = (end * 24000).toInt().coerceAtMost(samples.size)
            require(last - first >= 24000) { "选段至少需要 1 秒" }
            return samples.copyOfRange(first, last)
        }
        form.addView(button("试听选段") {
            runCatching {
                val data = clip(); stop()
                val file = File(cacheDir, "short-reference-preview.wav")
                ReferenceAudio.write(file, data); previewFiles.add(file); queuedAudio.add(file); playNext()
            }.onFailure { toast(it.message ?: "试听失败") }
        })
        form.addView(label("原录音文字（供核对）\n${role.referenceText}", 14).apply { setTextIsSelectable(true) })
        val transcript = EditText(this).apply {
            hint = "只填写选中短句的原文，保留语气标点"; minLines = 2
            filters = arrayOf(android.text.InputFilter.LengthFilter(1000))
        }
        form.addView(transcript)
        val dialog = AlertDialog.Builder(this).setTitle("短句参考 · 原角色保留")
            .setView(ScrollView(this).apply { addView(form) }).setNegativeButton("取消", null)
            .setPositiveButton("另存角色", null).create()
        dialog.setOnDismissListener { stop() }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            runCatching {
                val text = transcript.text.toString().trim()
                require(text.isNotEmpty()) { "请根据试听填写选段原文，不能继续使用整段原文" }
                val data = clip()
                val next = role.copy(id = UUID.randomUUID().toString(), name = role.name.take(70) + "·短参考", referenceText = text)
                val file = File(current, "references/${next.id}.wav")
                try {
                    ReferenceAudio.write(file, data)
                    store.saveRoles(current, store.roles(current) + next)
                } catch (e: Exception) { file.delete(); throw e }
                selected = next; renderRoles(); dialog.dismiss()
                status.text = "短参考角色已创建。请先试听；供其他应用调用时，再将其设为系统 TTS 默认音色。"
            }.onFailure { toast(it.message ?: "创建失败") }
        } }
        dialog.show()
    }

    private fun speak() {
        if (busy) return
        val p = pack ?: return toast("请先导入音色包")
        val r = selected ?: return toast("请先上传录音创建角色")
        val text = input.text.toString().trim()
        if (text.isEmpty()) return toast("请输入文字")
        stop()
        val token = generation.get()
        val target = File(cacheDir, "reading-$token.wav")
        busy = true; synthesisDone = false; status.text = "正在准备第一段，生成后开始朗读…"
        worker.execute {
            var part = 0
            val result = runCatching { check(engine.synthesize(p, r, text, target, onSamples = { samples, sampleRate ->
                val file = File(cacheDir, "preview-$token-${part++}.wav")
                check(com.k2fsa.sherpa.onnx.GeneratedAudio(samples, sampleRate).save(file.absolutePath))
                runOnUiThread {
                    if (destroyed || generation.get() != token) file.delete()
                    else { previewFiles.add(file); queuedAudio.add(file); if (player == null) playNext() }
                }
                generation.get() == token
            }) { generation.get() != token }) { "合成已取消或没有生成音频" } }
            runOnUiThread {
                busy = false
                if (destroyed || generation.get() != token) { target.delete(); return@runOnUiThread }
                synthesisDone = true
                result.onSuccess {
                    audio?.delete(); audio = target
                    if (player == null) playNext()
                }.onFailure { target.delete(); stop(); status.text = "合成失败：${it.message}" }
            }
        }
    }

    private fun playNext() {
        if (readingPaused || player != null) return
        val next = queuedAudio.pollFirst()
        if (next == null) { status.text = if (synthesisDone) "朗读完成" else "正在准备下一段…"; return }
        runCatching {
            playingPart++
            player = MediaPlayer().apply {
                setDataSource(next.absolutePath); prepare()
                setOnCompletionListener { it.release(); player = null; playNext() }
                setOnErrorListener { _, _, _ -> stop(); status.text = "音频播放失败"; true }
                start()
            }
            status.text = "正在朗读${if (!synthesisDone) " · 同时准备后续内容" else ""}"
        }.onFailure { stop(); status.text = "播放失败：${it.message}" }
    }

    private fun stop() {
        generation.incrementAndGet(); player?.release(); player = null
        queuedAudio.clear(); previewFiles.forEach { it.delete() }; previewFiles.clear()
        synthesisDone = true; readingPaused = false; playingPart = 0
        if (::progress.isInitialized) progress.progress = 0
    }
    private fun openDocument(code: Int) = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), code)
    private fun createDocument(code: Int, type: String, name: String) = startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type).putExtra(Intent.EXTRA_TITLE, name), code)

    @Deprecated("Legacy activity result API")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        val uri = data?.data ?: return
        if (result != RESULT_OK || busy) return
        stop(); busy = true; status.text = "正在处理文件，请稍候…"
        val exporting = exportPack
        val wav = audio
        worker.execute {
            val outcome = runCatching {
                when (request) {
                    10 -> contentResolver.openInputStream(uri)!!.use { store.importPack(it) }
                    11 -> { contentResolver.openOutputStream(uri, "wt")!!.use { store.exportPack(exporting ?: error("未选择音色包"), it) }; null }
                    12 -> { contentResolver.openOutputStream(uri, "wt")!!.use { output -> (wav ?: error("无音频")).inputStream().use { it.copyTo(output) } }; null }
                    13 -> { val file = File(cacheDir, "reference-${UUID.randomUUID()}.wav"); AudioImporter.import(this, uri, file); pendingReference?.delete(); pendingReference = file; null }
                    else -> null
                }
            }
            runOnUiThread {
                busy = false
                if (destroyed) return@runOnUiThread
                outcome.onSuccess { installed ->
                    if (installed != null) { pack = installed; selected = store.roles(installed).firstOrNull(); renderRoles(); prepareModel() }
                    status.text = when(request) { 10 -> "音色包已安装，可断网使用"; 13 -> "录音已导入，请填写原文"; else -> "导出完成" }
                    if (request == 13) editRole(null)
                }.onFailure { status.text = "操作失败：${it.message}" }
            }
        }
    }

    override fun onDestroy() {
        destroyed = true; stop(); handler.removeCallbacksAndMessages(null)
        worker.execute { engine.close() }; worker.shutdown()
        super.onDestroy()
    }
}
