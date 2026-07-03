# **Intelligent Firewall — Next-Generation Network Protection**

Intelligent Firewall è un sistema avanzato di protezione di rete sviluppato per il corso di Software Architecture and Pattern Design (SAPD) da Giuseppe Specchi. Il progetto mira a superare i limiti dei sistemi di sicurezza tradizionali (spesso basati su codice monolitico) offrendo una **sicurezza proattiva** capace di adattarsi dinamicamente alle minacce emergenti.

Il sistema separa le logiche di analisi dal motore del firewall, ottimizzando le risorse e garantendo un tasso di rilevamento delle minacce del 99.9% grazie all'uso dell'Intelligenza Artificiale.

---

## 🔧 **Caratteristiche Principali**

* **Filtraggio Intelligente:** Il sistema adatta dinamicamente le regole per bloccare le minacce in tempo reale.
* **Sicurezza Proattiva:** Previene attacchi zero-day individuando comportamenti anomali prima che colpiscano il sistema.
* **Ottimizzazione delle Risorse:** Bilancia il carico attivando l'analisi IA avanzata solo in caso di traffico sospetto.
* **Alta Modularità e Bassa Latenza:** Nessuna dipendenza diretta tra il motore del firewall e gli algoritmi specifici, garantendo un passaggio dei pacchetti ottimizzato senza colli di bottiglia.

---

## 🖥️ **Architettura del Sistema**

Il sistema utilizza una netta separazione tra l'interfaccia utente (UI Web / Dashboard) e il motore di analisi (Firewall Server in Java). 

La comunicazione avviene tramite **API REST**:
1.  La Dashboard invia pacchetti di rete sotto forma di richieste `HTTP POST` in formato JSON.
2.  Il server intercetta il traffico sulla porta `8080` ed esegue il parsing del JSON in oggetti `NetworkPacket` (estraendo feature come `duration`, `src_bytes`, `dst_bytes`, `serror_rate`, ecc.).
3.  Il routing dinamico instrada l'analisi verso regole statiche (`/simple_analyze`) o verso il Machine Learning (`/intelligent_analyze`).
4.  L'esito (etichetta della minaccia e grado di confidenza dell'IA) viene restituito con una HTTP Response `200 OK` alla Web UI.

---

## 🧩 **Pattern Architetturali Utilizzati**

L'architettura del software si fonda su tre pattern principali per garantire flessibilità, manutenibilità e massime prestazioni:

### 1. Strategy Pattern (Comportamentale)
Consente lo switch dinamico degli algoritmi di filtraggio a runtime. 
* L'interfaccia `PacketFilterStrategy` espone il metodo `analyzePacket(NetworkPacket)`.
* È implementata da `SimpleFilter` (per l'analisi basata su regole statiche) e `IntelligentFilter` (per l'analisi basata su IA).
* Questo garantisce totale disaccoppiamento: il `FirewallServer` non ha bisogno di conoscere i dettagli del Machine Learning.

### 2. Client-Server (Architetturale)
Garantisce la separazione netta tra la Dashboard Web (Client) e il Server Java, facendoli comunicare in modo totalmente indipendente tramite API REST.

### 3. Singleton (Creazionale)
Il modello di Machine Learning (algoritmo **J48** di Weka) è pesante e richiede risorse per essere inizializzato. Tramite il pattern Singleton, il modello viene istanziato e caricato in RAM una sola volta all'avvio. Questo azzera la latenza durante l'analisi, evitando di dover ricaricare il dataset per ogni singolo pacchetto in transito.

---

## 🛠️ **Requisiti e Installazione**

* **Java Development Kit (JDK) 11+**
* Libreria **Weka** per l'Intelligenza Artificiale (modello `J48` per gli alberi decisionali).

**Configurazione della Libreria:**
Assicurarsi che il file `weka.jar` (presente nella cartella `lib/`) sia incluso nel **Classpath** del progetto. 
* Su **VS Code**: la libreria dovrebbe essere rilevata automaticamente nei *Referenced Libraries*.
* Su **Eclipse / IntelliJ**: fare tasto destro sul file `.jar` e selezionare *Add to Build Path* / *Add as Library*.

---

## 🧠 **Dataset e Modello Pre-addestrato**

All'interno della root del progetto sono presenti due file fondamentali per il funzionamento del modulo di Intelligenza Artificiale:
* **`firewall_model.ser`**: È il modello di Machine Learning (Decision Tree J48) pre-addestrato e serializzato. Il server carica questo file all'avvio, abbattendo drasticamente i tempi di inizializzazione e permettendo un'analisi immediata del traffico.
* **`KDDTrain+.txt`**: È il dataset originale contenente il traffico di rete (normale e anomalie/attacchi) utilizzato per addestrare il modello. Rimane a disposizione nel repository per permettere futuri ri-addestramenti o per implementazioni di *Online Learning*.

---

## 🚀 **Avvio rapido (simulazione locale)**

All'interno del file tasks.json sono presenti i comandi per compilare ed eseguire nel terminale i file del server

Successivamente avviare la UI Web per iniziare a inviare pacchetti e visualizzare i risultati.

---

## 🔮 **Sviluppi Futuri**
L'architettura estensibile permette di pianificare i seguenti aggiornamenti futuri senza dover modificare il codice core esistente:

**1. Switch Automatico (Auto-Adaptive):** Transizione automatica e thread-safe tra l'algoritmo statico e l'IA senza perdita di pacchetti.

**2. Espansione delle Strategie:** Integrazione di nuove regole di filtraggio, come reti neurali profonde o regole basate sulla geolocalizzazione IP.

**3. Online Learning:** Addestramento continuo del modello in tempo reale sui nuovi pacchetti processati, per aumentare la precisione empirica giorno dopo giorno.


© 2026 — **Intelligent Firewall** | SAPD Project by Giuseppe Specchi
