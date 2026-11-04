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
