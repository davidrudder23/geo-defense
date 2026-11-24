package org.noses.towerdefense.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.noses.towerdefense.game.SoundCue
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Writes the soundtrack out as a WAV so it can be listened to without installing the game.
 *
 * Run `./gradlew :app:test` and open `app/build/music.wav`. Tuning a tune you cannot hear is
 * guesswork, so this is the loop you actually ship, not a re-creation of it.
 */
class MusicExportTest {

    @Test
    fun `exports the loop as a wav file`() {
        val samples = Music.render()
        val target = File("build/music.wav")
        target.parentFile?.mkdirs()
        target.writeBytes(wavBytes(samples, Music.SAMPLE_RATE))

        assertTrue(target.exists())
        // 44 byte header plus two bytes a sample.
        assertTrue(target.length() == 44L + samples.size * 2L)
    }

    @Test
    fun `exports every sound effect as a wav file`() {
        val target = File("build/sfx")
        target.mkdirs()
        SoundCue.entries.forEach { cue ->
            val clip = Sfx.render(cue)
            val file = File(target, cue.name.lowercase() + ".wav")
            file.writeBytes(wavBytes(clip, Sfx.SAMPLE_RATE))
            assertTrue(file.length() > 44L)
        }
        assertEquals(SoundCue.entries.size, target.listFiles()?.size)
    }

    /** Wraps 16-bit mono PCM in the smallest valid WAV header. */
    private fun wavBytes(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)

        buffer.put("RIFF".toByteArray())
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray())

        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)                       // PCM header size
        buffer.putShort(1)                      // uncompressed
        buffer.putShort(1)                      // mono
        buffer.putInt(sampleRate)
        buffer.putInt(sampleRate * 2)           // bytes per second
        buffer.putShort(2)                      // bytes per frame
        buffer.putShort(16)                     // bits per sample

        buffer.put("data".toByteArray())
        buffer.putInt(dataBytes)
        samples.forEach { buffer.putShort(it) }

        return buffer.array()
    }
}
