package io.cyrene.voice.core

import kotlin.math.sqrt

/** Preserve normal phrasing, only shorten long, near-silent gaps. Never shift pitch. */
object SpeechPauses {
    fun compact(samples: FloatArray, sampleRate: Int): FloatArray {
        if (samples.isEmpty()) return samples
        val frame = (sampleRate / 100).coerceAtLeast(1) // 10 ms
        val count = (samples.size + frame - 1) / frame
        val rms = DoubleArray(count) { n ->
            val end = minOf(samples.size, (n + 1) * frame)
            var energy = 0.0
            for (i in n * frame until end) energy += samples[i].toDouble() * samples[i]
            sqrt(energy / (end - n * frame))
        }
        // A conservative threshold protects quiet consonants and breaths.
        val threshold = minOf(.0015, (rms.maxOrNull() ?: 0.0) * .015)
        if (threshold <= 0.0) return samples
        val result = FloatArray(samples.size)
        var written = 0
        var n = 0
        fun copy(from: Int, to: Int) {
            if (to > from) { samples.copyInto(result, written, from, to); written += to - from }
        }
        while (n < count) {
            val start = n
            if (rms[n] >= threshold) {
                while (n < count && rms[n] >= threshold) n++
                copy(start * frame, minOf(n * frame, samples.size))
            } else {
                while (n < count && rms[n] < threshold) n++
                val from = start * frame
                val to = minOf(n * frame, samples.size)
                val limit = when {
                    start == 0 -> sampleRate * 80 / 1000
                    n == count -> sampleRate * 160 / 1000
                    to - from > sampleRate * 9 / 10 -> sampleRate * 65 / 100
                    else -> to - from
                }
                if (to - from <= limit) copy(from, to)
                else if (start == 0) copy(to - limit, to)
                else if (n == count) copy(from, from + limit)
                else { copy(from, from + limit / 2); copy(to - (limit - limit / 2), to) }
            }
        }
        return result.copyOf(written)
    }
}
