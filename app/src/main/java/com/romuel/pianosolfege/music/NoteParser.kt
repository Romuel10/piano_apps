package com.romuel.pianosolfege.music

import java.text.Normalizer
import java.util.Locale

object NoteParser {
    private val notePattern = Regex("^(do|re|mi|fa|sol|la|si|c|d|e|f|g|a|b)(#|b)?([0-8])?(?::([0-9]+(?:\\.[0-9]+)?))?$")
    private val bareNotePattern = Regex("^(do|re|mi|fa|sol|la|si|c|d|e|f|g|a|b)(#|b)?([0-8])?$")
    private val restPattern = Regex("^(pause|silence|r)(?::([0-9]+(?:\\.[0-9]+)?))?$")
    private val chordPattern = Regex("^\\[(.+)](?::([0-9]+(?:\\.[0-9]+)?))?$")
    private val tokenPattern = Regex("\\[[^]]+](?::[0-9]+(?:\\.[0-9]+)?)?|[^\\s,;]+")

    private val semitones = mapOf(
        "do" to 0,
        "re" to 2,
        "mi" to 4,
        "fa" to 5,
        "sol" to 7,
        "la" to 9,
        "si" to 11,
        "c" to 0, "d" to 2, "e" to 4, "f" to 5,
        "g" to 7, "a" to 9, "b" to 11,
    )

    private val displayNames = arrayOf(
        "Do", "Do#", "Ré", "Ré#", "Mi", "Fa",
        "Fa#", "Sol", "Sol#", "La", "La#", "Si",
    )

    fun parse(input: String): ParseResult {
        val tokens = tokenPattern.findAll(input)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()

        if (tokens.isEmpty()) {
            return ParseResult(emptyList(), listOf("Entre au moins une note, par exemple : Do4 Ré4 Mi4"))
        }

        val notes = mutableListOf<PianoNote>()
        val errors = mutableListOf<String>()

        tokens.forEachIndexed { index, original ->
            val normalized = normalize(original)

            val rest = restPattern.matchEntire(normalized)
            if (rest != null) {
                val beats = rest.groupValues[2].toFloatOrNull() ?: 1f
                if (beats <= 0f) {
                    errors += "Élément ${index + 1} ($original) : la durée doit être positive."
                } else {
                    notes += PianoNote(label = "Pause", midis = emptyList(), beats = beats)
                }
                return@forEachIndexed
            }

            val chord = chordPattern.matchEntire(normalized)
            if (chord != null) {
                val chordParts = chord.groupValues[1]
                    .split(Regex("[\\s,]+"))
                    .filter { it.isNotBlank() }
                val beats = chord.groupValues[2].toFloatOrNull() ?: 1f

                if (beats <= 0f) {
                    errors += "Élément ${index + 1} ($original) : la durée doit être positive."
                    return@forEachIndexed
                }
                if (chordParts.size < 2) {
                    errors += "Élément ${index + 1} ($original) : un accord doit contenir au moins deux notes."
                    return@forEachIndexed
                }

                val chordMidis = mutableListOf<Int>()
                chordParts.forEach { part ->
                    val midi = parseBareMidi(part)
                    if (midi == null) {
                        errors += "Élément ${index + 1} ($original) : note d'accord invalide « $part ». Exemple : [Do4 Mi4 Sol4]:2."
                    } else if (midi !in 21..108) {
                        errors += "Élément ${index + 1} ($original) : $part est hors de la plage A0–C8."
                    } else {
                        chordMidis += midi
                    }
                }

                if (chordMidis.size == chordParts.size) {
                    notes += PianoNote(
                        label = chordMidis.joinToString(prefix = "[", postfix = "]") { labelForMidi(it) },
                        midis = chordMidis.distinct(),
                        beats = beats,
                    )
                }
                return@forEachIndexed
            }

            val match = notePattern.matchEntire(normalized)
            if (match == null) {
                errors += "Élément ${index + 1} ($original) invalide. Exemples : Do, Ré, Mi, Fa#4, Sib3, Ré4:0.5, [Do Mi Sol]:2."
                return@forEachIndexed
            }

            val midi = midiFromMatch(match)
            val beats = match.groupValues[4].toFloatOrNull() ?: 1f

            if (beats <= 0f) {
                errors += "Élément ${index + 1} ($original) : la durée doit être positive."
            } else if (midi !in 21..108) {
                errors += "Élément ${index + 1} ($original) : utilise une note comprise entre A0 et C8."
            } else {
                notes += PianoNote(label = labelForMidi(midi), midis = listOf(midi), beats = beats)
            }
        }

        return ParseResult(notes, errors)
    }

    fun labelForMidi(midi: Int): String {
        val safeMidi = midi.coerceIn(0, 127)
        val octave = safeMidi / 12 - 1
        return "${displayNames[safeMidi % 12]}$octave"
    }

    private fun parseBareMidi(value: String): Int? {
        val match = bareNotePattern.matchEntire(normalize(value)) ?: return null
        return midiFromMatch(match)
    }

    private fun midiFromMatch(match: MatchResult): Int {
        val name = match.groupValues[1]
        val accidental = match.groupValues[2]
        val octave = match.groupValues[3].toIntOrNull() ?: 4

        var semitone = semitones.getValue(name)
        if (accidental == "#") semitone += 1
        if (accidental == "b") semitone -= 1

        return (octave + 1) * 12 + semitone
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase(Locale.ROOT)
            .replace('♯', '#')
            .replace('♭', 'b')
            .trim()
    }
}
