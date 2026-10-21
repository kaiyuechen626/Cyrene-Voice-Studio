package io.cyrene.voice.core

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.GeneratedAudio
import java.io.File

/** One model in memory, serialized inference. No network client or cloud fallback. */
class LocalVoiceEngine {
    private var engine: OfflineTts? = null
    private var loaded: String? = null

    /** Load while the user selects a role or writes text, rather than at first speech. */
    @Synchronized fun prepare(pack: File) {
        if (loaded != pack.absolutePath) {
            close()
            val model = File(pack, "model")
            fun path(name: String) = File(model, name).absolutePath
            engine = OfflineTts(config = OfflineTtsConfig(
                model = OfflineTtsModelConfig(numThreads = minOf(4, Runtime.getRuntime().availableProcessors()).coerceAtLeast(1), zipvoice = OfflineTtsZipVoiceModelConfig(
                    encoder = path("encoder.int8.onnx"), decoder = path("decoder.int8.onnx"),
                    vocoder = path("vocos_24khz.onnx"), tokens = path("tokens.txt"),
                    dataDir = path("espeak-ng-data"), lexicon = path("lexicon.txt"),
                    // ZipVoice-distill defaults differ from the non-distilled model.
                    guidanceScale = 3.0f,
                )),
            ))
            loaded = pack.absolutePath
        }
    }

    @Synchronized fun synthesize(pack: File, role: VoiceRole, text: String, output: File?, onSamples: ((FloatArray, Int) -> Boolean)? = null, cancelled: () -> Boolean = { false }): Boolean {
        require(text.isNotBlank() && text.length <= 8000) { "请使用 1–8000 字的文本" }
        if (cancelled()) return false
        prepare(pack)
        if (cancelled()) return false
        val reference = ReferenceAudio.read(File(pack, "references/${role.id}.wav"))
        // 1x uses the model's reference-conditioned duration estimate. Do not impose
        // an unrelated global rate or remove 80% of its natural pauses.
        val config = GenerationConfig(referenceAudio = reference, referenceSampleRate = 24000, silenceScale = 1f,
            referenceText = role.referenceText, speed = role.speed, numSteps = 8,
            extra = mapOf("min_char_in_sentence" to "30", "guidance_scale" to if (role.stableProsody) "4.0" else "3.0"))
        val chunks = mutableListOf<FloatArray>()
        var delivered = false
        var aborted = false
        val audio = engine!!.generateWithConfigAndCallback(text, config, NativeSpeechCallback {
            if (cancelled()) { aborted = true; 0 } else {
                val processed = SpeechPauses.compact(it, engine!!.sampleRate())
                delivered = delivered || processed.isNotEmpty()
                if (output != null) chunks.add(processed)
                if (onSamples?.invoke(processed, engine!!.sampleRate()) == false) { aborted = true; 0 } else 1
            }
        })
        if (cancelled() || aborted || !delivered) return false
        if (output == null) return true
        val samples = FloatArray(chunks.sumOf { it.size })
        var offset = 0
        for (chunk in chunks) { chunk.copyInto(samples, offset); offset += chunk.size }
        return GeneratedAudio(samples, audio.sampleRate).save(output.absolutePath)
    }

    @Synchronized fun close() { engine?.release(); engine = null; loaded = null }
}
