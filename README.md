# Piano Solfège Android

Application Android en **Kotlin + Jetpack Compose** qui joue des mélodies et des accords écrits en solfège français.

## Fonctionnalités

- Notes : `Do Ré Mi Fa Sol La Si`
- Octaves sur la plage piano A0–C8
- Dièses et bémols : `Fa#4`, `Sib4`
- Durées : `Do4:0.5`, `Ré4:1`, `Mi4:2`
- Silences : `Pause:1`
- Accords : `[Do4 Mi4 Sol4]:2`
- Tempo configurable de 40 à 220 BPM
- Lecture, pause/reprise et stop
- Clavier visuel interactif sur une octave
- Mise en évidence des touches jouées
- Sauvegarde locale et rechargement des morceaux
- Vrais échantillons de piano mis en cache sur l'appareil
- Synthétiseur intégré de secours si les samples ne sont pas encore disponibles

## Exemple

```text
Do4:1 Ré4:1 Mi4:1 Fa4:1
[Do4 Mi4 Sol4]:2 Pause:0.5
[Fa4 La4 Do5]:2 [Sol4 Si4 Ré5]:2
```

## Audio

L'application télécharge au premier lancement un petit ensemble d'échantillons du **Salamander Grand Piano V3** puis les conserve dans le stockage interne de l'application. Les notes intermédiaires sont obtenues par transposition des échantillons les plus proches.

Si le téléchargement n'est pas possible, le moteur `AudioTrack` intégré prend automatiquement le relais avec une synthèse locale. Une fois les échantillons mis en cache, le piano réel peut être utilisé sans nouveau téléchargement.

Les enregistrements Salamander Grand Piano V3 sont de **Alexander Holm**, sous licence **Creative Commons Attribution 3.0 (CC BY 3.0)**. Voir `NOTICE.md`.

## Architecture

```text
UI Jetpack Compose
      ↓
PianoViewModel
      ├── SongRepository (SharedPreferences)
      ↓
NoteParser
      ↓
PianoEngine
      ├── SoundPool + samples Salamander
      └── AudioTrack (secours synthétique)
```

## Ouvrir le projet

1. Cloner ce dépôt.
2. Ouvrir le dossier dans Android Studio.
3. Laisser Android Studio synchroniser Gradle.
4. Lancer l'application sur Android API 26+.

Le workflow GitHub Actions compile également `:app:assembleDebug` avec Gradle 8.9 et JDK 17.

## Syntaxe

| Fonction | Exemple |
|---|---|
| Note | `Do4` |
| Dièse | `Fa#4` |
| Bémol | `Sib4` |
| Durée | `Ré4:0.5` |
| Silence | `Pause:1` |
| Accord | `[Do4 Mi4 Sol4]:2` |

## Prochaines évolutions possibles

- plusieurs octaves visibles sur le clavier
- import/export MIDI
- métronome
- transposition globale
- partage/export des morceaux
- tests unitaires du parser et du séquenceur
