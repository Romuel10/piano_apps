# Piano Partition+ — mises à jour Android sans désinstallation

Android n'accepte une nouvelle APK **par-dessus** l'ancienne que si :
1. l'identifiant (`applicationId`) est identique ;
2. la signature est identique ou dispose d'une rotation autorisée ;
3. le `versionCode` n'est pas inférieur à celui installé.

L'identifiant historique **`com.romuel.pianosolfege` est conservé**. Le numéro de version de la CI augmente automatiquement (numéro du run GitHub + 100). Le nom visible est **Piano Partition+**.

## Pourquoi les anciennes APK imposaient parfois une désinstallation

Les artefacts `app-debug.apk` précédents ont été créés par des machines GitHub Actions éphémères. La clé debug est générée par défaut sur la machine de compilation : elle change donc entre certains builds. Android refuse les mises à jour dont la signature diffère.

**Important : il n'est pas possible de forcer Android à remplacer une application existante avec une autre signature tout en conservant les données.** Il faut une transition unique vers l'APK signée avec votre nouvelle clé pérenne. Sauvegardez vos morceaux manuellement avant cette transition. Les morceaux enregistrés dans l'application peuvent être perdus lors de sa désinstallation.

## Mettre en place une clé permanente et privée

**Ne commitez jamais le fichier `.jks` ou son mot de passe dans le dépôt public.**

Vous pouvez créer votre propre clé sur votre ordinateur :

```bash
keytool -genkeypair -v -keystore pianopartition-release.jks \
  -alias pianopartition -keyalg RSA -keysize 3072 -validity 10000
```

Conservez le fichier JKS et ses mots de passe dans un coffre-fort sécurisé avec sauvegarde. Pour l'envoyer à GitHub Actions sous forme de secret :

macOS/Linux :

```bash
base64 -w 0 pianopartition-release.jks
```

Sur macOS où `-w` n'est pas disponible, utilisez `base64 < pianopartition-release.jks | tr -d '\\n'`. Sur Windows, encodez le fichier en Base64 sans retours à la ligne, par exemple avec PowerShell.

Dans **GitHub → votre dépôt → Settings → Secrets and variables → Actions → New repository secret**, créer les **quatre secrets** suivants :

| Nom | Valeur |
|---|---|
| `PIANO_KEYSTORE_BASE64` | Contenu Base64 complet du fichier JKS |
| `PIANO_KEYSTORE_PASSWORD` | Mot de passe du fichier de signature |
| `PIANO_KEY_PASSWORD` | Mot de passe de la clé privée |
| `PIANO_KEY_ALIAS` | Alias de la clé (ex. `pianopartition`) |

Vous pouvez également utiliser le kit de signature privé préparé dans la conversation de développement. **Ce kit est confidentiel, à conserver hors du dépôt**. Il contient les quatre valeurs nécessaires.

## Chaque prochaine mise à jour

Une fois les quatre secrets configurés :

1. Chaque `push` sur `main` lance les tests et compile une version.
2. L'action GitHub produit en plus l'artefact **`piano-partition-plus-release`** contenant une APK signée avec **la même clé permanente**.
3. Installez l'APK signée sur le téléphone, puis les versions ultérieures de ce même canal pourront remplacer l'APK précédente directement, sans la désinstaller.
4. Pour une distribution automatique via Google Play, l'artefact **`piano-partition-plus-aab`** est aussi préparé ; l'app doit être publiée sur un compte Google Play Console, avec Play App Signing.

Ne distribuez pas `piano-solfege-debug` aux utilisateurs qui souhaitent recevoir des mises à jour durables : l'APK debug reste une construction de test et sa signature est variable.

### Si vous avez déjà l'ancienne version de test

Le premier remplacement d'une version debug ancienne par la version de distribution signée peut échouer avec `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Dans ce cas, sauvegardez vos partitions avant de désinstaller une dernière fois, puis installez la version **release signée**. **Les mises à jour release suivantes conserveront normalement les données**, tant que `applicationId`, signature et `versionCode` restent compatibles.

Si vous perdez la clé privée et ne passez pas par le mécanisme de signatures de Google Play, aucune mise à jour directe ne pourra être signée avec cette identité.

Références : https://developer.android.com/google/play/app-updates et https://developer.android.com/studio/publish/app-signing
