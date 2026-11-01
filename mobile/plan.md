# Plan RépétiPro Mobile

## Objectif

Créer une première version mobile Android de RépétiPro, en français, pensée pour une utilisation quotidienne par un répétiteur au Bénin. Cette version fournit une expérience mobile cohérente et testable autour des quatre tâches principales : consulter le tableau de bord, gérer les élèves, suivre les séances et suivre les factures.

Le projet mobile reste séparé du dépôt Spring Boot existant pendant cette première étape. Les données de l’interface sont isolées derrière un module de données local afin de pouvoir remplacer ce module par une API RépétiPro authentifiée sans réécrire les écrans. La connexion réelle au backend et l’authentification par téléphone seront traitées dans une étape dédiée après stabilisation de l’interface.

## Architecture

- `app/` : routes Expo Router et quatre onglets principaux.
- `components/` : composants visuels réutilisables, cartes, badges et lignes métier.
- `lib/repetipro-data.ts` : types et données locales de démonstration, avec fonctions de formatage ; futur point d’entrée de l’API RépétiPro.
- `constants/` et `global.css` : tokens de couleurs, espacement et thème.
- `server/` et `drizzle/` : infrastructure du starter conservée pour l’intégration backend ultérieure.

## Décisions de design

### Styles envisagés

- **Carnet de terrain** (0,07) : interface sobre, tactile, avec cartes de suivi et accents turquoise.
- **Tableau administratif classique** (0,05) : densité élevée et nombreux tableaux.
- **Dashboard néon** (0,03) : contraste fort et effets décoratifs.

### Style retenu : Carnet de terrain

- **Mouvement** : produit utilitaire mobile inspiré d’un carnet de suivi, avec une hiérarchie calme et des actions immédiatement visibles.
- **Principes** : lisibilité au soleil, actions à une main, états explicites, peu de décoration.
- **Couleurs** : turquoise profond comme couleur de confiance et d’action, fond ivoire très léger pour éviter l’effet clinique, ambre réservé aux échéances et alertes.
- **Mise en page** : en-têtes courts, cartes empilées, listes avec information essentielle à gauche et action à droite ; pas de tableau large nécessitant un défilement horizontal.
- **Signatures** : pastilles d’initiales, barre de progression des encaissements, boutons arrondis à libellé verbal.
- **Interactions** : chaque action confirme son état localement ; les actions destructives ou irréversibles devront ensuite passer par une confirmation native contrôlée.
- **Animation** : transitions courtes et discrètes ; aucune animation décorative qui ralentit la saisie sur une connexion ou un téléphone modestes.
- **Typographie** : système sans-serif natif, titres de 26–30 px, titres de section 17–19 px, texte courant 14–16 px, chiffres financiers en semi-gras.
- **Voix** : directe, chaleureuse et locale. Exemples : « Voici votre journée » et « Tout est à jour pour le moment ».
- **Marque** : symbole de cahier ouvert associé à un chemin de progression, sans texte dans l’icône.
- **Couleur signature** : turquoise RépétiPro `#087F78`.

## Découpage de la première version

1. Accueil : date, résumé financier, séances du jour et raccourcis.
2. Élèves : recherche, liste, formulaire local d’ajout et indicateur de formule.
3. Séances : filtres temporels, présence et action de pointage.
4. Factures : filtres de statut, résumé encaissé et action de marquage payé.
5. Branding Android : icône carrée et configuration Expo cohérente.
6. Vérification : TypeScript, lint, tests existants, démarrage Expo Web et contrôle des deux listeners.

## Évolution vers la version connectée

Le module de données local sera remplacé par des appels vers une API REST ou tRPC du dépôt `repetipro`. Cette étape devra ajouter l’authentification par téléphone, les jetons sécurisés, le filtrage systématique par répétiteur côté serveur, la gestion des erreurs réseau et une stratégie de cache hors ligne. Aucune donnée de démonstration ne devra être présentée comme une donnée réelle après branchement du backend.
