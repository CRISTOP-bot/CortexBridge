package com.cristopher.cortexbridge.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaConverterOptionsTest {
    @Test
    fun whatsappRemovesAudioByDefault() {
        val options = MediaConverter.Options(MediaConverter.Destination.WHATSAPP)
        assertTrue(options.removeAudio)
    }

    @Test
    fun tiktokCanKeepAudio() {
        val options = MediaConverter.Options(
            destination = MediaConverter.Destination.TIKTOK,
            removeAudio = false
        )
        assertFalse(options.removeAudio)
    }
}
