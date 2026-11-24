package org.noses.towerdefense.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MusicTest {

    private val loop: ShortArray by lazy { Music.render() }

    @Test
    fun `the loop is the length the tempo implies`() {
        // 16 bars of 16 sixteenths at 128 BPM is 30 seconds.
        assertEquals(30f, Music.loopSeconds, 0.01f)
        assertEquals((Music.loopSeconds * Music.SAMPLE_RATE).toInt(), loop.size)
    }

    @Test
    fun `the loop is not silent`() {
        val peak = loop.maxOf { abs(it.toInt()) }
        assertTrue("peak was $peak", peak > Short.MAX_VALUE / 2)
    }

    @Test
    fun `the loop is normalized without clipping`() {
        val peak = loop.maxOf { abs(it.toInt()) }
        // Normalized to 0.88 of full scale, so it is loud but has headroom.
        assertTrue("peak was $peak", peak <= Short.MAX_VALUE.toInt())
        assertTrue("peak was $peak", peak > (Short.MAX_VALUE * 0.8f).toInt())
        // Nothing should sit pinned at the rail for long, which is what clipping sounds like.
        val pinned = loop.count { abs(it.toInt()) >= Short.MAX_VALUE - 1 }
        assertTrue("$pinned samples were pinned at full scale", pinned < loop.size / 1000)
    }

    @Test
    fun `rendering is deterministic`() {
        // The noise is seeded, so two renders match. That keeps the loop seamless and the
        // tests repeatable.
        val again = Music.render()
        assertEquals(loop.size, again.size)
        assertTrue(loop.contentEquals(again))
    }

    @Test
    fun `the loop joins up without a click`() {
        // A loud sample right at either end would pop on every repeat.
        val head = abs(loop.first().toInt())
        val tail = abs(loop.last().toInt())
        val headroom = (Short.MAX_VALUE * 0.25f).toInt()
        assertTrue("first sample was $head", head < headroom)
        assertTrue("last sample was $tail", tail < headroom)
    }

    @Test
    fun `the arrangement builds up rather than starting at full tilt`() {
        // The lead comes in at bar 4, so the second half of the loop should be busier than the
        // first: a flat loop would mean the arrangement never happened.
        val quarter = loop.size / 4
        fun energy(from: Int, to: Int): Double =
            (from until to).sumOf { abs(loop[it].toInt()).toDouble() } / (to - from)

        val opening = energy(0, quarter)
        val later = energy(quarter * 2, quarter * 3)
        assertTrue("opening $opening vs later $later", later > opening)
    }

    @Test
    fun `there is a kick on every beat`() {
        // Four on the floor: a loud low transient every half second at 128 BPM. Check the
        // opening bar has an energy spike at each beat.
        val samplesPerBeat = (60f / Music.BPM * Music.SAMPLE_RATE).toInt()
        val window = samplesPerBeat / 8
        repeat(4) { beat ->
            val start = beat * samplesPerBeat
            val onBeat = (start until start + window).maxOf { abs(loop[it].toInt()) }
            // Just before the next beat the kick has decayed away.
            val offBeat = ((start + samplesPerBeat - window) until (start + samplesPerBeat))
                .maxOf { abs(loop[it].toInt()) }
            assertTrue("beat $beat was not louder than the gap before the next",
                onBeat > offBeat)
        }
    }
}
