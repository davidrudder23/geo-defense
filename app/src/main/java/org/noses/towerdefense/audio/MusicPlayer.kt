package org.noses.towerdefense.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/**
 * Plays the generated loop on repeat.
 *
 * The whole loop is written into a static [AudioTrack] buffer once and looped by the hardware, so
 * nothing has to feed audio while the game is running.
 */
class MusicPlayer {
    private var track: AudioTrack? = null
    private var samples: ShortArray? = null

    val playing: Boolean get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    /** Renders the loop if it has not been rendered yet. Call off the main thread. */
    fun prepare() {
        if (samples == null) samples = Music.render()
    }

    fun start() {
        if (track != null) {
            track?.play()
            return
        }
        val pcm = samples ?: Music.render().also { samples = it }
        val built = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(Music.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * Short.SIZE_BYTES)
            .build()
        built.write(pcm, 0, pcm.size)
        // Mono, so one sample is one frame. -1 loops forever.
        built.setLoopPoints(0, pcm.size, -1)
        built.setVolume(0.7f)
        built.play()
        track = built
    }

    fun pause() {
        track?.pause()
    }

    fun release() {
        track?.let {
            it.pause()
            it.flush()
            it.release()
        }
        track = null
    }
}
