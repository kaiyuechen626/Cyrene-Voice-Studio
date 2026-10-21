package io.cyrene.voice.core

import kotlin.math.*

/** Windowed-sinc low-pass resampling. Linear interpolation aliases high frequencies when downsampling. */
object AudioResampler {
    fun to24k(input: FloatArray, sourceRate: Int): FloatArray {
        require(sourceRate in 8000..192000 && input.isNotEmpty())
        require(input.all { it.isFinite() }) { "录音包含无效音频数据" }
        if (sourceRate == 24000) return input.copyOf()
        val ratio = sourceRate / 24000.0
        val cutoff = .475 * min(1.0, 1.0 / ratio)
        val radius = ceil(20 * max(1.0, ratio)).toInt()
        val phases = 512
        val kernels = Array(phases) { phase ->
            val fraction = phase.toDouble() / phases
            val kernel = DoubleArray(radius * 2 + 1) { j ->
                val distance = j - radius - fraction
                val x = 2 * cutoff * distance
                val sinc = if (abs(x) < 1e-10) 1.0 else sin(PI * x) / (PI * x)
                val window = if (abs(distance) > radius) 0.0 else .42 + .5 * cos(PI * distance / radius) + .08 * cos(2 * PI * distance / radius)
                2 * cutoff * sinc * window
            }
            val sum = kernel.sum()
            FloatArray(kernel.size) { (kernel[it] / sum).toFloat() }
        }
        return FloatArray((input.size.toLong() * 24000 / sourceRate).toInt()) { i ->
            val position = i * ratio
            val base = floor(position).toInt()
            val phase = ((position - base) * phases).roundToInt().coerceIn(0, phases - 1)
            val kernel = kernels[phase]
            var value = 0.0
            for (j in kernel.indices) value += input[(base + j - radius).coerceIn(0, input.lastIndex)] * kernel[j]
            value.toFloat().coerceIn(-1f, 1f)
        }
    }
}
