package com.romuel.pianosolfege.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.romuel.pianosolfege.music.TimedNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Accurate overlapping voices for MusicXML (both hands, different durations).
 * Streamed polyphonic synthesis: memory stays bounded even for long scores.
 */
class PolyphonicScorePlayer {
    private val sampleRate = 44_100
    private val format = AudioFormat.Builder()
        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
        .setSampleRate(sampleRate)
        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
        .build()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    @Volatile private var track: AudioTrack? = null

    suspend fun play(
        notes: List<TimedNote>,
        bpm: Int,
        paused: () -> Boolean,
        onActive: (List<Int>) -> Unit,
    ) = withContext(Dispatchers.Default) {
        if (notes.isEmpty()) return@withContext
        val tempo = bpm.coerceIn(40, 220)
        val beatsPerSecond = tempo / 60.0
        val lastBeat = notes.maxOf { it.startBeats + it.durationBeats }
        require(lastBeat * 60 / tempo < 10 * 60) {
            "La partition dépasse la limite de 10 minutes du lecteur."
        }
        val totalFrames = (lastBeat / beatsPerSecond * sampleRate).toLong()
        val bufferFrames = 2048
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(bufferFrames * 2 * 4)
        val output = AudioTrack(
            attributes, format, minBytes, AudioTrack.MODE_STREAM,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track = output
        var written = 0L
        var wasPaused = false
        var lastProgress = -1L
        val sorted = notes.sortedBy { it.startBeats }
        try {
            output.play()
            while (written < totalFrames) {
                currentCoroutineContext().ensureActive()
                if (paused()) {
                    if (!wasPaused) {
                        output.pause()
                        wasPaused = true
                    }
                    delay(25)
                    continue
                }
                if (wasPaused) {
                    output.play()
                    wasPaused = false
                }
                val frames = min(bufferFrames.toLong(), totalFrames - written).toInt()
                val firstBeat = written.toDouble() / sampleRate * beatsPerSecond
                val lastWindowBeat = (written + frames).toDouble() / sampleRate * beatsPerSecond
                val active = sorted.filter {
                    it.startBeats <= lastWindowBeat &&
                        it.startBeats + it.durationBeats >= firstBeat
                }
                val frequencies = active.map {
                    440.0 * 2.0.pow((it.midi - 69) / 12.0)
                }
                val pcm = ShortArray(frames)
                val norm = sqrt(max(1, active.size).toDouble())
                for (i in 0 until frames) {
                    val beat = (written + i).toDouble() / sampleRate * beatsPerSecond
                    var combined = 0.0
                    for (j in active.indices) {
                        val note = active[j]
                        if (beat < note.startBeats || beat > note.startBeats + note.durationBeats) continue
                        val seconds = (beat - note.startBeats) / beatsPerSecond
                        val remaining = (note.startBeats + note.durationBeats - beat) / beatsPerSecond
                        val attack = min(1.0, seconds / 0.014)
                        val release = min(1.0, remaining / 0.08)
                        val envelope = attack * release * exp(-1.0 * seconds)
                        val phase = 2.0 * PI * frequencies[j] * seconds
                        val tone = sin(phase) + 0.34 * sin(2 * phase) +
                            0.12 * sin(3 * phase)
                        combined += envelope * tone / 1.46
                    }
                    pcm[i] = ((combined / norm * 0.55).coerceIn(-1.0, 1.0) *
                        Short.MAX_VALUE).toInt().toShort()
                }
                var offset = 0
                while (offset < pcm.size) {
                    currentCoroutineContext().ensureActive()
                    val count = output.write(
                        pcm, offset, pcm.size - offset, AudioTrack.WRITE_BLOCKING,
                    )
                    if (count <= 0) error("Échec de sortie audio (AudioTrack = $count).")
                    offset += count
                }
                written += frames
                if (written / (sampleRate / 4) != lastProgress) {
                    lastProgress = written / (sampleRate / 4)
                    val activeMidis = active.filter {
                        firstBeat >= it.startBeats && firstBeat < it.startBeats + it.durationBeats
                    }.map { it.midi }.distinct()
                    withContext(Dispatchers.Main) { onActive(activeMidis) }
                }
            }

            // Let the device play the last buffered frames before releasing it.
            while (output.playbackHeadPosition.toLong() < totalFrames - bufferFrames &&
                output.playState == AudioTrack.PLAYSTATE_PLAYING) {
                currentCoroutineContext().ensureActive()
                delay(20)
            }
        } finally {
            runCatching { output.pause() }
            runCatching { output.flush() }
            runCatching { output.release() }
            if (track === output) track = null
            withContext(Dispatchers.Main) { onActive(emptyList()) }
        }
    }

    fun stop() {
        track?.let { audio ->
            runCatching { audio.pause() }
            runCatching { audio.flush() }
        }
    }
}
