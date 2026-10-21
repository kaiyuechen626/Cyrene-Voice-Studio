package io.cyrene.voice

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import io.cyrene.voice.core.VoicePackStore
import java.io.File

class CheckVoiceData : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val store = VoicePackStore(File(filesDir, "voice-packs"))
        val ready = store.packs().any { runCatching { store.roles(it).isNotEmpty() }.getOrDefault(false) }
        val data = Intent().putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, if (ready) arrayListOf("zho-CHN", "eng-USA") else arrayListOf())
            .putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, if (ready) arrayListOf() else arrayListOf("zho-CHN", "eng-USA"))
        setResult(if (ready) TextToSpeech.Engine.CHECK_VOICE_DATA_PASS else TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL, data)
        finish()
    }
}
