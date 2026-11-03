# Piano Solfège Android

Application Android en **Kotlin + Jetpack Compose** qui joue des mélodies, des accords et des partitions PDF.

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
- Import de PDF contenant des noms de notes via OCR local ML Kit
- Import de partitions PDF imprimées via un moteur OMR local léger
- Vrais échantillons de piano mis en cache sur l'appareil
- Synthétiseur intégré de secours si les samples ne sont pas encore disponibles

## Import PDF et OMR

Lorsqu'un PDF est sélectionné, l'application rend chaque page en image puis utilise deux chemins de reconnaissance :

1. **OMR de partition classique** : détection des groupes de cinq lignes, des têtes de notes, estimation de la hauteur, estimation simple des durées et regroupement des notes alignées en accords.
2. **OCR de texte musical** : si aucune portée exploitable n'est trouvée, ML Kit recherche des noms comme `Do Ré Mi` ou `C D E`.

Pour un système piano à deux portées, la portée supérieure est interprétée en **clé de sol** et la portée inférieure en **clé de fa**. Les portées isolées sont interprétées en clé de sol.

L'OMR local est volontairement léger afin de fonctionner directement sur Android sans serveur ni modèle de plusieurs centaines de mégaoctets. Il est surtout adapté aux partitions imprimées propres, droites et bien contrastées. Il reconnaît les hauteurs naturelles, les accords alignés et estime les rondes, blanches, noires et certaines croches. Les altérations graphiques, armures, triolets, liaisons, reprises et rythmes polyphoniques complexes ne sont pas encore interprétés de façon complète ; le résultat converti reste éditable dans la zone de notes avant lecture.

## Exemple texte

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
      ├── PdfNoteReader
      │     ├── SheetMusicOmr (portées / notes / accords)
      │     └── ML Kit Text Recognition (solfège texte)
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

Le workflow GitHub Actions compile également `:app:assembleDebug` avec Gradle 8.9 et JDK 17 et publie l'APK debug comme artefact.

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

- OMR avancé avec armures, altérations et voix rythmiques indépendantes
- MusicXML / MIDI
- plusieurs octaves visibles sur le clavier
- métronome
- transposition globale
- partage/export des morceaux
- tests unitaires du parser, du séquenceur et de l'OMR
