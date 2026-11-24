package org.noses.towerdefense.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import org.noses.towerdefense.game.SoundCue
import kotlin.random.Random

/**
 * Plays the sound effects, mixing however many are overlapping at the time.
 *
 * The music is a single pre-rendered buffer the hardware loops, which leaves no way to play
 * anything alongside it. So the effects get their own streaming track and their own tiny mixer:
 * a background thread sums every sounding clip into a chunk of PCM and writes it, and the
 * blocking write is what paces the loop. One extra thread, no audio files, any number of
 * overlapping sounds.
 */
class SfxPlayer {
    private companion object {
        /** Frames per write. Smaller means lower latency and more wake-ups. */
        const val CHUNK_FRAMES = 512

        /** Hard cap on overlapping sounds, so a big wave cannot turn into noise. */
        const val MAX_VOICES = 20

        /** How many copies of the *same* sound may start in one frame. */
        const val MAX_PER_BATCH = 2
    }

    /** One clip, part way through being played. */
    private class Voice(val clip: ShortArray, val gain: Float) {
        var position: Int = 0
    }

    private val clips = HashMap<SoundCue, ShortArray>()
    private val voices = ArrayList<Voice>()
    private val random = Random.Default

    /** Scratch for [playAll]; only ever touched on the frame-callback thread. */
    private val startedThisBatch = IntArray(SoundCue.entries.size)

    private var track: AudioTrack? = null
    private var mixer: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    var muted: Boolean = false

    /** Renders every clip. Takes a moment, so call it off the main thread. */
    fun prepare() {
        if (clips.isEmpty()) clips.putAll(Sfx.renderAll())
    }

    fun start() {
        if (running) return
        if (clips.isEmpty()) prepare()

        val minimum = AudioTrack.getMinBufferSize(
            Sfx.SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferBytes = maxOf(minimum, CHUNK_FRAMES * Short.SIZE_BYTES * 2)

        val built = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Sfx.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferBytes)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        built.play()
        track = built
        running = true
        mixer = Thread({ mixLoop(built) }, "sfx-mixer").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Starts [cue] sounding. Safe to call every frame; returns immediately.
     */
    fun play(cue: SoundCue) {
        if (muted || !running) return
        val clip = clips[cue] ?: return
        // A little level variation stops a repeating shot sounding like a stuck machine.
        val gain = 0.85f + random.nextFloat() * 0.3f
        synchronized(voices) {
            if (voices.size >= MAX_VOICES) return
            voices.add(Voice(clip, gain))
        }
    }

    /**
     * Plays a frame's worth of cues, collapsing repeats.
     *
     * Twelve enemies dying in the same blast should sound like a blast, not like twelve of the
     * same clip stacked on top of each other, which just reads as a click.
     */
    fun playAll(cues: List<SoundCue>) {
        if (cues.isEmpty() || muted || !running) return
        java.util.Arrays.fill(startedThisBatch, 0)
        for (cue in cues) {
            val index = cue.ordinal
            if (startedThisBatch[index] >= MAX_PER_BATCH) continue
            startedThisBatch[index]++
            play(cue)
        }
    }

    fun release() {
        running = false
        mixer?.join(500)
        mixer = null
        track?.let {
            it.pause()
            it.flush()
            it.release()
        }
        track = null
        synchronized(voices) { voices.clear() }
    }

    private fun mixLoop(track: AudioTrack) {
        val mix = FloatArray(CHUNK_FRAMES)
        val out = ShortArray(CHUNK_FRAMES)
        while (running) {
            java.util.Arrays.fill(mix, 0f)
            synchronized(voices) {
                var v = 0
                while (v < voices.size) {
                    val voice = voices[v]
                    val clip = voice.clip
                    var i = 0
                    var position = voice.position
                    while (i < CHUNK_FRAMES && position < clip.size) {
                        mix[i] += clip[position] * voice.gain
                        i++
                        position++
                    }
                    voice.position = position
                    if (position >= clip.size) voices.removeAt(v) else v++
                }
            }
            for (i in 0 until CHUNK_FRAMES) {
                val value = mix[i]
                out[i] = when {
                    value >= Short.MAX_VALUE.toFloat() -> Short.MAX_VALUE
                    value <= Short.MIN_VALUE.toFloat() -> Short.MIN_VALUE
                    else -> value.toInt().toShort()
                }
            }
            // Blocking: this is what keeps the loop in step with the audio clock.
            track.write(out, 0, CHUNK_FRAMES)
        }
    }
}
