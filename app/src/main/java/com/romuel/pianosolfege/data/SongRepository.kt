package com.romuel.pianosolfege.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class SavedSong(
    val id: String,
    val name: String,
    val content: String,
    val bpm: Int,
)

class SongRepository(context: Context) {
    private val prefs = context.getSharedPreferences("piano_songs", Context.MODE_PRIVATE)

    fun load(): List<SavedSong> = runCatching {
        val raw = prefs.getString(KEY_SONGS, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    SavedSong(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        content = item.getString("content"),
                        bpm = item.optInt("bpm", 90).coerceIn(40, 220),
                    )
                )
            }
        }
    }.getOrElse { emptyList() }

    fun save(name: String, content: String, bpm: Int): List<SavedSong> {
        val songs = load().toMutableList()
        val existingIndex = songs.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        val song = SavedSong(
            id = if (existingIndex >= 0) songs[existingIndex].id else UUID.randomUUID().toString(),
            name = name,
            content = content,
            bpm = bpm.coerceIn(40, 220),
        )

        if (existingIndex >= 0) {
            songs[existingIndex] = song
        } else {
            songs.add(0, song)
        }
        persist(songs)
        return songs
    }

    fun delete(id: String): List<SavedSong> {
        val songs = load().filterNot { it.id == id }
        persist(songs)
        return songs
    }

    private fun persist(songs: List<SavedSong>) {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject()
                    .put("id", song.id)
                    .put("name", song.name)
                    .put("content", song.content)
                    .put("bpm", song.bpm)
            )
        }
        prefs.edit().putString(KEY_SONGS, array.toString()).apply()
    }

    private companion object {
        const val KEY_SONGS = "songs"
    }
}
