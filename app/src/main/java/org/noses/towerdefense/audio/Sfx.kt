package org.noses.towerdefense.audio

import org.noses.towerdefense.game.SoundCue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every sound effect, synthesized the same way the music is: no audio files anywhere.
 *
 * Each clip is a short burst of 16-bit mono PCM, rendered once at startup and then mixed by
 * [SfxPlayer]. Nothing here touches Android, so the clips can be rendered and measured in a unit
 * test.
 *
 * The clips are normalized to deliberately different peaks, and those peaks *are* the mix: a
 * bullet fires every 0.65 seconds and has to sit quietly under everything, while a shell landing
 * should be the loudest thing on the board.
 */
object Sfx {
    const val SAMPLE_RATE = 44100

    private const val TAU = (2.0 * PI).toFloat()

    fun renderAll(): Map<SoundCue, ShortArray> = SoundCue.entries.associateWith { render(it) }

    fun render(cue: SoundCue): ShortArray = when (cue) {
        SoundCue.BULLET_SHOT -> bulletShot()
        SoundCue.BLAST_LAUNCH -> blastLaunch()
        SoundCue.BLAST_IMPACT -> blastImpact()
        SoundCue.LASER_SHOT -> laserShot()
        SoundCue.SQUARE_DOWN -> squareDown()
        SoundCue.TRIANGLE_DOWN -> triangleDown()
        SoundCue.CIRCLE_DOWN -> circleDown()
        SoundCue.UPGRADE -> upgrade()
        SoundCue.RUSH_BONUS -> rushBonus()
    }

    // --- the weapons ----------------------------------------------------------------------

    /** Dry, clipped pop. Short on purpose: it fires constantly and must not become a drone. */
    private fun bulletShot(): ShortArray {
        val mix = buffer(0.10f)
        val noise = Random(11)
        var phase = 0f
        for (i in mix.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val frequency = 180f + 260f * exp(-t * 70f)
            phase += TAU * frequency / SAMPLE_RATE
            val body = if (sin(phase) >= 0f) 1f else -1f
            val click = if (t < 0.0015f) (noise.nextFloat() * 2f - 1f) * (1f - t / 0.0015f) else 0f
            mix[i] = body * exp(-t * 46f) * 0.75f + click * 0.6f
        }
        return finish(mix, 0.26f)
    }

    /** A hollow thump, like something heavy leaving a tube. */
    private fun blastLaunch(): ShortArray {
        val mix = buffer(0.20f)
        val noise = Random(22)
        var phase = 0f
        for (i in mix.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val frequency = 60f + 110f * exp(-t * 26f)
            phase += TAU * frequency / SAMPLE_RATE
            val puff = (noise.nextFloat() * 2f - 1f) * exp(-t * 55f) * 0.3f
            mix[i] = sin(phase) * exp(-t * 15f) + puff
        }
        return finish(mix, 0.30f)
    }

    /** The explosion: a crack, a body of low-passed noise, and a sub-bass thump under it. */
    private fun blastImpact(): ShortArray {
        val mix = buffer(0.55f)
        val noise = Random(33)
        var phase = 0f
        var smoothed = 0f
        for (i in mix.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val raw = noise.nextFloat() * 2f - 1f
            // One-pole low pass: turns hiss into rumble.
            smoothed += 0.09f * (raw - smoothed)
            val frequency = 38f + 45f * exp(-t * 14f)
            phase += TAU * frequency / SAMPLE_RATE
            val body = sin(phase) * exp(-t * 6.5f)
            val crack = raw * exp(-t * 90f) * 0.5f
            mix[i] = body * 0.85f + smoothed * exp(-t * 7.5f) * 3.2f + crack
        }
        return finish(mix, 0.55f)
    }

    /** Classic downward zap. */
    private fun laserShot(): ShortArray {
        val mix = buffer(0.26f)
        val noise = Random(44)
        var phase = 0f
        for (i in mix.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val frequency = 230f + 2000f * exp(-t * 11f)
            phase += TAU * frequency / SAMPLE_RATE
            val body = sin(phase) + 0.35f * sin(phase * 2f)
            val fizz = (noise.nextFloat() * 2f - 1f) * exp(-t * 38f) * 0.18f
            mix[i] = body * exp(-t * 12f) * 0.7f + fizz
        }
        return finish(mix, 0.36f)
    }

    // --- the enemies ----------------------------------------------------------------------

    /**
     * Heavy and blunt: a tank coming apart. The noise is filtered down hard so this reads as a
     * low crunch rather than a hiss - it should be the dullest sound in the game, where the
     * armoured circle is the brightest. Shorter and tighter than the shell explosion.
     */
    private fun squareDown(): ShortArray {
        val mix = buffer(0.34f)
        val noise = Random(55)
        var phase = 0f
        var smoothed = 0f
        for (i in mix.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val raw = noise.nextFloat() * 2f - 1f
            smoothed += 0.06f * (raw - smoothed)
            val frequency = 50f + 45f * exp(-t * 18f)
            phase += TAU * frequency / SAMPLE_RATE
            mix[i] = sin(phase) * exp(-t * 11f) * 1.1f + smoothed * exp(-t * 13f) * 2.6f
        }
        return finish(mix, 0.46f)
    }

    /** A light, high tick. It barely had any health, so it barely makes a sound. */
    private fun triangleDown(): ShortArray {
        val mix = buffer(0.18f)
        val noise = Random(66)
        for (i in mix.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            val tone = sin(TAU * 1480f * t) * exp(-t * 26f) * 0.8f
            val partial = sin(TAU * 1480f * 2.76f * t) * exp(-t * 42f) * 0.25f
            val click = (noise.nextFloat() * 2f - 1f) * exp(-t * 300f) * 0.3f
            mix[i] = tone + partial + click
        }
        return finish(mix, 0.30f)
    }

    /**
     * A metallic clank, because the circle is the armoured one.
     *
     * The partials are deliberately *not* whole-number multiples of each other. Harmonic ratios
     * sound like a musical note; inharmonic ones sound like struck metal.
     */
    private fun circleDown(): ShortArray {
        val mix = buffer(0.50f)
        val noise = Random(77)
        val partials = arrayOf(
            floatArrayOf(523f, 0.62f, 9f),
            floatArrayOf(797f, 0.88f, 10f),
            floatArrayOf(1131f, 0.80f, 12f),
            floatArrayOf(1483f, 0.58f, 15f),
            floatArrayOf(1931f, 0.42f, 18f),
        )
        for (i in mix.indices) {
            val t = i.toFloat() / SAMPLE_RATE
            var value = 0f
            for (partial in partials) {
                value += sin(TAU * partial[0] * t) * partial[1] * exp(-t * partial[2])
            }
            val strike = (noise.nextFloat() * 2f - 1f) * exp(-t * 120f) * 0.25f
            mix[i] = value * 0.45f + strike
        }
        return finish(mix, 0.42f)
    }

    // --- the player -----------------------------------------------------------------------

    /** Three rising bell notes, an A major triad: unmistakably a good thing. */
    private fun upgrade(): ShortArray {
        val mix = buffer(0.55f)
        val notes = floatArrayOf(880.00f, 1108.73f, 1318.51f)
        val offsets = floatArrayOf(0f, 0.07f, 0.14f)
        for (n in notes.indices) {
            val start = (offsets[n] * SAMPLE_RATE).toInt()
            var i = start
            while (i < mix.size) {
                val t = (i - start).toFloat() / SAMPLE_RATE
                val f = notes[n]
                val value = sin(TAU * f * t) +
                    0.30f * sin(TAU * f * 2f * t) +
                    0.12f * sin(TAU * f * 3f * t)
                mix[i] += value * exp(-t * 7f) * 0.5f
                i++
            }
        }
        return finish(mix, 0.52f)
    }

    /**
     * Cash paid for going early: a quick run of bright blips, like coins landing.
     *
     * Deliberately shorter and thinner than the upgrade chime. It can fire at the same moment a
     * wave starts, so it has to sit out of the way rather than compete.
     */
    private fun rushBonus(): ShortArray {
        val mix = buffer(0.34f)
        val notes = floatArrayOf(1318.51f, 1760.00f, 2093.00f, 2637.02f)
        for (n in notes.indices) {
            val start = (n * 0.045f * SAMPLE_RATE).toInt()
            var i = start
            while (i < mix.size) {
                val t = (i - start).toFloat() / SAMPLE_RATE
                val f = notes[n]
                val value = sin(TAU * f * t) + 0.22f * sin(TAU * f * 2f * t)
                mix[i] += value * exp(-t * 26f) * 0.38f
                i++
            }
        }
        return finish(mix, 0.44f)
    }

    // --- plumbing -------------------------------------------------------------------------

    private fun buffer(seconds: Float) = FloatArray((seconds * SAMPLE_RATE).toInt())

    /**
     * Normalizes to [peak] of full scale and converts to 16-bit.
     *
     * Both ends get a very short ramp. Without the tail, a clip whose last sample is loud clicks
     * every time it is played; without the half-millisecond head, a percussive clip that starts
     * at full amplitude pops against the silence before it. Half a millisecond is short enough
     * that the attack still sounds instant.
     */
    private fun finish(mix: FloatArray, peak: Float): ShortArray {
        var loudest = 0f
        for (value in mix) {
            val magnitude = abs(value)
            if (magnitude > loudest) loudest = magnitude
        }
        val scale = if (loudest > 0f) peak / loudest else 0f

        val fadeOut = (0.004f * SAMPLE_RATE).toInt().coerceAtMost(mix.size)
        val fadeOutStart = mix.size - fadeOut
        val fadeIn = (0.0005f * SAMPLE_RATE).toInt().coerceAtMost(mix.size)

        val pcm = ShortArray(mix.size)
        for (i in mix.indices) {
            var value = mix[i] * scale
            if (i < fadeIn) value *= i.toFloat() / fadeIn
            if (i >= fadeOutStart) value *= (mix.size - i).toFloat() / fadeOut
            pcm[i] = (value * Short.MAX_VALUE)
                .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())
                .toInt()
                .toShort()
        }
        return pcm
    }
}
