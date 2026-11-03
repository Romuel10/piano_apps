package com.romuel.pianosolfege.music

import java.text.Normalizer
import java.util.Locale

object NoteParser {
    private val notePattern = Regex("^(do|re|mi|fa|sol|la|si)(#|b)?([0-8])(?::([0-9]+(?:\\.[0-9]+)?))?$")
    private val restPattern = Regex("^(pause|silence|r)(?::([0-9]+(?:\\.[0-9]+)?))?$")

    private val semitones = mapOf(
        "do" to 0,
        "re" to 2,
        "mi" to 4,
        "fa" to 5,
        "sol" to 7,
        "la" to 9,
        "si" to 11,
    )

    fun parse(input: String): ParseResult {
        val tokens = input
            .replace(',', ' ')
            .replace(';', ' ')
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        if (tokens.isEmpty()) {
            return ParseResult(emptyList(), listOf("Entre au moins une note, par exemple : Do4 Ré4 Mi4"))
        }

        val notes = mutableListOf<PianoNote>()
        val errors = mutableListOf<String>()

        tokens.forEachIndexed { index, original ->
            val token = normalize(original)

            val rest = restPattern.matchEntire(token)
            if (rest != null) {
                val beats = rest.groupValues[2].toFloatOrNull() ?: 1f
                if (beats <= 0f) {
                    errors += "Élément ${index + 1} ($original) : la durée doit être positive."
                } else {
                    notes += PianoNote(label = "Pause", midi = null, beats = beats)
                }
                return@forEachIndexed
            }

            val match = notePattern.matchEntire(token)
            if (match == null) {
                errors += "Élément ${index + 1} ($original) invalide. Exemple accepté : Do4, Fa#4, Sib3, Ré4:0.5."
                return@forEachIndexed
            }

            val name = match.groupValues[1]
            val accidental = match.groupValues[2]
            val octave = match.groupValues[3].toInt()
            val beats = match.groupValues[4].toFloatOrNull() ?: 1f

            if (beats <= 0f) {
                errors += "Élément ${index + 1} ($original) : la durée doit être positive."
                return@forEachIndexed
            }

            var semitone = semitones.getValue(name)
            if (accidental == "#") semitone += 1
            if (accidental == "b") semitone -= 1

            val midi = (octave + 1) * 12 + semitone
            if (midi !in 21..108) {
                errors += "Élément ${index + 1} ($original) : utilise une note comprise entre A0 et C8."
                return@forEachIndexed
            }

            notes += PianoNote(label = original, midi = midi, beats = beats)
        }

        return ParseResult(notes, errors)
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase(Locale.ROOT).trim()
    }
}
