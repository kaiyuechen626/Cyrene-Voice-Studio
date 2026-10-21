package io.cyrene.voice.core

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pack references use canonical 24 kHz mono PCM16 WAV, 1–30 seconds. */
object ReferenceAudio {
    fun read(file: File): FloatArray {
        require(file.length() in (44L + 24000 * 2)..(44L + 24000 * 30 * 2)) { "参考录音需为 1–30 秒" }
        val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(b.getInt(0) == 0x46464952 && b.getInt(8) == 0x45564157 && b.getInt(12) == 0x20746d66 && b.getInt(16) == 16 && b.getShort(20).toInt() == 1 && b.getShort(22).toInt() == 1 && b.getInt(24) == 24000 && b.getShort(34).toInt() == 16 && b.getInt(36) == 0x61746164 && b.getInt(40) == b.limit() - 44) { "参考录音格式无效" }
        b.position(44)
        return FloatArray(b.remaining() / 2) { b.short / 32768f }
    }
    fun write(file: File, samples: FloatArray) {
        require(samples.size in 24000..720000) { "请导入 1–30 秒的清晰单人录音" }
        require(samples.any { kotlin.math.abs(it) > 0.003f }) { "参考录音接近静音，请换一段录音" }
        val b = ByteBuffer.allocate(44 + samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + samples.size * 2).put("WAVEfmt ".toByteArray()).putInt(16)
        b.putShort(1).putShort(1).putInt(24000).putInt(48000).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(samples.size * 2)
        samples.forEach { b.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
        file.parentFile!!.mkdirs(); file.writeBytes(b.array())
    }
}
