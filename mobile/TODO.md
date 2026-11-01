# TODO RépétiPro Mobile

## Interface mobile métier

- [ ] L’application Android doit proposer une navigation en français pensée d’abord pour le téléphone, avec les rubriques Accueil, Élèves, Séances et Factures.
- [ ] L’accueil doit afficher la date, un résumé des élèves actifs, le montant à encaisser, les séances du jour et des raccourcis vers les tâches fréquentes.
- [ ] La rubrique Élèves doit afficher les élèves sous forme de liste lisible, permettre une recherche locale et proposer un ajout guidé sans tableau horizontal.
- [ ] La rubrique Séances doit afficher les séances du jour et à venir, permettre de filtrer les périodes et proposer le pointage de la présence.
- [ ] La rubrique Factures doit afficher les montants en FCFA, distinguer les factures payées, en attente et en retard, et permettre de marquer une facture payée dans l’interface.

## Fiabilité et cohérence

- [ ] Les états de chargement, les listes vides et les erreurs doivent être compréhensibles en français.
- [ ] Les actions locales doivent donner un retour visuel immédiat et ne pas dépendre d’une animation décorative ou d’une connexion rapide.
- [ ] Le modèle de données local doit être isolé afin de pouvoir être remplacé par une API RépétiPro authentifiée sans réécrire les écrans.
- [ ] Le projet doit conserver les identifiants Expo/Android générés par le starter et fournir une icône carrée RépétiPro sans coins arrondis intégrés.

## Vérification et livraison

- [ ] Le projet doit passer le contrôle TypeScript et le lint disponibles.
- [ ] Metro doit démarrer sur le port 8081 et le serveur API du starter sur le port 3000 avant l’aperçu mobile.
- [ ] Le projet doit être enregistré sur sa branche de travail avant toute proposition de fusion vers `main`.
- [ ] Un APK Android ne sera annoncé comme disponible qu’après un build Android réel confirmé par le tableau de bord ; l’aperçu Expo Web ne suffit pas à prouver la présence d’un APK.

## Étape suivante

- [ ] Ajouter l’API mobile RépétiPro au dépôt Spring Boot et remplacer les données locales par l’authentification et les données du compte connecté.
