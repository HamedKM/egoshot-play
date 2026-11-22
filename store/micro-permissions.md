# EgoShot : autorisation du micro (App Store et Play Store)

Le micro sert uniquement au chat vocal entre les joueurs d'une même partie.
Le son passe en direct d'un téléphone à l'autre : il n'est ni enregistré, ni envoyé à nos serveurs, ni gardé.

## Ce qui est déjà en place dans le jeu

- Le micro n'est demandé qu'au premier appui sur 🎤, jamais à l'ouverture de l'appli.
- Avant la question du téléphone, une fenêtre explique à quoi sert le micro (« Parler avec les joueurs »).
  Le joueur peut répondre « Pas maintenant ».
- Le joueur peut couper son micro à tout moment (bouton 🎤).
- Si le micro a été refusé, le jeu explique comment l'autoriser et, sur Android, ouvre directement les réglages d'EgoShot.

## Android (Play Store) : déjà dans l'appli

Dans `android/app/src/main/AndroidManifest.xml` :

- `android.permission.RECORD_AUDIO` : le micro, demandé au premier appui sur 🎤 ;
- `android.permission.MODIFY_AUDIO_SETTINGS` : pour régler le son pendant le chat vocal ;
- `android.hardware.microphone` en `required="false"` : l'appli reste installable sur un appareil sans micro (télé Android).

Réponses pour la fiche « Sécurité des données » de la Play Console :

- Données audio collectées : **non**. Le son va en direct d'un joueur à l'autre, rien n'est enregistré ni envoyé à nos serveurs.
- Usage du micro : chat vocal entre joueurs pendant une partie.

## iPhone (App Store) : à mettre dans l'appli iOS le jour où elle est créée

Dans `Info.plist`, clé `NSMicrophoneUsageDescription` (texte affiché par l'iPhone dans sa question) :

- Français : « EgoShot utilise ton micro pour parler avec les joueurs de ta partie. Rien n'est enregistré. »
- Anglais : "EgoShot uses your microphone so you can talk with the players in your game. Nothing is recorded."

Apple refuse les applis qui demandent le micro sans expliquer pourquoi : ce texte est obligatoire.

## Phrase pour la politique de confidentialité

> Microphone : EgoShot demande l'accès au micro uniquement si tu actives le chat vocal pendant une partie.
> Le son est transmis en direct aux autres joueurs de la partie. Il n'est ni enregistré, ni stocké, ni utilisé à d'autres fins.
> Tu peux retirer cette autorisation à tout moment dans les réglages de ton téléphone.
