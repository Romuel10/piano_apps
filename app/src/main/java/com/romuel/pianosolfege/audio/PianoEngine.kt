package com.romuel.pianosolfege.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.romuel.pianosolfege.music.PianoNote
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

class PianoEngine {
    private val sampleRate = 44_100
    @Volatile private var currentTrack: AudioTrack? = null

    suspend fun play(note: PianoNote, bpm: Int) {
        val durationMs = ((60_000f / bpm.coerceIn(40, 220)) * note.beats)
            .toLong()
            .coerceAtLeast(30L)

        if (note.isRest) {
            delay(durationMs)
            return
        }

        val frequency = 440.0 * 2.0.pow((note.midi!! - 69) / 12.0)
        val samples = synthesize(frequency, durationMs)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        currentTrack = track
        try {
            track.write(samples, 0, samples.size)
            track.play()
            delay(durationMs)
        } finally {
            runCatching { track.stop() }
            track.release()
            if (currentTrack === track) currentTrack = null
        }
    }

    fun stop() {
        val track = currentTrack ?: return
        runCatching { track.stop() }
        runCatching { track.flush() }
        currentTrack = null
    }

    private fun synthesize(frequency: Double, durationMs: Long): ShortArray {
        val count = ((sampleRate * durationMs) / 1000L).toInt().coerceAtLeast(1)
        val output = ShortArray(count)

        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / count

            val attack = (t / 0.012).coerceIn(0.0, 1.0)
            val release = ((1.0 - progress) / 0.10).coerceIn(0.0, 1.0)
            val decay = exp(-3.1 * progress)
            val envelope = attack * release * decay

            val tone =
                1.00 * sin(2.0 * PI * frequency * t) +
                0.42 * sin(2.0 * PI * frequency * 2.0 * t) +
                0.20 * sin(2.0 * PI * frequency * 3.0 * t) +
                0.09 * sin(2.0 * PI * frequency * 4.0 * t)

            val normalized = (tone / 1.71) * envelope * 0.75
            output[i] = (normalized.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }

        return output
    }
}
