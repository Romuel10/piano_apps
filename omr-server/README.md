# Service OMR avancé (optionnel)

**Architecture :** PDF imprimé → Audiveris 5.11 → MusicXML/MXL → Piano Partition+.
Ce service n'est **pas hébergé actuellement**. La reconnaissance avancée ne
fonctionnera qu'après déploiement sur un serveur privé.

Le logiciel Audiveris (AGPL v3) est conçu pour les partitions **imprimées**.
Il n'est ni universel ni fiable à 100 % et ne prend pas en charge les
manuscrits : https://audiveris.github.io/audiveris/_pages/handbook/

## Lancer localement

\`\`\`sh
docker build -t piano-omr ./omr-server
export OMR_API_TOKEN="$(openssl rand -hex 32)"
docker run --rm -p 127.0.0.1:8080:8080 \
  -e OMR_API_TOKEN="$OMR_API_TOKEN" piano-omr
\`\`\`

Pour tester sur la **machine serveur** :

\`\`\`sh
curl http://127.0.0.1:8080/health
curl -f -H "Authorization: Bearer $OMR_API_TOKEN" \
  -H "Content-Type: application/pdf" \
  --data-binary @partition.pdf http://127.0.0.1:8080/v1/recognize \
  --output result.mxl
\`\`\`

Pour utiliser Android sur Internet, il faut placer cette API derrière un
reverse proxy **HTTPS** avec domaine, certificat TLS et pare-feu :
\`https://votre-domaine.example/v1/recognize\` (exemple non opérationnel).
Ne publiez jamais directement le port HTTP 8080 sur Internet.

Exemple de configuration Caddy :

\`\`\`
omr.votre-domaine.example {
    reverse_proxy 127.0.0.1:8080
}
\`\`\`

Ensuite, dans l'application, ouvrir « Reconnaissance avancée », saisir
l'adresse HTTPS et le jeton, choisir un PDF puis **accepter son envoi**.
Un PDF n'est transmis que lorsque l'utilisateur valide cet envoi ; le jeton
n'est conservé qu'en mémoire pendant la session. Les fichiers temporaires
du serveur sont supprimés en fin de conversion.

API : \`POST /v1/recognize\`, corps PDF brut (Content-Type application/pdf),
en-tête \`Authorization: Bearer ...\`, réponse XML ou MXL, limite 12 Mo
en entrée et sortie, durée maximale d'analyse trois minutes. Une seule
conversion simultanée. Service destiné à un usage privé (pas de quotas
multi-utilisateurs ni d'authentification multi-comptes).

### Précautions

La conversion et l'édition de MusicXML doivent être vérifiées avant une
exécution musicale exigeante. Audiveris ne garantit pas les partitions
manuscrites, les voix difficiles, les signes rares ou certains tuplets.
Les sources d'Audiveris sont disponibles à
https://github.com/Audiveris/audiveris sous **AGPL v3**.
Les obligations de cette licence s'appliquent à la distribution ou au
service en réseau du logiciel. Le moteur n'est pas inclus dans l'APK.


## Déployer ce service avec Railway (GitHub → HTTPS)

La source se trouve dans le même dépôt que l'application Android :
https://github.com/Romuel10/piano_apps

Railway gère une adresse HTTPS et fournit le port dynamique via `PORT`.
Le serveur utilise automatiquement cette variable, sans modifier le code
ni publier le port `8080` en clair.

1. Dans Railway, créer un projet et ajouter un **service GitHub** avec le dépôt `Romuel10/piano_apps`, branche `main`.
2. Dans **Settings → Source**, définir **Root Directory = `/omr-server`**. Cette étape est indispensable : le Dockerfile et `server.py` se trouvent dans ce dossier, pas à la racine Android.
3. Vérifier que le moteur de build est **Dockerfile**. Ne pas essayer de construire le projet Android sur Railway.
4. Dans **Variables**, définir `OMR_API_TOKEN` avec un secret aléatoire d'au moins 24 caractères (`openssl rand -hex 32` produit 64 caractères). Ne pas commiter cette valeur ni la placer dans la documentation publique.
5. Dans **Networking**, générer un **Public Domain** HTTPS. Dans **Healthcheck Path**, indiquer `/health`.
6. Configurer une limite mémoire suffisante pour Audiveris (recommandation initiale : **2 Gio minimum**, à ajuster après mesure) et activer le redémarrage automatique en cas d'échec.
7. Vérifier `GET https://<domaine>/health` (réponse attendue : `ok`) puis tester un vrai PDF imprimé via `POST /v1/recognize` avec le jeton. Un `GET /health` réussi confirme le serveur HTTP, pas la capacité d'Audiveris à convertir les partitions.

Le domaine public renvoyé par Railway ressemble à `https://nom-du-service.up.railway.app`. **N'utilisez pas cet exemple comme adresse réelle** : seules les valeurs renvoyées par votre projet Railway fonctionnent.

### Utilisation sur Android

Dans **Piano Partition+ → Importer une partition → Reconnaissance avancée PDF (Audiveris)** :
- Adresse : `https://<domaine-railway>/v1/recognize` (votre domaine réel).
- Jeton : la valeur de `OMR_API_TOKEN` du service Railway.
- Cocher le consentement d'envoi, puis sélectionner le PDF.

Le service ne reçoit que les PDF envoyés volontairement. Les fichiers de traitement temporaires sont détruits après chaque appel. **Le jeton reste visible à celui qui l'utilise** : ce mode à jeton partagé est destiné à un déploiement privé, pas à une application publique pour de nombreux utilisateurs. Une future version multi-utilisateurs demandera une authentification personnelle et des quotas.

### Important pour les coûts et les tests

Le conteneur peut consommer beaucoup de mémoire pendant la reconnaissance. Railway est un hébergement facturé selon les ressources et le forfait. Les PDF de 12 Mo maximum sont acceptés et la conversion est interrompue au bout de 180 secondes. Faire au moins un test de conversion complet avant de considérer le service opérationnel.

Pour vérifier sans ordinateur, ouvrir **Railway → Service → Deployments → Logs**. Les erreurs d'installation d'Audiveris et les refus d'authentification sont visibles via les codes HTTP.

Documentation Railway : https://docs.railway.com/builds/dockerfiles et https://docs.railway.com/builds/build-configuration
