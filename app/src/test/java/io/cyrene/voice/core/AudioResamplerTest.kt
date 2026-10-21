package io.cyrene.voice.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioResamplerTest {
    @Test fun downsamplingSuppressesFrequenciesAboveTheNewNyquistLimit() {
        fun rmsAfterDownsampling(hz: Int): Double {
            val source = FloatArray(48000) { (.5 * sin(2 * PI * hz * it / 48000)).toFloat() }
            val result = AudioResampler.to24k(source, 48000)
            assertEquals(24000, result.size)
            return sqrt(result.sliceArray(300 until 23700).map { it.toDouble() * it }.average())
        }
        assertTrue(rmsAfterDownsampling(1000) > .3)
        assertTrue(rmsAfterDownsampling(18000) < .01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteRecordingSamples() {
        AudioResampler.to24k(floatArrayOf(Float.NaN), 48000)
    }
}
