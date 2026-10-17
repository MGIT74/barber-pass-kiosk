# Barber Pass Kiosk

Application Android autonome en plein écran pour la page « Sans RDV » de Barber Pass. Le site Barber Pass original n'est pas modifié.

## Installation / APK
Le workflow GitHub Actions **Build Android APK** compile un APK debug installable sans abonnement. Ouvrir Actions, sélectionner le dernier run, télécharger l'artifact `BarberPassKiosk-debug-apk`, dézipper puis installer `app-debug.apk` sur la tablette.

## Configuration
Au premier lancement, saisir l'URL du salon (exemple : https://app.thebarberone.com/kiosk.html?salon=the-barber-one-oyonnax). Pour changer de salon, effectuer six appuis rapides dans le coin supérieur gauche puis entrer le PIN local. La configuration est propre à chaque tablette.

## Sécurité
Affichage immersif, mais **pas un verrouillage Android certifié** : les gestes système peuvent permettre de quitter la borne. Pour un verrouillage strict, configurer Android Device Owner/Lock Task sur une tablette dédiée. Ne pas utiliser de session administrateur Barber Pass sur la borne.

## Android 7 (Samsung SM-T810)
Télécharger l'artifact ZIP depuis GitHub Actions sur ordinateur, **extraire** le ZIP puis transférer uniquement le fichier `app-debug.apk` sur la tablette. Ne pas essayer d'installer le ZIP. Version minimale Android 7.0 (API 24).
