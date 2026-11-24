package org.noses.towerdefense.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The soundtrack, synthesized from scratch: a 16-bar early-techno loop at 128 BPM in A minor.
 *
 * Nothing here touches Android, so the loop can be rendered and inspected in a unit test. The
 * result is one seamless loop of 16-bit mono PCM, meant to be handed to [MusicPlayer].
 *
 * The arrangement, one chord per bar over Am - F - C - G:
 *   - kick      four on the floor
 *   - hats      closed on every off-16th, open on the offbeats
 *   - clap      backbeat, from bar 4
 *   - bass      octave-jumping 16ths on the chord root
 *   - lead      8th-note arpeggio of the chord triad, from bar 4, an octave up for the last 4 bars
 */
object Music {
    const val SAMPLE_RATE = 44100
    const val BPM = 128
    const val STEPS_PER_BAR = 16
    const val BARS = 16

    /** Seconds per 16th note. */
    private const val STEP_SECONDS = 60f / BPM / 4f

    /** Chord roots for Am - F - C - G, down in the bass register. */
    private val BASS_ROOTS = floatArrayOf(110.00f, 87.31f, 130.81f, 98.00f)

    /** The triad of each chord, up where the lead sits. */
    private val LEAD_TRIADS = arrayOf(
        floatArrayOf(440.00f, 523.25f, 659.25f), // A minor
        floatArrayOf(349.23f, 440.00f, 523.25f), // F major
        floatArrayOf(523.25f, 659.25f, 783.99f), // C major
        floatArrayOf(392.00f, 493.88f, 587.33f), // G major
    )

    /** Which 16ths of the bar the bass plays. */
    private val BASS_STEPS = intArrayOf(0, 3, 6, 8, 11, 14)

    val loopSeconds: Float get() = BARS * STEPS_PER_BAR * STEP_SECONDS

    /** Renders the whole loop. Takes a moment, so call it off the main thread. */
    fun render(): ShortArray {
        val totalSamples = (loopSeconds * SAMPLE_RATE).toInt()
        val mix = FloatArray(totalSamples)
        // Fixed seed, so the noise in the hats and claps is the same every run.
        val noise = Random(0x5EEDL)

        var leadStep = 0
        for (step in 0 until BARS * STEPS_PER_BAR) {
            val bar = step / STEPS_PER_BAR
            val inBar = step % STEPS_PER_BAR
            val at = (step * STEP_SECONDS * SAMPLE_RATE).toInt()
            val chord = bar % 4

            if (inBar % 4 == 0) kick(mix, at)

            if (inBar % 2 == 1) hat(mix, at, noise, gain = 0.13f, decay = 70f)
            if (inBar % 4 == 2) hat(mix, at, noise, gain = 0.17f, decay = 16f)

            if (bar >= 4 && (inBar == 4 || inBar == 12)) clap(mix, at, noise)

            if (inBar in BASS_STEPS) {
                val index = BASS_STEPS.indexOf(inBar)
                // Every other note jumps an octave: the engine of a techno bassline.
                val octave = if (index % 2 == 0) 1f else 2f
                bass(mix, at, BASS_ROOTS[chord] * octave)
            }

            if (bar >= 4 && inBar % 2 == 0) {
                val triad = LEAD_TRIADS[chord]
                val octave = if (bar >= 12) 2f else 1f
                lead(mix, at, triad[leadStep % triad.size] * octave)
                leadStep++
            }
        }

        return toPcm(mix)
    }

    // --- voices ---------------------------------------------------------------------------

    /** Sine with a fast downward pitch sweep: the classic synthesized kick drum. */
    private fun kick(out: FloatArray, at: Int, gain: Float = 0.95f) {
        val length = (0.26f * SAMPLE_RATE).toInt()
        var phase = 0f
        for (i in 0 until length) {
            val index = at + i
            if (index >= out.size) return
            val t = i.toFloat() / SAMPLE_RATE
            val frequency = 48f + 90f * exp(-t * 30f)
            phase += (2f * PI.toFloat() * frequency) / SAMPLE_RATE
            out[index] += sin(phase) * exp(-t * 16f) * gain
        }
    }

    /** A short burst of noise. A slow [decay] gives an open hat, a fast one a closed hat. */
    private fun hat(out: FloatArray, at: Int, noise: Random, gain: Float, decay: Float) {
        val length = (0.22f * SAMPLE_RATE).toInt()
        var previous = 0f
        for (i in 0 until length) {
            val index = at + i
            if (index >= out.size) return
            val t = i.toFloat() / SAMPLE_RATE
            val raw = noise.nextFloat() * 2f - 1f
            // Crude high pass, so it sits above the kick instead of muddying it.
            val bright = raw - previous
            previous = raw
            out[index] += bright * exp(-t * decay) * gain
        }
    }

    /** Three noise bursts a few milliseconds apart, which is what makes a clap sound like one. */
    private fun clap(out: FloatArray, at: Int, noise: Random, gain: Float = 0.3f) {
        val spacing = (0.009f * SAMPLE_RATE).toInt()
        for (burst in 0 until 3) {
            val start = at + burst * spacing
            val length = (0.18f * SAMPLE_RATE).toInt()
            for (i in 0 until length) {
                val index = start + i
                if (index >= out.size) break
                val t = i.toFloat() / SAMPLE_RATE
                val raw = noise.nextFloat() * 2f - 1f
                out[index] += raw * exp(-t * 26f) * gain * (if (burst == 2) 1f else 0.5f)
            }
        }
    }

    /** Square wave, short and punchy. */
    private fun bass(out: FloatArray, at: Int, frequency: Float, gain: Float = 0.3f) {
        val length = (STEP_SECONDS * 2.2f * SAMPLE_RATE).toInt()
        for (i in 0 until length) {
            val index = at + i
            if (index >= out.size) return
            val t = i.toFloat() / SAMPLE_RATE
            val square = if (sin(2f * PI.toFloat() * frequency * t) >= 0f) 1f else -1f
            // Quick attack ramp stops each note from clicking.
            val attack = (t * 400f).coerceAtMost(1f)
            out[index] += square * attack * exp(-t * 7f) * gain
        }
    }

    /** Detuned saw pair: thin, bright, and very 1990. */
    private fun lead(out: FloatArray, at: Int, frequency: Float, gain: Float = 0.27f) {
        val length = (STEP_SECONDS * 2.6f * SAMPLE_RATE).toInt()
        for (i in 0 until length) {
            val index = at + i
            if (index >= out.size) return
            val t = i.toFloat() / SAMPLE_RATE
            val a = saw(frequency * t)
            val b = saw(frequency * 1.005f * t)
            val attack = (t * 300f).coerceAtMost(1f)
            out[index] += (a + b) * 0.5f * attack * exp(-t * 6f) * gain
        }
    }

    /** Rising saw from -1 to 1 over each cycle; [cycles] is the phase in whole turns. */
    private fun saw(cycles: Float): Float {
        val fraction = cycles - kotlin.math.floor(cycles)
        return fraction * 2f - 1f
    }

    // --- output ---------------------------------------------------------------------------

    /** Normalizes the mix to a safe level and converts it to 16-bit samples. */
    private fun toPcm(mix: FloatArray): ShortArray {
        var peak = 0f
        for (value in mix) {
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
        }
        val scale = if (peak > 0f) 0.88f / peak else 0f
        val pcm = ShortArray(mix.size)
        for (i in mix.indices) {
            val scaled = (mix[i] * scale * Short.MAX_VALUE).coerceIn(
                Short.MIN_VALUE.toFloat(),
                Short.MAX_VALUE.toFloat(),
            )
            pcm[i] = scaled.toInt().toShort()
        }
        return pcm
    }
}
