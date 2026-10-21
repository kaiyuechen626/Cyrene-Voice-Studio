package io.cyrene.voice.core

/** JNI looks up invoke(float[]): Integer on the concrete callback class. */
internal class NativeSpeechCallback(private val receive: (FloatArray) -> Int) : (FloatArray) -> Int {
    override fun invoke(samples: FloatArray): Int = receive(samples)
}
