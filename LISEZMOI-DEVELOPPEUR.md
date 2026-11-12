# Orion XS : notes pour toi (l'aidant)

## Ce qu'il faut envoyer à l'utilisateur

1. `livraison/OrionXS-1.01.apk` : l'application signée (toujours envoyer la version la plus récente).
2. `livraison/Mode-d-emploi-OrionXS.pdf` : le mode d'emploi illustré.
L'utilisateur saisit lui-même la clé : l'assistant et le guide lui expliquent où la trouver dans VictronConnect.
« Coller la clé » accepte aussi un message entier (WhatsApp ou autre) et en extrait les 32 caractères.

## Récupérer la clé dans VictronConnect

1. Ouvre VictronConnect près de l'Orion XS et connecte-toi à l'appareil.
2. Va dans ⚙️ Réglages, puis ⋮ (en haut à droite), puis **Product info**.
3. Mets **Instant readout via Bluetooth** sur *Enabled*. Sans ça, le chargeur n'émet aucune donnée.
4. Dans **Instant readout details**, touche **Show** : la clé (*encryption key*) fait 32 caractères hexadécimaux.

## Intégrer la clé et ton numéro WhatsApp dans l'APK (recommandé)

Copie `secrets.properties.example` en `secrets.properties` (jamais publié sur GitHub) et remplis-le :

```properties
supportPhone=33612345678
victronKey=
```

(ou en ligne de commande : `./gradlew livrer -PsupportPhone=33612345678`)

- `victronKey` : la clé de 32 caractères. L'étape « clé » de l'assistant est alors sautée.
- `supportPhone` : ton numéro au format international, sans `+` ni espaces. Le bouton « diagnostic » ouvre alors directement ta conversation WhatsApp.

Les deux restent modifiables dans l'app (onglet Aide, puis Réglages avancés).

## ⚠️ Le keystore : à sauvegarder absolument

Le dossier `keystore/` contient `orionxs-release.jks` et `keystore.properties` (mot de passe).
**Copie-le dans un endroit sûr** (clé USB, gestionnaire de mots de passe…). Sans lui, tu ne pourras pas publier
de mise à jour : Android refuse une mise à jour signée avec une autre clé. Il faudrait alors désinstaller l'app,
et l'historique serait perdu.

Empreinte SHA-256 du certificat : `e43228d0db8902a788161bed7876511478f32ac1a0954b1ecaf0b737f68b8384`

## Publier une mise à jour

1. Dans `app/build.gradle.kts`, augmente `versionCode` de 1 (3, 4, …) et change `versionName` (1.02, 1.03…).
   Android refuse une mise à jour dont le `versionCode` n'est pas plus grand.
2. Lance `./gradlew livrer` : l'APK signé arrive dans `livraison/OrionXS-<version>.apk`.
3. Ajoute une ligne dans l'historique des versions ci-dessous.
4. Envoie l'APK : il s'installe par-dessus, et l'historique est conservé.

La version installée s'affiche en bas de l'onglet Aide, et en tête de chaque diagnostic.

## Historique des versions

| Version | Code | Changements |
|---|---|---|
| 1.01 | 2 | Première version envoyée. Clé à saisir par l'utilisateur (étapes VictronConnect affichées), CSV mis à jour automatiquement (sans bouton d'export), numéro WhatsApp de l'aidant intégré, correction de l'extraction de la clé dans un message. |
| 1.0 | 1 | Version de développement, jamais distribuée. |

Environnement de compilation : JDK 21, Android SDK 35 (installé dans `%LOCALAPPDATA%\Android\Sdk`), Gradle 8.11.1 (wrapper).

## Fonctionnement technique

- **Décodage** (`ble/VictronDecoder.kt`) : données constructeur Victron (0x02E1), enregistrement 0x0F (Orion XS)
  et 0x04 (Orion Smart DC-DC), AES-128-CTR avec un compteur little endian, comme dans victron-ble.
  Les tests unitaires utilisent un vrai vecteur de victron-ble.
- **Arrière-plan** (`service/MonitorService.kt`) : service de premier plan de type `connectedDevice`, avec une
  notification permanente. Écran éteint, le scan est filtré sur Victron, ce qui est indispensable sur Samsung.
  Il est relancé toutes les 20 min (Android dégrade les scans de plus de 30 min) et dès qu'aucune donnée n'arrive pendant 3 min.
- **Fiabilité** : une alarme exacte tous les quarts d'heure (`AlarmScheduler`) relance le service s'il a été tué.
  L'app relance aussi tout après un redémarrage du téléphone ou une mise à jour. L'assistant demande l'exemption
  d'optimisation de batterie, qui correspond au réglage Samsung « Non restreinte ».
- **Historique** : SQLite, une ligne par heure. La première mesure reçue dans l'heure fait foi ; une heure sans
  donnée est notée « pas de mesure ».
- **CSV** : `Téléchargements/OrionXS/orion_xs_historique.csv` est réécrit avec tout l'historique à chaque nouvelle
  mesure et à chaque démarrage du service, et recréé s'il a été supprimé. Séparateur `;` et virgule décimale, pour
  Excel en français. Après une *désinstallation puis réinstallation* (pas une mise à jour), Android interdit de
  réécrire l'ancien fichier : un nouveau `orion_xs_historique (1).csv` est créé, et l'ancien n'est plus mis à jour.
- **Diagnostic** : l'onglet Aide, puis le bouton vert, ouvre WhatsApp avec un rapport : vérifications système,
  état du scan, appareils Victron vus (avec « Clé incorrecte » si la clé ne correspond pas), dernières mesures
  et journal interne.

## Lire un diagnostic

| Ce que tu vois | Cause probable |
|---|---|
| `Appareils BLE reçus : 0` | Bluetooth bloqué : autorisation, Bluetooth éteint, ou localisation coupée (Android ≤ 11) |
| BLE > 0 mais `annonces Victron : 0` | Trop loin du chargeur, ou « Instant readout » désactivé dans VictronConnect |
| Appareil `Orion XS → Clé incorrecte` | Mauvaise clé : renvoie-la |
| Appareil `→ OK`, mais l'historique a des trous | L'app a été mise en veille : vérifie « Batterie non restreinte » et « Arrière-plan autorisé » |
