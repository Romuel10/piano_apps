# Piano Partition+ — Android

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

## Importer une partition PDF ou MusicXML

La nouvelle interface suit le parcours **importer → vérifier/corriger → jouer**.

- **PDF imprimé** : l'analyse est locale (sans téléversement vers un serveur). Le moteur OMR repère les portées, estime les hauteurs en clés de sol/fa, distingue plusieurs valeurs de notes et regroupe certains accords. L'OCR du texte (Do/Ré/Mi) reste disponible en secours. Les résultats sont **approximatifs** : comparez avec l'aperçu de la page avant de lancer la lecture.
- **MusicXML (.musicxml, .xml) / MXL (.mxl)** : import structuré pour une meilleure fidélité des voix indépendantes, silences, durées pointées, tuplets, altérations, accords, liaisons de prolongation et indications de tempo. L'audio polyphonique utilise une synthèse en flux pour garder les différentes durées entre les deux mains.
- **Éditeur** : insertion rapide de notes/accords/pauses, vérification immédiate de la syntaxe et exemple traditionnel de Frère Jacques. Les morceaux saisis peuvent être enregistrés sur l'appareil.

### Exemple de PDF testé comme référence de mise au point

Le fichier utilisateur *Frère Jacques* comporte une couverture suivie d'une partition sur **deux portées par système** (clé de sol et clé de fa), en **4/4**, à **100 BPM**, avec noires, blanches et croches reliées par des barres. Le détecteur reconnaît désormais l'espacement important entre les deux portées et filtre mieux les barres de mesure / clés qui pouvaient être prises pour des notes.

**Limites importantes :** la reconnaissance visuelle de PDF reste expérimentale. Elle ne garantit pas les altérations écrites dans la portée, les armures, les reprises, les articulations, les polyrythmies, les scans inclinés ou manuscrits. Ce n'est **pas** un moteur professionnel Audiveris et l'import PDF ne produit pas de MusicXML fidèle. Pour les partitions complexes, exporter un MusicXML/MXL depuis un éditeur de partitions (par exemple MuseScore) donne un résultat nettement plus fiable.

L'aperçu texte produit par MusicXML est **une représentation simplifiée** : tant qu'il n'est pas modifié, la lecture utilise la partition polyphonique structurée. Après une modification manuelle, la lecture revient à la syntaxe de l'éditeur. La sauvegarde locale enregistre actuellement la transcription texte (pas les voix indépendantes MusicXML). Les reprises ne sont pas encore déroulées automatiquement.

### Vérification automatisée

GitHub Actions exécute les tests JUnit du convertisseur MusicXML, puis compile et met à disposition l'APK debug dans les artefacts du workflow. Le PDF de référence envoyé par l'utilisateur n'est pas publié dans ce dépôt.


## Version 1.2 — lecture PDF par mains indépendantes et identité Android

La lecture des partitions PDF se base désormais sur un **calendrier de notes polyphonique** : la main gauche et la main droite sont traitées indépendamment et une blanche peut rester tenue pendant plusieurs noires sur l'autre portée. Cela corrige une erreur du précédent lecteur, qui regroupait les notes par position horizontale et coupait des durées au plus court accord. Les tests JUnit valident cette synchronisation pour les accords, les deux mains et les systèmes successifs.

**L'OMR de PDF demeure expérimental.** Il détecte des symboles par analyse de pixels ; il peut encore confondre des notes et ne lit pas toutes les subtilités de la gravure musicale. Pour une transcription de partition complexe fiable, préférez l'import **MusicXML/MXL** à partir d'un éditeur de partitions. Ne considérez pas la prévisualisation PDF comme une transcription certifiée.

L'application porte désormais le nom **Piano Partition+** et possède une icône de lanceur en forme de clavier de piano et de clé de sol, avec les mêmes informations d'identité Android qu'avant (`com.romuel.pianosolfege`).

**Mises à jour sans désinstallation :** une configuration de signature pérenne a été préparée. Voir **[SIGNING.md](SIGNING.md)** pour installer une clé privée via les secrets GitHub et générer des APK `piano-partition-plus-release`. Les APK debug sont uniquement destinées aux essais et ne bénéficient pas de garantie de mise à jour sur place. Une transition unique peut être nécessaire depuis l'ancienne version debug.

## Version 1.3 — rythme PDF mesuré, plutôt que deviné

**Correction de la reconnaissance du rythme :**

- détection des barres de mesure verticales sur les deux portées ;
- prise en compte de la signature rythmique (4/4, 3/4, 6/8), lue par OCR quand possible ou choisie manuellement ;
- distinction plus robuste des têtes de notes **pleines** (noires/croches) et **creuses** (blanches) et rejet des traits de ligature pris pour de fausses notes ;
- regroupement des notes dans chaque mesure et synchronisation des mains **à chaque barre de mesure** ;
- contrôle de la somme des durées pour chaque portée et chaque mesure, avec avertissement précis si la transcription n'atteint pas la mesure attendue ;
- lecture bloquée en présence d'erreurs rythmiques, sauf validation explicite d'une écoute approximative ;
- lecture du tempo indiqué sous la forme `♩=100`, `q=100` ou OCR `/=100`, sinon avertissement et réglage BPM manuel à l'unité.

**Référence :** sur le PDF de deux pages *Frère Jacques* fourni en conversation, la partition de la deuxième page est en 4/4 à 100 BPM. Le prototype de détection sur le rendu de cette page a retrouvé huit mesures de quatre temps par portée (deux blanches à la basse, combinaisons de noires/blanches/croches à la mélodie). Cette vérification sur un rendu spécifique **n'est pas un test sur téléphone** et ne garantit pas les autres partitions.

**Limites :** cette OMR locale est heuristique. Elle peut encore mal lire des silences, altérations, notes pointées, triolets, changements de mesure ou plusieurs voix superposées sur la même portée. Un document complexe nécessite un moteur spécialisé de reconnaissance de partition vers MusicXML (par exemple Audiveris), puis validation et correction. Importer directement le MusicXML reste la méthode la plus fiable de cette application.

La branche `main` conserve `com.romuel.pianosolfege` et le système de signature release pérenne de `SIGNING.md`. Évitez les APK debug si vous souhaitez conserver les mises à jour sans réinstallation.

## v1.4 — Lecture des crochets et des doubles-croches

Le problème signalé était que l'OMR ne différenciait pas le nombre de crochets/barres : une note pleine reliée était toujours classée comme croche. La lecture d'image possède désormais un classifieur de **symboles rythmiques par géométrie**, testable indépendamment de l'interface Android.

| Symbole | Durée en temps de noire | Représentation texte |
|---|---:|---|
| Ronde (tête vide sans hampe) | 4 | `Do4:4 |
| Blanche (tête vide avec hampe) | 2 | `Do4:2 |
| Noire (tête pleine avec hampe) | 1 | `Do4:1 |
| Croche (1 barre ou 1 crochet) | 0,5 | `Do4:0.5 |
| Double-croche (2 barres ou 2 crochets) | 0,25 | `Do4:0.25 |
| Triple-croche (3 barres ou 3 crochets) | 0,125 | `Do4:0.125 |
| Point de prolongation | × 1,5 | `Do4:1.5 |
| Pause de ronde / blanche (rectangle imprimé) | 4 / 2 | `Pause:4 / `Pause:2 |

Les notes peuvent se suivre à la croche ou à la double-croche tout en conservant des durées indépendantes dans les deux mains. Les repos rectangulaires détectés avancent la position temporelle **sans déclencher de son**. L'application vérifie ensuite le total des temps de chaque portée/mesure et avertit si le rythme reconstitué est incohérent. L'écran d'édition dispose maintenant d'un bouton **Tester les rythmes** et de raccourcis pour saisir les durées.

**Contrôles automatisés :** des tests JUnit dessinent des têtes de notes, des hampes, un, deux et trois faisceaux, des crochets isolés, un point de prolongation, ainsi que des silences de ronde/blanche. Des tests supplémentaires vérifient l'alignement des deux mains et le décalage produit par un silence.

**Limites et prudence :** les silhouettes imprimées ne sont pas toujours lisibles par simples règles de pixels, surtout en basse résolution. Les **soupirs**, **demi-soupirs**, **quarts de soupir** et autres silences à fanions, les liaisons de prolongation entre mesures, les triolets et les changements de voix ne sont *pas* garantis par le détecteur PDF. L'import MusicXML/MXL reste disponible et plus fidèle pour ces cas. Des mesures incohérentes sont signalées au lieu d'être déclarées correctes. Une réussite des tests synthétiques **ne démontre pas** une lecture parfaite de n'importe quel PDF ou scan manuscrit.

Le fichier PDF personnel de référence n'est pas placé dans le dépôt.

## v1.5 — Reconnaissance de Do / Ré / Mi et des hauteurs sur les portées

Cette version répare deux problèmes différents :

**Saisie texte :** on peut maintenant jouer directement `Do Ré Mi Fa Sol La Si` (ainsi qu'une seule note `Do`, sans obligation de préciser l'octave). L'octave par défaut est la 4. Les notes anglaises `C D E F G A B` sont aussi acceptées. On peut préciser une durée, ex. `Do:0.5`, ou un accord `[Do Mi Sol]:2`.

**PDF sur portée :** l'ancien système recherchait un pic de noir à une position théorique, ce qui confondait parfois notes, clés et lignes. La nouvelle première étape recherche les **têtes de notes ovales réellement imprimées** : elle soustrait les longs traits horizontaux, réunit les contours des têtes creuses, élimine les traits fins, puis associe les centres obtenus à une position diatonique. Une deuxième étape lit les hampes, barres et points pour estimer la durée. Les deux portées d'un piano restent synchronisées dans la timeline.

Le test technique de référence a été effectué sur un rendu local de la page 2 du PDF *Frère Jacques* fourni pendant le développement. Les 8 groupes de notes de la première ligne ont été retrouvés en clé de sol comme **Do4 Ré4 Mi4 Do4 / Do4 Ré4 Mi4 Do4**, et les notes de la main gauche en clé de fa comme **Do3 Sol3 / Do3 Sol3** (deux blanches par mesure). Les autres systèmes du document ont également servi à comparer les positions de notes. Ce test sur image ne remplace pas une validation de la version APK sur un vrai téléphone.

**La lecture optique n'est toujours pas universelle :** certaines partitions manuscrites, les armures, les altérations graphiques, les liaisons et les voix complexes peuvent encore être mal reconnues. Le mode MusicXML/MXL reste disponible pour obtenir une transcription musicale plus fiable. Une mesure incohérente est signalée au lieu d'être présumée exacte.

La suite de tests automatisés vérifie désormais l'identification des positions en clés de sol et de fa, les têtes pleines/creuses, et la saisie en solfège sans octave. Les fichiers sources privés de la partition ne sont pas copiés dans le dépôt.

## 2.0 bêta — reconnaissance des partitions complexes

Pour aller au-delà des croches et notes simples, il faut une **conversion professionnelle en MusicXML**. La version 2.0 propose donc trois chemins :

1. **MusicXML / MXL** : import direct de partitions structurées, prises en charge de la polyphonie et des accords, voix indépendantes, durées, silences, liaisons de prolongation, altérations, changement de métrique, reprises simples et première/deuxième fins, ainsi que **changements de tempo indiqués numériquement dans le fichier**. Les fichiers \`score-partwise\` et \`score-timewise\` sont acceptés.
2. **PDF avancé (Audiveris)** : un bouton optionnel dans Android envoie, **seulement après accord explicite**, le PDF imprimé à un **serveur privé configuré par l'utilisateur**, qui invoque Audiveris 5.11 et renvoie du MusicXML/MXL. L'utilisateur écoute la transcription structurée ou **exporte le MusicXML** pour le corriger dans un éditeur avant lecture. **Le service n'est pas hébergé dans ce dépôt** : son Dockerfile, les tests et les instructions sont dans [omr-server/README.md](omr-server/README.md).
3. **PDF local sans serveur** : l'ancien algorithme heuristique Android reste disponible hors connexion, mais il **ne constitue pas un lecteur fiable de partitions difficiles**.

**Précision : aucun moteur OMR existant ne garantit tous les types de partitions**, notamment manuscrites, anciennes ou très denses. L'application doit vérifier les notes reconnues, les rythmes et l'interprétation musicale plutôt qu'annoncer « 100 % ». Les règles complexes telles que **Da Capo/Dal Segno, coda, pédale, nuances, rubato et partitions de piano à micro-intervalles** restent des travaux complémentaires. Les reprises classiques et changements de tempo explicites du MusicXML sont désormais respectés ; les indications de tempo purement textuelles sans chiffre ne sont pas encore interprétées automatiquement.

### Confidentialité et déploiement

Le PDF n'est **pas transmis à un serveur OpenAI ou public** par cette fonctionnalité. Il est envoyé uniquement à **l'URL HTTPS que l'utilisateur configure** après une confirmation spécifique. Le jeton est gardé en mémoire pour la session et non sauvegardé. L'adresse du serveur peut être enregistrée localement.

Le service externe est fourni sous forme de code source et d'image Docker **à héberger**. Tant qu'aucun serveur Audiveris n'a été déployé, le bouton de reconnaissance avancée ne peut pas fonctionner. Consultez [omr-server/README.md](omr-server/README.md) pour les étapes de déploiement et les exigences de licence AGPL v3 du moteur.

Le package Android historique \`com.romuel.pianosolfege\` est conservé, ce qui permet aux APK **release** signées avec la même clé et dotées d'un \`versionCode\` plus récent de remplacer les anciennes versions sans effacer les données. Les APK **debug** ne doivent pas être utilisées pour les mises à jour utilisateurs.

Tests : \`gradle :app:testDebugUnitTest :app:assembleDebug\` et \`python3 -m unittest discover -s omr-server/tests -v\`. Un job CI teste également le démarrage d'une image Docker Audiveris.

## Version 2.1 bêta — exemple Canon in D (Pachelbel)

Un PDF comme *Canon in D*, arrangement de Ben Dunnett (deux pages, armure de
ré majeur à deux dièses, 4/4, main gauche en blanches, main droite en croches,
doubles-croches et valeurs pointées), **dépasse les capacités du détecteur
local par pixels**.

Les avertissements du type « Mesure 1 : 1 temps lu / 4 attendus » signifient
que **des symboles ont été manqués**, pas que la partition est incorrecte.
Changer le bouton 3/4, 4/4 ou 6/8 ne crée pas les notes manquantes.

La nouvelle interface présente directement **Reconnaissance avancée PDF
(Audiveris)**, préconfigure l'adresse du serveur privé Railway déjà déployé,
demande toujours le jeton de session et le consentement explicite d'envoi,
et propose de **relancer la reconnaissance sur le PDF déjà ouvert**.
Un échec OMR s'affiche à proximité de l'import avec le message d'erreur
du serveur, plutôt que seulement en bas de l'écran.

Le serveur essaie le PDF directement, puis, en cas d'échec, le **convertit
en TIFF multipage à 300 ppp via Ghostscript** et relance Audiveris.
Cette deuxième tentative conserve les deux pages dans un seul document,
afin que la transcription MusicXML puisse conserver les deux mains.

La CI vérifie désormais la reconnaissance PDF vers MusicXML avec une
partition publique de Beethoven mise à disposition par le Mutopia Project.
Cela teste le *moteur réel* et pas seulement son démarrage.
Aucun PDF personnel ni la partition Ben Dunnett fournie par l'utilisateur
n'a été publié dans le dépôt.

**Limite :** même un OMR spécialisé peut se tromper sur une partition imprimée
complexe. La transcription doit être vérifiée, puis exportée en MusicXML pour
correction si nécessaire. **Ce mode ne promet pas une lecture universelle**.
