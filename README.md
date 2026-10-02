<div align="center">

# 🐟 Red Fish API Catalog

**Un punto di riferimento per scoprire, documentare e provare le API del tuo team.**

REST · gRPC · OpenAPI · Versionamento · Test API

Java 21 · Spring Boot 3.4 · PostgreSQL

</div>

---

Red Fish API Catalog raccoglie servizi, specifiche e informazioni operative in un unico portale. Organizza le API per area, esplora endpoint e modelli, confronta le revisioni e prova le chiamate HTTP direttamente dall'interfaccia.

L'applicazione si distribuisce come **un singolo JAR**, con asset frontend inclusi e persistenza su PostgreSQL. Non richiede una build Node.js né un'infrastruttura a container.

## Cosa puoi fare

| Area | Funzionalità |
| --- | --- |
| **Organizzare** | Gruppi e sottogruppi gerarchici, servizi REST/gRPC, tag, referenti e documentazione. |
| **Importare** | Specifiche Swagger/OpenAPI 2.0 e OpenAPI 3.x in JSON o YAML, file `.proto`, da upload o URL HTTP/HTTPS. |
| **Esplorare** | Endpoint, parametri, request body, risposte, autenticazione, schemi ad albero ed esempi generati. |
| **Navigare i modelli** | Schede dei modelli, riferimenti incrociati e filtro dei modelli usati da un endpoint. |
| **Versionare** | Storico delle revisioni, selezione della versione corrente, download dell'originale e diff di endpoint e modelli. |
| **Provare** | Console HTTP con header, parametri e body; risposta con status, durata e dimensione; generazione di comandi cURL. |
| **Seguire i servizi** | Ambienti multipli, health check periodici, link esterni, preferiti e commenti su servizi ed endpoint. |
| **Gestire gli accessi** | Ruoli ADMIN, EDITOR e VIEWER, ruoli personalizzati, permessi per gruppo e audit delle operazioni. |

La ricerca globale offre filtri per gruppo e sottogruppi, tipo e stato del servizio, con pagine da 20 risultati per sezione e limiti applicati dal database. La dashboard evidenzia ambienti non raggiungibili, servizi senza referenti e servizi senza un ambiente con URL, con collegamenti alle rispettive schede. Nome, logo e impostazioni operative sono personalizzabili dall'interfaccia, che include anche il tema scuro.

### Dal contratto alla chiamata

1. **Crea la struttura** del catalogo per dominio, team o area applicativa.
2. **Importa una specifica** e consulta gli endpoint e i modelli estratti.
3. **Aggiungi gli ambienti** del servizio, i referenti e i collegamenti utili.
4. **Prova una chiamata HTTP** oppure copia il comando generato.
5. **Importa una nuova revisione** e confrontala con la precedente.

La console esegue richieste HTTP dal server applicativo. Console e cURL utilizzano l'URL completo dell'ambiente selezionato; in modalità automatica viene scelto il primo ambiente con URL, altrimenti il primo server HTTP/HTTPS assoluto della specifica. Gli URL con variabili non risolte e i server relativi richiedono un URL di ambiente esplicito. Se manca un indirizzo, la console richiede l'URL completo e non genera un comando verso un host predefinito.

Il confronto delle revisioni evidenzia separatamente modifiche potenzialmente incompatibili: endpoint e modelli rimossi, nuovi parametri o corpi obbligatori, cambi di tipo, campi eliminati ed enum ristretti, anche negli schemi annidati. Si tratta di un aiuto alla revisione, non di una certificazione completa di compatibilità.

Per gRPC sono disponibili la consultazione dei contratti e la generazione di comandi `grpcurl`; la console HTTP non esegue RPC gRPC.

## Avvio rapido

### Prerequisiti

- **JDK 21**.
- **Maven 3.9+** per compilare il progetto.
- **PostgreSQL** raggiungibile e un database dedicato.

### 1. Crea il database

Da una sessione PostgreSQL con i permessi necessari:

```sql
CREATE DATABASE redfish_catalog ENCODING 'UTF8';
```

Flyway crea lo schema e i dati iniziali al primo avvio.

### 2. Compila

Dalla directory del progetto:

```sh
mvn clean package
```

Il comando esegue i test e produce `target/red-fish-api-catalog.jar`.

### 3. Configura e avvia

Con i valori predefiniti, l'applicazione si collega a PostgreSQL su `localhost:5432`, database `redfish_catalog`, con utente e password `postgres`.

```sh
java -jar target/red-fish-api-catalog.jar
```

Per impostare credenziali diverse, in PowerShell:

```powershell
$env:DB_USER = 'catalog'
$env:DB_PASSWORD = 'la-tua-password'
java -jar target/red-fish-api-catalog.jar
```

Oppure in una shell POSIX:

```sh
DB_USER=catalog DB_PASSWORD='la-tua-password' java -jar target/red-fish-api-catalog.jar
```

### 4. Completa il primo accesso

Apri [localhost:8080](http://localhost:8080). Il wizard iniziale permette di:

1. scegliere il nome dell'applicazione e creare il primo amministratore;
2. verificare il riepilogo e generare, facoltativamente, una struttura di gruppi di esempio.

Al termine il wizard viene disabilitato e il catalogo è pronto per il primo import.

### Specifiche di esempio

| File | Contenuto |
| --- | --- |
| [payments-sepa-openapi3.yaml](examples/payments-sepa-openapi3.yaml) | API REST per pagamenti SEPA, con parametri, enum, riferimenti agli schemi e autenticazione bearer. |
| [notification-v1.proto](examples/notification-v1.proto) | Servizio gRPC di notifica con message, enum, `oneof` e RPC unarie e streaming. |

Dalla voce **Importa specifica**, scegli il gruppo di destinazione e carica uno dei file.

## Configurazione

Le principali variabili d'ambiente sono definite in [application.yml](src/main/resources/application.yml).

| Variabile | Default | Scopo |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Host PostgreSQL. |
| `DB_PORT` | `5432` | Porta PostgreSQL. |
| `DB_NAME` | `redfish_catalog` | Nome del database. |
| `DB_USER` | `postgres` | Utente del database. |
| `DB_PASSWORD` | `postgres` | Password del database. |
| `PORT` | `8080` | Porta HTTP dell'applicazione. |
| `CATALOG_STORAGE` | `data/storage` | Directory dei file caricati, come il logo. |
| `THYMELEAF_CACHE` | `true` | Cache dei template; impostare `false` in sviluppo. |

Le impostazioni amministrative consentono di modificare nome, logo, limite di upload, timeout dei test API e intervallo dei controlli di salute. Il limite applicativo iniziale per gli upload è 10 MiB; i limiti multipart configurati sono 20 MB per file e 24 MB per richiesta.

Gli import da URL, i test API e gli health check utilizzano la connettività del server su cui gira il catalogo. Gli asset Bootstrap e le icone sono inclusi nel JAR tramite WebJars.

## Architettura

L'interfaccia è renderizzata lato server con Spring MVC e Thymeleaf. La logica applicativa gestisce import, versionamento, permessi e controlli dei servizi; Spring Data JPA persiste i dati su PostgreSQL.

| Componente | Tecnologia |
| --- | --- |
| Runtime e backend | Java 21, Spring Boot 3.4.5, Spring MVC |
| Interfaccia | Thymeleaf, Bootstrap 5.3.3, Bootstrap Icons, JavaScript e CSS |
| Sicurezza | Spring Security, password BCrypt, sessioni e protezione CSRF |
| Persistenza | Spring Data JPA, PostgreSQL, Flyway |
| Parsing | Swagger Parser e parser dedicato per i file proto |
| Build e test | Maven, JUnit, Mockito, Spring MockMvc |

```text
src/main/java/it/fn/redfish/catalog/
├── config/       Configurazione applicativa e sicurezza
├── domain/       Entità JPA ed enumerazioni
├── repo/         Repository Spring Data
├── security/     Autenticazione, permessi e filtri di accesso
├── service/      Logica applicativa
├── spec/         Parsing delle specifiche e rendering degli schemi
├── support/      Utilità ed eccezioni
└── web/          Controller MVC e form

src/main/resources/
├── db/migration/ Migrazioni SQL e dati iniziali
├── static/       CSS, JavaScript e immagini
└── templates/    Pagine e frammenti Thymeleaf

src/test/         Test automatici e fixture
examples/         Specifiche pronte per l'import
scripts/          Verifiche end-to-end in PowerShell
```

I permessi assegnati a un gruppo valgono anche per i suoi discendenti. Il sistema impedisce di lasciare l'installazione senza amministratori attivi e registra le operazioni tramite audit.

## Sviluppo e verifiche

### Test automatici

```sh
mvn test
```

La suite copre parser, schemi, riferimenti fra modelli, diff, generazione cURL, servizi e controller MVC. I test unitari e MockMvc non richiedono un database PostgreSQL attivo.

### Avvio in sviluppo

```sh
mvn spring-boot:run
```

Per disabilitare la cache dei template, impostare `THYMELEAF_CACHE=false` prima dell'avvio. Su Windows è disponibile anche:

```powershell
.\run-dev.ps1 -Port 8080
```

Lo script disabilita la cache dei template, compila e avvia l'applicazione dal classpath. Usa Maven in modalità offline: le dipendenze devono essere già presenti nella cache locale. Dopo una modifica alle dipendenze del POM, rigenera il classpath:

```powershell
mvn dependency:build-classpath "-Dmdep.outputFile=target\cp.txt" "-Dmdep.includeScope=runtime"
```

### Verifiche end-to-end

Gli script PowerShell in `scripts/` verificano setup, import, navigazione, permessi, test API e health check su un'applicazione reale.

**Gli script seguenti eliminano e ricreano il database indicato da `-Database` (default `redfish_catalog`) e arrestano eventuali processi in ascolto sulla porta selezionata. Usarli esclusivamente in un ambiente di test dedicato.** Richiedono PostgreSQL e `psql`, oppure un container PostgreSQL accessibile tramite Docker; il parametro `-DockerContainer` ha valore predefinito `postgres`.

```powershell
.\scripts\reset-and-test.ps1
```

Per verificare il JAR, dopo `mvn clean package`:

```powershell
.\scripts\verify-jar.ps1
```

I blocchi `smoke-setup.ps1`, `smoke-full.ps1`, `smoke-extra.ps1` e `smoke-health.ps1` sono disponibili anche separatamente. Modificano i dati del catalogo; quello di setup richiede un'installazione non ancora configurata.

### Database esistenti

La migrazione `V2__remove_global_api_url.sql` elimina l'impostazione obsoleta `app.base.url`. Gli URL dei singoli ambienti restano disponibili; Flyway applica la migrazione al successivo avvio.


La rimozione dei commenti dalla migrazione `V1__init.sql` ne modifica il checksum Flyway. Su installazioni che l'hanno già applicata, verificare la corrispondenza dello schema e riallineare la cronologia con la procedura Flyway `repair` prima dell'avvio. Su un database nuovo non è necessario alcun intervento.
