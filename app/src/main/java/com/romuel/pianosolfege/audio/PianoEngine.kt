package com.romuel.pianosolfege.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import com.romuel.pianosolfege.music.PianoNote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class PianoEngine(private val context: Context) {
    private data class Sample(val midi: Int, val fileName: String)

    private val sampleRate = 44_100
    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(audioAttributes)
        .build()

    private val samples = listOf(
        Sample(21, "A0.mp3"),
        Sample(24, "C1.mp3"),
        Sample(36, "C2.mp3"),
        Sample(48, "C3.mp3"),
        Sample(60, "C4.mp3"),
        Sample(72, "C5.mp3"),
        Sample(84, "C6.mp3"),
        Sample(96, "C7.mp3"),
    )

    private val soundIds = mutableMapOf<Int, Int>()
    private val sampleDirectory = File(context.filesDir, "salamander_piano")

    @Volatile private var realSamplesReady = false
    @Volatile private var activeStreams: List<Int> = emptyList()
    @Volatile private var currentTrack: AudioTrack? = null

    suspend fun warmUp(onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): Int {
        sampleDirectory.mkdirs()
        val downloaded = mutableListOf<Pair<Sample, File>>()

        samples.forEachIndexed { index, sample ->
            val file = ensureSample(sample)
            if (file != null) downloaded += sample to file
            onProgress(index + 1, samples.size)
        }

        val loaded = loadSamples(downloaded)
        realSamplesReady = loaded == samples.size
        return loaded
    }

    suspend fun play(note: PianoNote, bpm: Int, isPaused: () -> Boolean) {
        val durationMs = ((60_000f / bpm.coerceIn(40, 220)) * note.beats)
            .toLong()
            .coerceIn(30L, 30_000L)

        if (note.isRest) {
            waitPausable(durationMs, isPaused)
            return
        }

        if (realSamplesReady && playRealSamples(note, durationMs, isPaused)) return
        playSynthesized(note, durationMs, isPaused)
    }

    fun stop() {
        activeStreams.forEach { streamId -> runCatching { soundPool.stop(streamId) } }
        activeStreams = emptyList()

        currentTrack?.let { track ->
            runCatching { track.stop() }
            runCatching { track.flush() }
        }
        currentTrack = null
    }

    fun close() {
        stop()
        soundPool.release()
    }

    private suspend fun playRealSamples(
        note: PianoNote,
        durationMs: Long,
        isPaused: () -> Boolean,
    ): Boolean {
        val streamIds = mutableListOf<Int>()
        val volume = (0.92 / sqrt(note.midis.size.toDouble())).toFloat().coerceIn(0.2f, 0.92f)

        note.midis.forEach { midi ->
            val sample = samples.minByOrNull { abs(it.midi - midi) } ?: return false
            val soundId = soundIds[sample.midi] ?: return false
            val rate = 2.0.pow((midi - sample.midi) / 12.0).toFloat().coerceIn(0.5f, 2f)
            val streamId = soundPool.play(soundId, volume, volume, 1, 0, rate)
            if (streamId == 0) {
                streamIds.forEach { soundPool.stop(it) }
                return false
            }
            streamIds += streamId
        }

        activeStreams = streamIds
        try {
            waitPausable(
                durationMs = durationMs,
                isPaused = isPaused,
                onPause = { streamIds.forEach { soundPool.pause(it) } },
                onResume = { streamIds.forEach { soundPool.resume(it) } },
            )
        } finally {
            streamIds.forEach { streamId -> runCatching { soundPool.stop(streamId) } }
            if (activeStreams == streamIds) activeStreams = emptyList()
        }
        return true
    }

    private suspend fun playSynthesized(
        note: PianoNote,
        durationMs: Long,
        isPaused: () -> Boolean,
    ) {
        val frequencies = note.midis.map { midi ->
            440.0 * 2.0.pow((midi - 69) / 12.0)
        }
        val pcm = synthesize(frequencies, durationMs)

        val track = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        currentTrack = track
        try {
            track.write(pcm, 0, pcm.size)
            track.play()
            waitPausable(
                durationMs = durationMs,
                isPaused = isPaused,
                onPause = { runCatching { track.pause() } },
                onResume = { runCatching { track.play() } },
            )
        } finally {
            runCatching { track.stop() }
            track.release()
            if (currentTrack === track) currentTrack = null
        }
    }

    private suspend fun waitPausable(
        durationMs: Long,
        isPaused: () -> Boolean,
        onPause: () -> Unit = {},
        onResume: () -> Unit = {},
    ) {
        var remaining = durationMs
        var paused = false

        while (remaining > 0) {
            if (isPaused()) {
                if (!paused) {
                    onPause()
                    paused = true
                }
                delay(20)
                continue
            }

            if (paused) {
                onResume()
                paused = false
            }

            val step = minOf(20L, remaining)
            delay(step)
            remaining -= step
        }
    }

    private suspend fun ensureSample(sample: Sample): File? = withContext(Dispatchers.IO) {
        val target = File(sampleDirectory, sample.fileName)
        if (target.exists() && target.length() > 1_000L) return@withContext target

        val temporary = File(sampleDirectory, "${sample.fileName}.part")
        runCatching {
            val connection = (URL("$SAMPLE_BASE_URL/${sample.fileName}").openConnection() as HttpURLConnection).apply {
                connectTimeout = 6_000
                readTimeout = 12_000
                requestMethod = "GET"
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                if (temporary.length() <= 1_000L) error("Échantillon audio vide")
                if (target.exists()) target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
                target
            } finally {
                connection.disconnect()
            }
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    private suspend fun loadSamples(files: List<Pair<Sample, File>>): Int =
        suspendCancellableCoroutine { continuation ->
            if (files.isEmpty()) {
                continuation.resume(0)
                return@suspendCancellableCoroutine
            }

            val lock = Any()
            val pending = mutableSetOf<Int>()
            var loaded = 0
            var setupFinished = false

            fun finishIfReady() {
                if (setupFinished && pending.isEmpty() && continuation.isActive) {
                    soundPool.setOnLoadCompleteListener(null)
                    continuation.resume(loaded)
                }
            }

            soundPool.setOnLoadCompleteListener { _, sampleId, status ->
                synchronized(lock) {
                    if (pending.remove(sampleId) && status == 0) loaded += 1
                    finishIfReady()
                }
            }

            synchronized(lock) {
                files.forEach { (sample, file) ->
                    val soundId = soundPool.load(file.absolutePath, 1)
                    if (soundId != 0) {
                        soundIds[sample.midi] = soundId
                        pending += soundId
                    }
                }
                setupFinished = true
                finishIfReady()
            }

            continuation.invokeOnCancellation {
                soundPool.setOnLoadCompleteListener(null)
            }
        }

    private fun synthesize(frequencies: List<Double>, durationMs: Long): ShortArray {
        val count = ((sampleRate * durationMs) / 1000L).toInt().coerceAtLeast(1)
        val output = ShortArray(count)
        val voiceCount = frequencies.size.coerceAtLeast(1)

        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val progress = i.toDouble() / count
            val attack = (t / 0.012).coerceIn(0.0, 1.0)
            val release = ((1.0 - progress) / 0.10).coerceIn(0.0, 1.0)
            val decay = exp(-3.1 * progress)
            val envelope = attack * release * decay

            var mixed = 0.0
            frequencies.forEach { frequency ->
                val tone =
                    1.00 * sin(2.0 * PI * frequency * t) +
                    0.42 * sin(2.0 * PI * frequency * 2.0 * t) +
                    0.20 * sin(2.0 * PI * frequency * 3.0 * t) +
                    0.09 * sin(2.0 * PI * frequency * 4.0 * t)
                mixed += tone / 1.71
            }

            val normalized = (mixed / voiceCount) * envelope * 0.78
            output[i] = (normalized.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return output
    }

    private companion object {
        const val SAMPLE_BASE_URL = "https://tonejs.github.io/audio/salamander"
    }
}
