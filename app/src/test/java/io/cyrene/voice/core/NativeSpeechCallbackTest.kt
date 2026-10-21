package io.cyrene.voice.core

import org.junit.Assert.*
import org.junit.Test

class NativeSpeechCallbackTest {
    @Test fun concreteCallbackExposesTheBoxedSignatureRequiredByJni() {
        val samples = floatArrayOf(0.2f, -0.2f)
        var delivered: FloatArray? = null
        val callback = NativeSpeechCallback { delivered = it; 1 }
        val method = callback.javaClass.getDeclaredMethod("invoke", FloatArray::class.java)
        assertEquals(Integer::class.java, method.returnType)
        assertEquals(1, method.invoke(callback, samples))
        assertSame(samples, delivered)
    }

    @Test fun cancellationIsReturnedThroughTheNativeSignature() {
        val callback = NativeSpeechCallback { 0 }
        val method = callback.javaClass.getDeclaredMethod("invoke", FloatArray::class.java)
        assertEquals(0, method.invoke(callback, floatArrayOf(0f)))
    }
}
