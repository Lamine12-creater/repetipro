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
- **Paiement** : en V1, le parent paie par Mobile Money sur le numéro du répétiteur
  (réglable dans *Paramètres*) ; le répétiteur marque ensuite la facture « Payé » en notant la
  référence de la transaction. Pas de paiement automatique (un agrégateur comme FedaPay demande
  des documents d'entreprise).
- **Formule gratuite** : 3 élèves actifs maximum. Au-delà, un message propose la formule
  **Solo** (1 500 FCFA/mois, tarif indicatif) avec les instructions de paiement Mobile Money.
- **Activer Solo** : un administrateur ouvre `/admin` et clique « Activer Solo » sur le compte.
  Les administrateurs sont les numéros listés dans la variable `ADMIN_TELEPHONES`.
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

## 5. Limites connues (V1)

- Paiements Mobile Money **manuels** (pas de confirmation automatique).
- Pas de mot de passe oublié : un administrateur doit recréer le compte (à ajouter).
- Pas d'envoi WhatsApp automatique : les boutons ouvrent WhatsApp avec le message prêt,
  le répétiteur appuie sur « Envoyer ».
- La police Inter est chargée depuis Google Fonts ; sans réseau, la police du téléphone est utilisée.
