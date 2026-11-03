# Piano Solfège Android

Application Android en **Kotlin + Jetpack Compose** qui lit une suite de notes de solfège saisie par l'utilisateur.

## Fonctionnalités du MVP

- Saisie de notes : `Do Ré Mi Fa Sol La Si`
- Octaves : `Do4`, `Ré5`, etc. (plage piano A0–C8)
- Dièses et bémols : `Fa#4`, `Sib4`
- Durées en temps : `Do4:0.5`, `Ré4:1`, `Mi4:2`
- Silences : `Pause:1`
- Tempo configurable de 40 à 220 BPM
- Lecture / Stop
- Affichage de la note en cours
- Fonctionnement hors ligne
- Génération audio locale : aucune banque de sons n'est nécessaire pour ce MVP

## Exemple

```text
Do4:1 Ré4:1 Mi4:1 Fa4:1 Sol4:2 Pause:0.5 Sol4:1 Do5:2
```

## Architecture

```text
UI Jetpack Compose
      ↓
PianoViewModel
      ↓
NoteParser
      ↓
PianoEngine (AudioTrack)
```

Le `NoteParser` transforme les notes françaises en numéros MIDI. `PianoEngine` convertit ensuite les numéros MIDI en fréquences et synthétise un timbre de piano simple avec plusieurs harmoniques.

## Ouvrir le projet

1. Cloner ce dépôt.
2. Ouvrir le dossier dans Android Studio.
3. Laisser Android Studio synchroniser Gradle.
4. Lancer l'application sur un appareil ou un émulateur Android API 26+.

> Le dépôt contient les fichiers Gradle du projet, mais pas encore le Gradle Wrapper binaire. Android Studio peut configurer/utiliser Gradle pour la synchronisation. Une prochaine étape recommandée est de générer le wrapper avec `gradle wrapper` puis de versionner `gradlew`, `gradlew.bat` et `gradle/wrapper/gradle-wrapper.jar`.

## Améliorations prévues

- Pause / reprise
- Accords, par exemple `[Do4 Mi4 Sol4]`
- Clavier de piano visuel
- Sauvegarde des morceaux
- Import/export MIDI
- Samples de vrai piano pour remplacer le synthétiseur intégré
- Tests unitaires du parser et du séquenceur
