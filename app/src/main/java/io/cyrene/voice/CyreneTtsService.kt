package io.cyrene.voice

import android.media.AudioFormat
import android.speech.tts.*
import io.cyrene.voice.core.LocalVoiceEngine
import io.cyrene.voice.core.VoicePackStore
import io.cyrene.voice.core.VoiceRole
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/** Standard Android TTS engine for any client that supports selecting a TTS engine. */
class CyreneTtsService : TextToSpeechService() {
    private val engine = LocalVoiceEngine()
    private val warmup = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val epoch = AtomicInteger()
    override fun onCreate() {
        super.onCreate()
        warmup.execute {
            runCatching {
                val selected = getSharedPreferences("voice", MODE_PRIVATE).getString("default", null)
                val all = entries()
                (all.firstOrNull { key(it) == selected } ?: all.firstOrNull())?.let { engine.prepare(it.first) }
            }
        }
    }
    private fun entries(): List<Pair<File, VoiceRole>> {
        val store = VoicePackStore(File(filesDir, "voice-packs"))
        return store.packs().flatMap { file -> runCatching { store.roles(file).map { file to it } }.getOrDefault(emptyList()) }
    }
    private fun key(entry: Pair<File, VoiceRole>) = "${entry.first.name}/${entry.second.id}"
    override fun onGetLanguage() = arrayOf("zho", "CHN", "")
    override fun onIsLanguageAvailable(lang: String, country: String, variant: String): Int =
        if (lang in listOf("zh", "zho", "chi", "en", "eng")) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
    override fun onLoadLanguage(lang: String, country: String, variant: String) = onIsLanguageAvailable(lang, country, variant)
    override fun onGetVoices(): List<Voice> = entries().map { Voice(key(it), Locale.SIMPLIFIED_CHINESE, Voice.QUALITY_NORMAL, Voice.LATENCY_VERY_HIGH, false, setOf(it.second.name)) }
    override fun onGetDefaultVoiceNameFor(lang: String, country: String, variant: String): String? {
        val all = entries()
        val selected = getSharedPreferences("voice", MODE_PRIVATE).getString("default", null)
        return all.firstOrNull { key(it) == selected }?.let { key(it) } ?: all.firstOrNull()?.let { key(it) }
    }
    override fun onIsValidVoiceName(name: String) = if (entries().any { key(it) == name }) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    override fun onLoadVoice(name: String) = onIsValidVoiceName(name)
    override fun onStop() { epoch.incrementAndGet() }
    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        val token = epoch.get()
        try {
            val selected = request.voiceName?.takeIf { it.isNotBlank() } ?: onGetDefaultVoiceNameFor(request.language, request.country, request.variant)
            val entry = entries().firstOrNull { key(it) == selected } ?: error("请先创建克隆角色")
            var started = false
            var failed = false
            val speed = (entry.second.speed * request.speechRate / 100f).coerceIn(.5f, 2f)
            val ok = engine.synthesize(entry.first, entry.second.copy(speed = speed), request.charSequenceText.toString(), null, onSamples = { samples, sampleRate ->
                if (!started) { failed = callback.start(sampleRate, AudioFormat.ENCODING_PCM_16BIT, 1) != TextToSpeech.SUCCESS; started = true }
                if (!failed && epoch.get() == token) {
                    val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    samples.forEach { bytes.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
                    var offset = 0
                    val limit = callback.maxBufferSize.coerceAtLeast(2)
                    while (offset < bytes.array().size && !failed && epoch.get() == token) {
                        val n = minOf(limit, bytes.array().size - offset)
                        failed = callback.audioAvailable(bytes.array(), offset, n) != TextToSpeech.SUCCESS
                        offset += n
                    }
                }
                !failed && epoch.get() == token
            }) { epoch.get() != token || failed }
            if (ok && !failed && epoch.get() == token) callback.done() else callback.error()
        } catch (_: Exception) { callback.error() }
    }
    override fun onDestroy() { onStop(); warmup.execute { engine.close() }; warmup.shutdown(); super.onDestroy() }
}
