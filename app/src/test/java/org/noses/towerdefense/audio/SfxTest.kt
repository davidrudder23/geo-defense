package org.noses.towerdefense.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.noses.towerdefense.game.SoundCue
import kotlin.math.abs

class SfxTest {

    private val clips: Map<SoundCue, ShortArray> by lazy { Sfx.renderAll() }

    private fun peakOf(cue: SoundCue): Int = clips.getValue(cue).maxOf { abs(it.toInt()) }

    @Test
    fun `every cue has a clip`() {
        SoundCue.entries.forEach { cue ->
            assertTrue("no clip for $cue", clips.containsKey(cue))
        }
        assertEquals(SoundCue.entries.size, clips.size)
    }

    @Test
    fun `no clip is silent`() {
        clips.forEach { (cue, clip) ->
            assertTrue("$cue was empty", clip.isNotEmpty())
            assertTrue("$cue was silent", clip.maxOf { abs(it.toInt()) } > 1000)
        }
    }

    @Test
    fun `no clip clips`() {
        clips.forEach { (cue, clip) ->
            val pinned = clip.count { abs(it.toInt()) >= Short.MAX_VALUE - 1 }
            assertTrue("$cue pinned $pinned samples at full scale", pinned == 0)
        }
    }

    @Test
    fun `every clip is short enough to feel instant`() {
        clips.forEach { (cue, clip) ->
            val seconds = clip.size.toFloat() / Sfx.SAMPLE_RATE
            assertTrue("$cue lasts ${seconds}s", seconds <= 0.6f)
        }
    }

    @Test
    fun `clips start and end quietly so they do not click`() {
        clips.forEach { (cue, clip) ->
            assertTrue("$cue starts loud", abs(clip.first().toInt()) < 3000)
            assertTrue("$cue ends loud", abs(clip.last().toInt()) < 1500)
        }
    }

    @Test
    fun `rendering is deterministic`() {
        val again = Sfx.render(SoundCue.BLAST_IMPACT)
        assertTrue(clips.getValue(SoundCue.BLAST_IMPACT).contentEquals(again))
    }

    @Test
    fun `the mix is balanced for how often each sound happens`() {
        // The bullet fires constantly, so it must sit under everything else.
        assertTrue(peakOf(SoundCue.BULLET_SHOT) < peakOf(SoundCue.BLAST_IMPACT))
        assertTrue(peakOf(SoundCue.BULLET_SHOT) < peakOf(SoundCue.SQUARE_DOWN))
        assertTrue(peakOf(SoundCue.BULLET_SHOT) < peakOf(SoundCue.UPGRADE))
        // A shell landing is the biggest thing on the board.
        assertTrue(peakOf(SoundCue.BLAST_IMPACT) >= peakOf(SoundCue.LASER_SHOT))
        // The flimsiest enemy makes the smallest noise when it dies.
        assertTrue(peakOf(SoundCue.TRIANGLE_DOWN) < peakOf(SoundCue.SQUARE_DOWN))
    }

    @Test
    fun `the three enemy deaths sound different from each other`() {
        // Compared by where their energy sits: a tank is low, a triangle is high, and the
        // armoured circle rings on much longer than either.
        fun centroidHz(cue: SoundCue): Double {
            val clip = clips.getValue(cue)
            // Crude brightness measure: how often the waveform crosses zero.
            var crossings = 0
            for (i in 1 until clip.size) {
                if ((clip[i - 1] < 0) != (clip[i] < 0)) crossings++
            }
            return crossings.toDouble() / (clip.size.toDouble() / Sfx.SAMPLE_RATE) / 2.0
        }

        val square = centroidHz(SoundCue.SQUARE_DOWN)
        val triangle = centroidHz(SoundCue.TRIANGLE_DOWN)
        val circle = centroidHz(SoundCue.CIRCLE_DOWN)

        assertTrue("square $square should be duller than triangle $triangle", square < triangle)
        assertTrue("circle $circle should be duller than triangle $triangle", circle < triangle)
        assertTrue("square $square should be duller than circle $circle", square < circle)
    }
}
