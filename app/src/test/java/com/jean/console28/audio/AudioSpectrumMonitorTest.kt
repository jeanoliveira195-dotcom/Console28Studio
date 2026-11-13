package com.jean.console28.audio

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class AudioSpectrumMonitorTest {
    @Test fun silenceIsSilent() {
        val meter = AudioSpectrumMonitor()
        val empty = ShortArray(8192)
        meter.process(empty, empty.size)
        assertTrue(meter.snapshot().bands.all { it == 0f })
        assertEquals(0f, meter.snapshot().left, 0.00001f)
    }

    @Test fun respondsToBassOnStereo() {
        val meter = AudioSpectrumMonitor()
        val pcm = ShortArray(8192*2) { index ->
            (sin(2.0*PI*100.0*(index/2)/48000.0)*17000).toInt().toShort()
        }
        meter.process(pcm,pcm.size)
        val reading=meter.snapshot()
        assertTrue("no 100 Hz energy", reading.bands[2] > 0.3f)
        assertTrue("unexpected high-frequency energy",reading.bands[2] > reading.bands[14])
        assertTrue("no left meter activity",reading.left > 0.2f)
        assertTrue("no right meter activity",reading.right > 0.2f)
    }

    @Test fun monoAndReset() {
        val meter=AudioSpectrumMonitor()
        meter.configure(44100,1)
        val x=ShortArray(8192) { i ->
            (sin(2.0*PI*2100.0*i/44100.0)*16000).toInt().toShort()
        }
        meter.process(x,x.size)
        assertTrue(meter.snapshot().bands[10]>0.2f)
        assertEquals(meter.snapshot().left, meter.snapshot().right, 0.00001f)
        meter.reset()
        assertTrue(meter.snapshot().bands.all{it==0f})
    }
}
