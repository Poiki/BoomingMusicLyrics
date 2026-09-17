package com.mardous.booming.playback.processor

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class BalanceAudioProcessorTest {
    private fun processor(channels: Int = 2) = BalanceAudioProcessor().apply {
        configure(AudioProcessor.AudioFormat(96000, channels, C.ENCODING_PCM_16BIT))
        flush(AudioProcessor.StreamMetadata.DEFAULT)
    }

    private fun process(processor: BalanceAudioProcessor, vararg samples: Int): List<Int> {
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it.toShort()) }
        input.flip()
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        val output = processor.output.order(ByteOrder.nativeOrder())
        return buildList { while (output.hasRemaining()) add(output.short.toInt()) }
    }

    @Test fun `neutral balance preserves every 16 bit sample including extremes`() {
        val samples = intArrayOf(-32768, 32767, -1, 1, 0, 12345)
        assertEquals(samples.toList(), process(processor(), *samples))
    }

    @Test fun `balance can change without restarting the audio stream`() {
        val processor = processor()
        assertEquals(listOf(1000, -1000), process(processor, 1000, -1000))
        processor.setBalance(0.5f, 1f)
        assertEquals(listOf(500, -1000), process(processor, 1000, -1000))
        processor.setBalance(1f, 1f)
        assertEquals(listOf(1000, -1000), process(processor, 1000, -1000))
    }

    @Test fun `mono gain and clipping remain bounded`() {
        val processor = processor(1)
        processor.setBalance(2f, 2f)
        assertEquals(listOf(32767, -32768), process(processor, 30000, -30000))
    }
}
