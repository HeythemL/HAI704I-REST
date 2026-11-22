# TP3 REST — Explication Complète et Feuille de Route

## 🎯 Objectif Global

Construire **une application distribuée en REST sur HTTP** avec trois niveaux hiérarchiques :

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   COMPARATEUR   │────▶│    AGENCE(S)    │────▶│    HÔTEL(S)     │
│    (Client)     │     │   (API REST)    │     │   (API REST)    │
│                 │     │   Marge propre  │     │ Offres, Photos, │
│ Agrège offres   │     │ Réservations    │     │ Réservations    │
│ Classe par prix │     │ JPA/H2          │     │ JPA/H2          │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

**Règles d'or :**
- Chaque flèche = **appel HTTP entre processus distincts**
- Un hôtel ne connaît aucune agence ; une agence ne connaît aucun comparateur
- Chaque niveau ne parle qu'au niveau immédiatement inférieur
- **AUCUNE application unique** — trois processus séparés minimum

---

## 📋 Prérequis Techniques

| Élément | Version |
|---------|---------|
| JDK | Java 17+ |
| Maven | 3.9+ |
| Spring Boot | 4.1.1 |
| Dépendances | `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `spring-boot-starter-restclient`, `spring-boot-starter-data-jpa`, `h2` (runtime) |
| Outils test | `curl`, Postman |
| Interface | CLI (Java standard), GUI en bonus |

> ⚠️ **Attention Spring Boot 4** : Les starters ont changé de nom (`webmvc` au lieu de `web`, test découpé par module). Voir Annexe 1.

---

## 🗺️ Feuille de Route Détaillée (Partie A — Obligatoire)

### **A0 — Mise en route : Observer HTTP réel** ✅ *Sans code*
| Sous-étape | Action | Validation |
|------------|--------|------------|
| A0.1 | Appeler API IGN (geocodage) avec `curl -i` puis `curl -v` | Voir méthode, URI, status, headers, body, `Accept` auto |
| A0.2 | Observer GitHub API (ETag, Cache-Control, rate limits) + httpbingo.org (toutes méthodes, codes erreur) | Obtenir 200, 404, 304, 409, 422, 503 |
| A0.3 | Refaire 3 appels dans Postman, retrouver tous les éléments | Maîtrise lecture échange HTTP |

**🎯 Point de contrôle A0** : Savoir lire un échange HTTP brut, avoir obtenu 200/404/304/erreur délibérée.

---

### **A1 — Conception avant code** 📝 *Document de conception (PAS de code)*
Produire un **tableau complet** :

| Ressource | URI | Pourquoi ressource ? |
|-----------|-----|---------------------|
| Offres | `GET /api/v1/offres` | Collection filtrable (ville, dates, nb personnes) |
| Offre | `GET /api/v1/offres/{id}` | Ressource identifiable |
| Photo | `GET /api/v1/offres/{id}/photo` | Sous-ressource, type média distinct |
| Réservations | `POST /api/v1/reservations` | Création, renvoie `Location` |
| Réservation | `GET/PUT/DELETE /api/v1/reservations/{id}` | Cycle de vie complet |

Pour chaque **(ressource, méthode)** :
- Signification métier
- Sûre ? Idempotente ?
- Code succès + codes échec possibles
- Représentation (champs exposés vs cachés)
- Relations (liens HATEOAS)

**A1.2 — Anti-modèle** : Expliquer pourquoi `POST /chercherOffres`, `POST /reserverChambre`, `POST /annulerReservation` perd :
- Cache HTTP intermédiaire
- Bookmarking client
- Rejouabilité sans risque (idempotence)
- *Exception* : POST d'action justifié quand aucune ressource ne modélise l'action (ex: paiement)

**🎯 Point de contrôle A1** : Tableau écrit, justifié, distinction sûre/idempotente correcte.

---

### **A2 — API Hôtel : Lecture + Photo** 🏨
| Tâche | Détail technique |
|-------|------------------|
| A2.1.1 | Projet `hotel` sur start.spring.io (webmvc, validation, restclient, data-jpa, h2) |
| A2.1.2 | `GET /api/v1/offres?ville=&dateDebut=&dateFin=&nbPersonnes=` |
| A2.1.3 | `GET /api/v1/offres/{id}` → 404 si inconnu |
| A2.1.4 | **Enveloppe collection** : `{ "offres": [...], "_links": {...} }` pour pagination future |
| A2.1.5 | **Séparation 3 couches** : DTO (représentation) ↔ Domaine (métier) ↔ Entité JPA (persistance) |
| A2.2.1 | Photo comme sous-ressource : `GET /api/v1/offres/{id}/photo` |
| A2.2.2 | `Content-Type: image/jpeg`, `Content-Length` exact, `ETag` (hash contenu) |
| A2.2.3 | Lien dans JSON offre : `"photo": { "href": "/api/v1/offres/123/photo", "type": "image/jpeg" }` |
| A2.2.4 | `Accept: application/json` sur photo → **406 Not Acceptable** (bon code) |

**🎯 Point de contrôle A2** : Collection enveloppée 200, 404 offre inconnue, photo avec type/longueur/ETag, lien dans JSON, pas de champ stockage exposé.

---

### **A3 — Sémantique HTTP des erreurs** ❌
| Exigence | Implémentation |
|----------|----------------|
| Centraliser | `@RestControllerAdvice` + `ProblemDetail` (RFC 9457) |
| Valider entrée | `@Valid` + distinction : 400 (illisible) vs 422 (syntaxique mais invalide) |
| Codes requis | **400, 404, 405, 409, 415, 422** minimum — chacun provoqué & relevé `curl` |
| 405 | Doit porter header `Allow` (vient de `@RequestMapping` methods) |

**Règle** : Chaque code = situation réelle, justifié vs voisin (401≠403, 400≠422, 404≠409).

**🎯 Point de contrôle A3** : Toutes erreurs en `application/problem+json` (type, title, status, detail), relevés curl par code, justifications.

---

### **A4 — Agence : Client REST explicite** 🏢
| Tâche | Détail |
|-------|--------|
| A4.1 | Projet `agence` **séparé** de `hotel` |
| A4.2 | `RestClient` injecté par constructeur |
| A4.3 | Lire **status + headers**, pas juste body |
| A4.4 | Traduire échecs distants : **injoignable** (timeout/connect) ≠ **lent** (read timeout) ≠ **500** (erreur serveur) |
| A4.5 | **Finals d'attente explicites** (connection + read) |
| A4.6 | **Séparation** : Métier n'importe rien de Spring Web |

**Interdits** : Adresse en static mutable, `Thread.sleep`, `System.exit`, couche client qui cache les codes status.

**🎯 Point de contrôle A4** : Agence obtient offres hôtel, distingue succès/refus métier/panne, hôtel arrêté = pas d'exception brute ni blocage infini.

---

### **A5 — Réservation : Écriture, Auth, Idempotence, Concurrence** 🔐
#### A5.1 — Créer réservation
- `POST /api/v1/reservations` → **201 Created** + header `Location: /api/v1/reservations/{id}`
- `GET /api/v1/reservations/{id}` rend la réservation
- **Auth agence** : Header custom (ex: `X-Agency-Secret`) — 401 + `WWW-Authenticate` si inconnu
- Conflits : **409** (offre déjà réservée) ≠ **404** (offre inexistante)

#### A5.2 — Idempotence (clé fournie par agence)
- Header `Idempotency-Key` (ou `Idempotency-Key`)
- Même clé = même réservation, **pas de doublon**
- Code : **200** (ou 201 pour 1er, 200 pour rejou) — pas 201 deux fois
- Propagation : Comparateur → Agence → Hôtel

#### A5.3 — Concurrence optimiste
- `ETag` sur réservation (change à chaque modif)
- `PUT /api/v1/reservations/{id}` + `If-Match: <etag>`
- ETag périmé → **412 Precondition Failed** (pas écrasement silencieux)
- Annulation `DELETE` : idempotente (répétition = même effet), mais codes peuvent différer (200 vs 204)

**🎯 Point de contrôle A5** : Réservation CRUD complète, auth refuse inconnue, rejeu clé = pas doublon, modif état périmé = 412.

---

### **A6 — Composition + Résilience** 🛡️
| Tâche | Détail |
|-------|--------|
| A6.1 | Liste hôtels **configurable** (application.yml / env var) — **pas de recompilation** |
| A6.2 | 3 hôtels : normal, **lent** (> timeout), **arrêté** |
| A6.3 | **Résultat partiel** : offres obtenues + liste nommée échecs + causes |
| A6.4 | Hôtel 500 → incident fournisseur, pas faute agence |
| A6.5 | Si **rien** obtenu → code = problème amont (**502 Bad Gateway** ou **503**) |

**Cœur du TP** : États partiels, pas binaire up/down. Jeter réponses valides = échec. Cacher défaillance = pire.

**🎯 Point de contrôle A6** : Hôtel arrêté + lent → agence répond vite, rend offres dispo, dit qui a échoué et pourquoi.

---

### **A7 — API Agence + Comparateur** 🔄
| Tâche | Détail |
|-------|--------|
| A7.1 | Agence = **serveur** (pour comparateur) + **client** (pour hôtels) |
| A7.2 | **Marge appliquée** : prix publié ≠ prix hôtel, prix base **ne fuit pas** |
| A7.3 | IDs offre **uniques globalement** (préfixe agence/hôtel) car hôtels peuvent avoir mêmes IDs |
| A7.4 | Comparateur interroge **plusieurs agences**, agrège, **classe par prix** |
| A7.5 | 2 agences marges différentes → même chambre = 2 prix |
| A7.6 | Comparateur survit agence absente (même logique A6) |

**Règle** : 3 processus distincts, pas de code métier partagé, pas de BDD partagée.

**🎯 Point de contrôle A7** : ≥2 hôtels, ≥2 agences, 1 comparateur simultanés, même chambre 2 prix, arrêt agence ≠ arrêt comparateur.

---

### **A8 — Persistance JPA/H2 + CLI** 💾
#### A8.1 — Mémoire → JPA/H2
- Interface `Repository` déjà existante → 2e implémentation JPA/H2
- **Entité JPA ≠ Représentation** — entité n'apparaît **jamais** dans contrôleur/réponse
- **Zéro changement** métier/contrôleur/représentations (frontière bien placée)
- Démontrer : réservations survivent redémarrage service

#### A8.2 — CLI interactive
- (i) Chercher/consulter offres, (ii) Réserver/retrouver réservation
- **Vrai parcours utilisateur** (pas suite appels en dur)
- CLI **ne construit AUCUNE URL**, **ne connaît AUCUN code status** → vit dans couche client
- Tests métier automatisés **sans réseau ni BDD**
- Rejouer chaîne complète **bout en bout**, tous processus séparés, conserver trace

**🎯 Point de contrôle A8** : JPA/H2 persistant, CLI parcours complet, tests passent, chaîne comparateur→agence→hôtel fonctionne.

---

## 🎁 Parties Bonus (Optionnelles, max +4 pts)

| Partie | Description | Points |
|--------|-------------|--------|
| **B** | Base externe MySQL **ou** MongoDB (3e implémentation repo, config externalisée, secrets pas versionnés, données survivent arrêt complet) | +1 |
| **C** | OpenAPI généré depuis code (springdoc), interface Swagger UI, **vérifié** sur 3 endpoints vs curl réel | +1 |
| **D** | Cache conditionnel : traces 200→304, mesure octets/temps, header `Cache-Control` justifié | +0,5 |
| **E** | HATEOAS : liens selon état (ex: annuler si confirmée), **client qui suit liens** (pas reconstruct URI) | +0,5 |
| **F** | GUI réutilisant **même couche client** que CLI (pas d'appel HTTP dans handlers) | +1 |

> **Condition** : Bonus attribués **seulement si Partie A fonctionne en processus séparés**.

---

## 📦 Livrables (Archive ZIP)

```
NOM1_NOM2_HAI704I_TP3.zip
├── Compte-Rendu.pdf          # Rapport ingénierie (voir §4.1)
├── README.pdf                # Procédure build/run complète (§4.3)
├── source/
│   ├── hotel/
│   ├── agence/
│   └── comparateur/
├── releves/                  # Échanges HTTP A0, A3, A5 (curl -i)
├── video/
│   └── demonstration.mp4     # Ou liens.txt si externe
└── liens.txt                 # Dépôt Git, vidéo (si pas dans CR)
```

### Compte-Rendu — Sections obligatoires
1. Page titre complète (université, UE, TP, enseignant, année, date, **2 membres** : nom, prénom, n° étudiant, parcours)
2. Table des matières
3. Rappel objectifs
4. Compte-rendu étape par étape (chapitres/sections)
5. **Conception A1** (tableau + justifications) — **PIÈCE MAÎTRESSE**
6. Pour chaque étape : implémentation, décisions, justifications, validation
   - 4 décisions **explicites** : choix ressources (A1), codes statut refus (A3), idempotence rejeu (A5), distinction refus/panne (A4/A6)
7. Passage mémoire → JPA/H2 : classes changées ? frontière ?
8. Schémas utiles (classes, séquence HTTP, composants/déploiement)
9. Relevés HTTP A0, A3, A5 commentés
10. Extraits code choisis (appuient explication)
11. Synthèse : fonctionne / ne fonctionne pas / difficultés
12. Regard critique : améliorations possibles
13. **Section IA** : utilisé ? lesquels ? tâches ? influence ? vérification ? ce qui est de vous
14. Références

### README — Contenu minimum
| Projet | Exécution |
|--------|-----------|
| Rôle chaque projet + relations | JDK + Maven versions |
| Structure sources | Commandes build (ordre) |
| Dépendances + versions | Ordre démarrage processus |
| Config adresses hôtels (agence) | Ports chaque hôtel/agence |
| Config secret agence | Lancer plusieurs hôtels/agences |
| Organisation client (console, métier, HTTP) | Lancement CLI, menu, saisie, arrêt |
| BDD : JDBC URL, reset base vide | Jeu données démo |
| Bonus réalisés | Install/base externe si bonus |
| IDE/éditeur + version | |

### Vidéo — Doit montrer
- [ ] Démarrage ≥2 hôtels → ≥2 agences → comparateur (ordre)
- [ ] CLI interactive : recherche → choix offre → réservation → référence
- [ ] Comparaison prix entre hôtels, dépendance agence
- [ ] ≥1 refus métier modélisé + refus secret erroné
- [ ] ≥1 défaillance partielle : hôtel éteint/lent → agence rend reste + dit qui/quoi
- [ ] Rejeu réservation → pas doublon
- [ ] Persistance : arrêt service → redémarrage → réservations là
- [ ] Arrêt propre
- [ ] Bonus si réalisés

---

## ✅ Checklist de Vérification Finale (Avant Dépôt)

### Architecture
- [ ] 9 points contrôle A0–A8 franchis
- [ ] **Processus séparés** : ≥2 hôtels, ≥2 agences, 1 comparateur
- [ ] **Aucune référence croisée** : agence n'importe pas hotel, comparateur n'importe pas hotel/agence
- [ ] **Entité JPA invisible** : aucun `@Entity` dans contrôleur/réponse
- [ ] **Métier sans Spring Web** : service compile sans `spring-webmvc`
- [ ] **CLI sans URL/status** : couche client gère tout

### Fonctionnel
- [ ] JPA/H2 persistant, réservations survivent redémarrage
- [ ] CLI parcours complet sans modifier code
- [ ] **Idempotence** : rejeu même clé = pas 2e réservation, pas 2e conso chambre
- [ ] **Concurrence** : modif état périmé = 412 (pas écrasement)
- [ ] **Auth** : secret erroné = refus, pas réservation
- [ ] **Résilience** : hôtel éteint + lent → agence rend offres dispo + liste échecs causes

### Sécurité / Propreté
- [ ] Aucune donnée paiement / secret réel dans code, tests, relevés, CR, vidéo
- [ ] Secrets agence externalisés (paramètre lancement / config fichier)
- [ ] Relevés HTTP A0, A3, A5 dans archive + commentés dans CR

### Livrables
- [ ] Compte-Rendu.pdf : titre complet 2 membres, TOC, conception A1, justifications, synthèse, regard critique, section IA, références
- [ ] README.pdf : tout tableau §4.3 rempli
- [ ] Vidéo accessible, URL dans CR si externe
- [ ] URL Git dans CR si dépôt utilisé
- [ ] Archive nommée `NOM1_NOM2_HAI704I_TP3.zip`, **sans artefacts** (pas target/, pas *.mv.db, pas .class/.jar, pas caches IDE)

---

## 🗓️ Planning Sugéré (Binôme)

| Semaine | Focus | Livrable intermédiaire |
|---------|-------|------------------------|
| 1 | A0–A1 : Observation HTTP + Conception complète | Tableau ressources/URI/méthodes/codes/représentations |
| 2 | A2–A3 : Hôtel lecture + photo + erreurs ProblemDetail | Hôtel fonctionnel, relevés curl A3 |
| 3 | A4–A5 : Agence client + Réservation (auth, idempotence, concurrence) | Agence + Hôtel réservations, rejeu prouvé |
| 4 | A6–A7 : Composition résiliente + Agence API + Comparateur | 3 niveaux tournants, prix doubles |
| 5 | A8 : JPA/H2 + CLI + Tests + Chaîne bout en bout | App finale persistante, CLI interactive |
| 6 | Bonus (si temps) + **Rédaction CR + README + Vidéo** | Archive prête à déposer |

---

## 💡 Conseils Clés (Pièges Fréquents)

| Piège | Solution |
|-------|----------|
| **Application unique** | 3 projets Maven, 3 `main()`, 3 ports, lancés séparément |
| **Entité JPA dans réponse** | DTO ↔ Mapper ↔ Entité — **jamais** `@Entity` dans `@RestController` |
| **Codes HTTP approximatifs** | Juste chaque code : situation concrète, pourquoi pas voisin |
| **Idempotence oubliée** | Header `Idempotency-Key` propagé bout en bout, stocké en BDD |
| **Pas de timeouts** | `RestClient` : `connectTimeout` + `readTimeout` obligatoires |
| **CLI qui connaît URLs** | Couche client = seule à connaître URLs, codes, headers |
| **Bonus sans A fonctionnel** | Bonus **annulés** si processus séparés ne marchent pas |
| **Archive > 50 Mo** | Nettoyer `target/`, `.class`, BDD, ou lien Git dans CR |
| **Section IA manquante** | 1 point facile — la remplir honnêtement |

---

## 📚 Ressources Fournies (À Consulter)

1. **Annexe 1** — `REST AVEC SPRING BOOT : WORKFLOW, PIÈGES ET AIDE GRADUÉE`
   - Env, start.spring.io, multi-ports, curl/Postman, lecture HTTP, pièges SB4, aide A0–A8, procédure "rien ne marche"
2. **Annexe 2** — `SERVICE EMPLOYÉ REST : SPRING BOOT, HTTP/JSON ET PERSISTANCE`
   - Exemple complet : séparation DTO/Domaine/Entité, 5 méthodes HTTP, 201+Location, ETag, conditionnel, idempotence, ProblemDetail, RestClient, tests, relevés curl
3. **Code démo** — `employe-rest-demo.zip` (2 projets : service + client)

---

## 🎓 Ce Qui Est Vraiment Évalué

> **Transparence, compréhension démontrée, cohérence CR/Code/Vidéo, capacité à justifier choix, reproductibilité.**

Pas la détection IA. Un dossier **cohérent et assumé**, avec déclaration honnête, **n'est jamais pénalisé** pour usage assistant.

---

*Document généré pour le TP3 HAI704I — Architectures Logicielles Distribuées — 2026-2027*