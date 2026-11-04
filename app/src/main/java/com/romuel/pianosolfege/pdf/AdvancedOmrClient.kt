package com.romuel.pianosolfege.pdf

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Explicit opt-in HTTPS upload to a user's private OMR service. */
internal object AdvancedOmrClient {
    private const val MAX_BYTES = 12 * 1024 * 1024

    fun normalizeUrl(raw: String): String {
        val input = raw.trim().trimEnd('/')
        require(input.isNotBlank()) { "Indique l'adresse HTTPS de ton serveur OMR." }
        val url = URL(input)
        require(url.protocol.equals("https", ignoreCase = true)) {
            "Un serveur HTTPS est obligatoire pour protéger les partitions."
        }
        require(url.host.isNotBlank() && url.userInfo == null && url.query == null && url.ref == null) {
            "Adresse HTTPS invalide (sans identifiants ni paramètres)."
        }
        val path = url.path
        require(path.isBlank() || path == "/" || path == "/v1/recognize") {
            "L'adresse doit finir par /v1/recognize ou par le domaine."
        }
        return if (path == "/v1/recognize") input else input + "/v1/recognize"
    }

    suspend fun recognize(context: Context, uri: Uri, endpoint: String, token: String): ByteArray =
        withContext(Dispatchers.IO) {
            val safeUrl = normalizeUrl(endpoint)
            val apiKey = token.trim()
            require(apiKey.isNotBlank()) { "Ajoute le jeton du serveur OMR privé." }

            val pdf = context.contentResolver.openInputStream(uri)?.use(::readLimited)
                ?: error("Impossible d'ouvrir le PDF.")
            require(pdf.size >= 8 && pdf.take(1024).toByteArray()
                .toString(Charsets.ISO_8859_1).contains("%PDF-")) {
                "Le fichier sélectionné n'est pas un PDF reconnu."
            }
            val conn = (URL(safeUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 235_000
                doOutput = true
                useCaches = false
                setRequestProperty("Authorization", "Bearer $apiKey")
                setRequestProperty("Content-Type", "application/pdf")
                setRequestProperty("Accept", "application/vnd.recordare.musicxml, application/vnd.recordare.musicxml+xml")
                setFixedLengthStreamingMode(pdf.size)
            }
            try {
                conn.outputStream.use { it.write(pdf) }
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    val detail = conn.errorStream?.use { readLimited(it, 2048) }
                        ?.toString(Charsets.UTF_8)?.trim().orEmpty()
                    error("Serveur OMR : code $code. ${detail.ifBlank { "Conversion impossible." }}")
                }
                val mime = conn.contentType.orEmpty().lowercase()
                require("musicxml" in mime || "octet-stream" in mime || "zip" in mime) {
                    "Le serveur n'a pas retourné du MusicXML."
                }
                conn.inputStream.use(::readLimited)
            } finally {
                conn.disconnect()
            }
        }

    private fun readLimited(stream: InputStream, maxBytes: Int = MAX_BYTES): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val n = stream.read(chunk)
            if (n < 0) break
            require(out.size() + n <= maxBytes) { "Fichier trop volumineux (12 Mo maximum)." }
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }
}
