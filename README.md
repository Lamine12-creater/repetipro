# RépétiPro

Application web pour les **répétiteurs indépendants au Bénin** : élèves, séances, présences,
compte rendu envoyé aux parents sur **WhatsApp**, factures mensuelles en **FCFA** (PDF) et
suivi des paiements **Mobile Money**. Interface en français, pensée d'abord pour le téléphone.

Technologies : Java 21, Spring Boot 3 (Web, Thymeleaf, Security, Data JPA, Validation),
H2 (en local), PostgreSQL (en production, Neon), OpenPDF (factures PDF).

![Tableau de bord, séance, factures](captures/apercu.png)

---

## 1. Lancer l'application sur votre ordinateur

### a) Installer Java 21 et Maven (une seule fois)

- **Windows** : installez le JDK 21 « Temurin » depuis <https://adoptium.net/>
  (cochez « Set JAVA_HOME »), puis Maven depuis <https://maven.apache.org/download.cgi>
  (dézippez-le, ajoutez son dossier `bin` à la variable `Path`).
- **Ubuntu / Debian** : `sudo apt install openjdk-21-jdk maven`
- **macOS** (Homebrew) : `brew install openjdk@21 maven`

Vérifiez dans un terminal :

```bash
java -version   # doit afficher 21
mvn -version
```

### b) Démarrer

```bash
cd repetipro
mvn spring-boot:run
```

Ouvrez <http://localhost:8080>. Le profil **dev** est utilisé par défaut :

- la base H2 est un fichier dans `./data/` (supprimez ce dossier pour repartir de zéro) ;
- un **compte démo** est créé, avec des données **fictives** :
  téléphone `01 00 00 00 00`, mot de passe `demo1234` ;
- ce compte démo est aussi **administrateur** (page `/admin`).

Autres commandes utiles :

```bash
mvn test              # lance les tests
PAIEMENT_SIMULATION=true mvn spring-boot:run   # avec la caisse de paiement simulée (section 5)
mvn package           # crée target/repetipro.jar (lance aussi les tests)
java -jar target/repetipro.jar
```

---

## 2. Comment le code est organisé

```
src/main/java/bj/repetipro/
├── RepetiproApplication.java   point de départ (fuseau horaire Africa/Porto-Novo)
├── model/        les "objets métier" enregistrés en base : Repetiteur, Eleve, Seance, Facture…
├── repository/   l'accès à la base (Spring Data écrit les requêtes à partir du nom des méthodes)
├── service/      les règles : limite gratuite, calcul des factures, messages WhatsApp, PDF…
├── web/          les contrôleurs (une URL -> une méthode -> une page) et les formulaires
├── config/       sécurité (connexion), base de données de production, données de démo
└── util/         petits outils : numéros béninois, liens wa.me, montants en FCFA
src/main/resources/
├── templates/    les pages HTML (Thymeleaf) ; fragments.html = en-tête, menu, messages
├── static/css/style.css   tout le design
└── application*.properties   la configuration (commune, dev, prod)
```

Le chemin d'une requête : le navigateur appelle une URL → un **contrôleur** (`web/`) →
un **service** (`service/`) applique les règles → un **repository** lit/écrit en base →
le contrôleur choisit une **page** (`templates/`) qui affiche le résultat.

**Sécurité** : connexion par téléphone + mot de passe (haché avec BCrypt). Chaque requête en
base est filtrée par l'identifiant du répétiteur connecté : un répétiteur ne peut ni voir ni
modifier les élèves, séances ou factures d'un autre (il reçoit une page 404).

---

## 3. Règles de fonctionnement

- **Facture mensuelle** : une facture par élève actif et par mois, d'un montant égal à son
  **tarif mensuel**. Le nombre de séances réalisées (présent ou en retard) est affiché à titre
  d'information. Échéance : le **10 du mois suivant** ; passé cette date, une facture non payée
  s'affiche « En retard ». Pourquoi ce choix ? La plupart des répétiteurs fixent un forfait
  mensuel avec les parents : un montant prévisible évite les discussions.
- **Paiement des factures** : le parent paie par Mobile Money sur le numéro du répétiteur
  (réglable dans *Paramètres*) ; le répétiteur marque ensuite la facture « Payé » en notant la
  référence de la transaction (pas de paiement en ligne des factures, voir section 5).
- **Formule gratuite** : 3 élèves actifs maximum. Au-delà, un message propose la formule
  **Solo** (1 500 FCFA/mois).
- **Payer Solo en ligne** (si FedaPay est configuré, voir section 5) : bouton
  « Payer 1 500 FCFA par Mobile Money » sur `/formule`. Chaque paiement confirmé donne **un mois**
  de Solo, ajouté à la fin du mois en cours s'il n'est pas terminé. À la date de fin, le compte
  retombe sur la limite gratuite (rien n'est effacé).
- **Activer Solo à la main** (transfert Mobile Money manuel, toujours proposé) : un
  administrateur ouvre `/admin` et clique « Activer Solo » sur le compte ; Solo est alors actif
  sans date de fin. Les administrateurs sont les numéros listés dans la variable `ADMIN_TELEPHONES`.
- **Archiver** un élève le retire des listes et de la limite gratuite, sans perdre l'historique.

---

## 4. Mettre en ligne gratuitement (Render + Neon)

Render héberge l'application (offre gratuite : 512 Mo de RAM, mise en veille après 15 minutes
sans visite, réveil en ~1 minute). Neon fournit la base PostgreSQL gratuite (0,5 Go), qui
n'expire pas (la base gratuite de Render, elle, est supprimée après 30 jours).

### Étape 1 : la base de données sur Neon

1. Créez un compte sur <https://neon.tech> puis un projet (région la plus proche : Europe).
2. Dans *Connection Details*, copiez la chaîne de connexion, de la forme
   `postgresql://utilisateur:motdepasse@ep-xxxx.eu-central-1.aws.neon.tech/neondb?sslmode=require`.

### Étape 2 : le code sur GitHub

```bash
git remote add origin https://github.com/Lamine12-creater/repetipro.git
git push -u origin main
```

### Étape 3 : l'application sur Render

1. Créez un compte sur <https://render.com> et reliez votre GitHub.
2. *New* → *Web Service* → choisissez le dépôt `repetipro`.
3. *Language / Runtime* : **Docker** (Render utilise le `Dockerfile` du projet). *Instance* : **Free**.
4. Dans *Environment Variables*, ajoutez :

| Variable | Valeur |
|---|---|
| `DATABASE_URL` | la chaîne Neon copiée à l'étape 1 |
| `ADMIN_TELEPHONES` | votre numéro, ex. `0197000000` (plusieurs : séparés par des virgules) |
| `PAIEMENT_MOMO_NUMERO` | le numéro où les répétiteurs paient Solo, ex. `01 97 00 00 00` |
| `PAIEMENT_MOMO_NOM` | le nom affiché par l'opérateur |
| `REMEMBER_ME_KEY` | une longue phrase secrète au hasard (pour « Rester connecté ») |
| `APP_BASE_URL` | l'adresse du site, ex. `https://repetipro-xxxx.onrender.com` (sans `/` final) |
| `FEDAPAY_SECRET_KEY` | *(facultatif)* clé API secrète FedaPay, voir section 5 |
| `FEDAPAY_WEBHOOK_SECRET` | *(facultatif)* secret du webhook FedaPay, voir section 5 |
| `FEDAPAY_ENV` | *(facultatif)* `sandbox` (test, par défaut) ou `live` (argent réel) |

5. *Create Web Service*. Le premier déploiement prend quelques minutes. Votre site est ensuite
   disponible à l'adresse `https://repetipro-xxxx.onrender.com`.
6. Inscrivez-vous sur le site avec le numéro mis dans `ADMIN_TELEPHONES` : vous aurez accès
   à `/admin` (lien dans *Paramètres*).

Le `Dockerfile` active le profil `prod` (PostgreSQL), limite la mémoire Java à 350 Mo et règle
le fuseau horaire sur `Africa/Porto-Novo`. Les tables sont créées automatiquement au premier
démarrage. Aucune donnée de démonstration n'est créée en production.

Option : pour éviter la mise en veille, un service gratuit de « ping » (ex. UptimeRobot) peut
appeler le site toutes les 10 minutes.

---

## 5. Paiement en ligne de Solo (FedaPay : MTN MoMo, Moov Money, Celtiis Cash)

### Pourquoi FedaPay (et pas KkiaPay) ?

| | FedaPay | KkiaPay |
|---|---|---|
| Compte test sans IFU | oui : mode test dès l'inscription ([doc](https://docs.fedapay.com/fr/introduction/createaccount.md)) | oui : sandbox dès l'inscription ([doc](https://docs.kkiapay.me/v1/compte/activation-de-compte.md)) |
| Intégration côté serveur (Java) | API REST complète : créer la transaction, obtenir le lien de paiement, relire le statut ([doc](https://docs.fedapay.com/fr/integration-api/collects-management.md)) | surtout un widget JavaScript ; SDK serveur PHP / Python / Node.js seulement, pour vérifier une transaction ([doc](https://docs.kkiapay.me/v1/plugin-et-sdk/admin-sdks-server-side.md)) |
| Webhooks signés | oui, en-tête `X-FEDAPAY-SIGNATURE` (HMAC-SHA256) ([doc](https://docs.fedapay.com/fr/integration-api/webhooks.md)) | oui (tableau de bord, menu Webhook) |
| Réseaux au Bénin | MTN, Moov, Celtiis (+ BMO, Coris, cartes) ([doc](https://docs.fedapay.com/fr/payment-methods.md)) | MTN, Moov, Celtiis ([numéros de test](https://docs.kkiapay.me/v1/compte/kkiapay-sandbox-guide-de-test.md)) |
| Frais Mobile Money Bénin | 1,80 % ([tarifs](https://www.fedapay.com/pricing)), retrait vers Mobile Money 0 FCFA | 1,5 % payés par le client par défaut, 7 000 FCFA par reversement bancaire ([frais](https://docs.kkiapay.me/v1/paiements/frais.md)) |
| Passage en réel | compte « Travailleur indépendant » : pièce d'identité + **IFU** ; Business : RCCM + IFU ([doc](https://docs.fedapay.com/fr/introduction/compte.md)) | consultant individuel : **IFU**, justificatif de domicile, pièce d'identité ; entreprise : RCCM + IFU ([doc](https://docs.kkiapay.me/v1/compte/activation-de-compte.md)) |

Choix : **FedaPay**, parce que tout se fait côté serveur en Java avec de simples appels REST
(pas de SDK Java nécessaire) et que les webhooks signés permettent d'activer Solo
automatiquement et de façon sûre. Sur 1 500 FCFA, FedaPay retient environ 27 FCFA.

### Comment ça marche

1. Sur `/formule`, le répétiteur clique **« Payer 1 500 FCFA par Mobile Money »**.
2. Le serveur crée un `Paiement` (statut *En attente*) puis une transaction FedaPay
   (`POST /v1/transactions` avec notre référence en `merchant_reference`) et demande le lien de
   paiement (`POST /v1/transactions/{id}/token`).
3. Le répétiteur est redirigé vers la page sécurisée de FedaPay, choisit MTN, Moov ou Celtiis
   et valide sur son téléphone.
4. FedaPay appelle le **webhook** `POST /paiement/webhook/fedapay`. La signature
   `X-FEDAPAY-SIGNATURE` est vérifiée (HMAC-SHA256, horodatage de moins de 5 minutes) ; une
   signature fausse ou absente reçoit une erreur 400 et ne change rien.
5. Si la transaction est *approved* et que le montant est bien 1 500 FCFA, le paiement passe à
   *Payé* et Solo est prolongé d'un mois. **Un même paiement ne prolonge Solo qu'une fois**,
   même si FedaPay renvoie l'événement plusieurs fois (il peut réessayer jusqu'à 10 fois).
6. FedaPay renvoie ensuite le répétiteur vers `/paiement/retour/{référence}`. Cette page ne
   croit pas le statut écrit dans l'adresse : elle **relit la transaction chez FedaPay**
   (`GET /v1/transactions/{id}`). Solo s'active donc même si le webhook tarde.

Sans `FEDAPAY_SECRET_KEY`, le bouton n'apparaît pas : seul le **transfert manuel** (numéro
`PAIEMENT_MOMO_NUMERO` + activation dans `/admin`) est affiché, comme avant. Quand FedaPay est
configuré, le transfert manuel reste affiché en dessous, au cas où.

### Tester sur votre ordinateur sans compte FedaPay (mode simulation)

```bash
PAIEMENT_SIMULATION=true mvn spring-boot:run
```

Le bouton mène alors à une **caisse de simulation** (bandeau « Simulation : aucun argent réel ») :
« Confirmer le paiement », « Simuler un échec » ou « Annuler ». La caisse fabrique un événement au
format FedaPay, le **signe** et le fait passer par le vrai traitement du webhook : tout le
parcours est testé. Ce mode ne fonctionne qu'avec le profil `dev` : en production
(`SPRING_PROFILES_ACTIVE=prod`, comme dans le `Dockerfile`) il est ignoré.

### Étape 1 : créer un compte FedaPay de test (gratuit, sans IFU)

1. Inscrivez-vous sur <https://fedapay.com> (bouton « S'inscrire »), confirmez votre e-mail.
2. Dans le tableau de bord, restez en **mode Test (Sandbox)** (interrupteur Test/Live).
3. Section **API** du menu : copiez la **clé secrète** de test (elle commence par `sk_sandbox_`).
   Ne la mettez jamais dans le code ni dans git ; si elle fuit, « Régénérer les clés API ».
   ([doc des clés](https://docs.fedapay.com/fr/dashboard/api.md))

### Étape 2 : déclarer le webhook

1. Le site doit être en ligne en HTTPS (Render) : FedaPay n'appelle pas `localhost`.
2. Tableau de bord FedaPay (mode Test) → **Webhooks** → *Ajouter un Webhook* :
   - URL : `https://repetipro-xxxx.onrender.com/paiement/webhook/fedapay`
   - événements : `transaction.approved`, `transaction.declined`, `transaction.canceled`
     (ou « tous les événements ») ;
   - laissez la vérification SSL activée.
3. Ouvrez le webhook créé et cliquez sur **« Click to reveal »** : c'est le secret
   (`wh_sandbox_...`) à mettre dans `FEDAPAY_WEBHOOK_SECRET`.
   ([doc des webhooks](https://docs.fedapay.com/fr/integration-api/webhooks.md))

### Étape 3 : variables sur Render

| Variable | Valeur en test |
|---|---|
| `FEDAPAY_SECRET_KEY` | `sk_sandbox_...` |
| `FEDAPAY_WEBHOOK_SECRET` | `wh_sandbox_...` |
| `FEDAPAY_ENV` | `sandbox` |
| `APP_BASE_URL` | `https://repetipro-xxxx.onrender.com` |

Ne mettez **pas** `PAIEMENT_SIMULATION` sur Render. Après redémarrage, le journal Render affiche
`Paiement en ligne : FedaPay (sandbox).`

Pour payer en test sur la page FedaPay, utilisez le numéro Mobile Money de test
**64000001** ou **66000001** (succès) ; tout autre numéro simule un échec (mode `momo_test`,
[doc](https://docs.fedapay.com/fr/integration-api/sending-requests.md)).

### Étape 4 : passer en argent réel (plus tard)

FedaPay demande de faire valider le compte (*Paramètres* → *Activer mon compte*)
([doc](https://docs.fedapay.com/fr/introduction/createaccount.md),
[types de comptes](https://docs.fedapay.com/fr/introduction/compte.md)) :

- compte **Travailleur indépendant** : pièce d'identité + copie, **numéro IFU** + justificatif
  IFU ; limite de 10 transactions par semaine, de 100 à 300 000 FCFA chacune ;
- compte **Business** (sans limite) : en plus, **RCCM** (registre du commerce) et nom du gérant ;
- dans tous les cas : engagement sur l'honneur, conditions générales et contrat de prestation
  FedaPay, à télécharger, signer, scanner et téléverser.

L'IFU s'obtient auprès de la Direction générale des impôts (DGI) du Bénin. Une fois le compte validé :
passez le tableau de bord en mode **Live**, recréez le webhook en Live (son secret est
différent : `wh_live_...`), puis sur Render mettez `FEDAPAY_SECRET_KEY=sk_live_...`,
`FEDAPAY_WEBHOOK_SECRET=wh_live_...` et `FEDAPAY_ENV=live`. Attention : avec le compte
Travailleur indépendant, 10 paiements par semaine au maximum.

### Pour plus tard

- **Paiement des factures par les parents en ligne** : pas fait volontairement. L'argent
  arriverait sur le compte FedaPay de RépétiPro et non sur celui du répétiteur ; il faudrait un
  compte Marketplace FedaPay (RCCM + IFU) avec un sous-compte par répétiteur, ou que chaque
  répétiteur ait son propre compte FedaPay.
- Rappel automatique quelques jours avant la fin du mois Solo.

---

## 6. Limites connues (V1)

- Factures des parents : paiement Mobile Money **manuel** (le répétiteur marque « Payé »).
- Pas de mot de passe oublié : un administrateur doit recréer le compte (à ajouter).
- Pas d'envoi WhatsApp automatique : les boutons ouvrent WhatsApp avec le message prêt,
  le répétiteur appuie sur « Envoyer ».
- La police Inter est chargée depuis Google Fonts ; sans réseau, la police du téléphone est utilisée.
