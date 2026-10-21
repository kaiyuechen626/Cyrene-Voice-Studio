package io.cyrene.voice

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import io.cyrene.voice.core.ReferenceAudio
import io.cyrene.voice.core.AudioResampler
import java.io.File
import java.nio.ByteOrder

/** Decode local WAV/MP3/M4A using Android codecs; downmix and resample to the pack format. */
object AudioImporter {
    fun import(context: Context, uri: Uri, output: File) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("文件中没有可解码的音频")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            if (format.containsKey(MediaFormat.KEY_DURATION)) require(format.getLong(MediaFormat.KEY_DURATION) <= 31_000_000L) { "请先截取 3–30 秒的单人录音" }
            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var encoding = AudioFormat.ENCODING_PCM_16BIT
            require(sampleRate in 8000..192000 && channels in 1..8) { "不支持此录音格式" }
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec = decoder
            decoder.configure(format, null, null, 0); decoder.start()
            val samples = ArrayList<Float>()
            val info = MediaCodec.BufferInfo()
            var ended = false
            var done = false
            val deadline = System.nanoTime() + 60_000_000_000L
            while (!done) {
                check(System.nanoTime() < deadline) { "音频解码超时" }
                if (!ended) {
                    val index = decoder.dequeueInputBuffer(10000)
                    if (index >= 0) {
                        val buffer = decoder.getInputBuffer(index)!!
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); ended = true }
                        else { decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val index = decoder.dequeueOutputBuffer(info, 10000)
                if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = decoder.outputFormat
                    sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    encoding = if (out.containsKey(MediaFormat.KEY_PCM_ENCODING)) out.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    require(sampleRate in 8000..192000 && channels in 1..8 && encoding in listOf(AudioFormat.ENCODING_PCM_16BIT, AudioFormat.ENCODING_PCM_FLOAT)) { "不支持此 PCM 格式" }
                } else if (index >= 0) {
                    val buffer = decoder.getOutputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                    val bytes = if (encoding == AudioFormat.ENCODING_PCM_FLOAT) 4 else 2
                    while (buffer.remaining() >= bytes * channels) {
                        var value = 0f
                        repeat(channels) { value += if (encoding == AudioFormat.ENCODING_PCM_FLOAT) buffer.float else buffer.short / 32768f }
                        samples.add((value / channels).coerceIn(-1f, 1f))
                        require(samples.size <= sampleRate * 30) { "请先截取 3–30 秒的单人录音" }
                    }
                    done = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    decoder.releaseOutputBuffer(index, false)
                }
            }
            val count = (samples.size.toLong() * 24000 / sampleRate).toInt()
            require(count in 24000..720000) { "录音长度需为 1–30 秒，建议使用 2–6 秒的完整短句" }
            val resampled = AudioResampler.to24k(samples.toFloatArray(), sampleRate)
            ReferenceAudio.write(output, resampled)
        } finally { codec?.release(); extractor.release() }
    }
}
