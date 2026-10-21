package io.cyrene.voice.core

import org.junit.Assert.*
import org.junit.Test

class SpeechPausesTest {
    private val speech = FloatArray(2400) { .2f }

    @Test fun keepsOrdinaryPhrasePausesAndSpeechUnchanged() {
        val input = speech + FloatArray(24000 / 2) + speech
        assertArrayEquals(input, SpeechPauses.compact(input, 24000), 0f)
    }

    @Test fun shortensOnlyLongSilenceWithoutChangingTheSpeechSamples() {
        val output = SpeechPauses.compact(speech + FloatArray(24000) + speech, 24000)
        assertEquals(4800 + 15600, output.size)
        assertArrayEquals(speech, output.copyOfRange(0, 2400), 0f)
        assertArrayEquals(speech, output.copyOfRange(output.size - 2400, output.size), 0f)
    }
}
