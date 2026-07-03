package strategy;

import firewall.NetworkPacket;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;

/**
 * Strategia INTELLIGENTE: Decision Tree J48 (Weka) addestrato su NSL-KDD.
 * Legge il CSV manualmente con BufferedReader per evitare problemi
 * con il CSVLoader di Weka su dataset senza header.
 *
 * true  = SICURO ("normal")
 * false = ANOMALIA
 */
public class IntelligentFilter implements PacketFilterStrategy {

    private static final String DATASET_PATH  = "KDDTrain+.txt";
    private static final String MODEL_PATH   = "firewall_model.ser"; // modello salvato su disco

    // Indici delle colonne nel CSV NSL-KDD (0-based, 43 colonne totali)
    // 0=duration, 4=src_bytes, 5=dst_bytes, 22=count, 23=srv_count,
    // 24=serror_rate, 26=rerror_rate, 28=same_srv_rate, 29=diff_srv_rate,
    // 31=dst_host_count, 32=dst_host_srv_count, 41=label
    private static final int[] COL_INDICES = {0, 4, 5, 22, 23, 24, 26, 28, 29, 31, 32};
    private static final int   LABEL_COL   = 41;

    private static final String[] FEATURE_NAMES = {
        "duration", "src_bytes", "dst_bytes", "count", "srv_count",
        "serror_rate", "rerror_rate", "same_srv_rate", "diff_srv_rate",
        "dst_host_count", "dst_host_srv_count"
    };

    // Stato condiviso — addestramento fatto una volta sola
    private static J48      classifier   = null;
    private static Instances datasetHeader = null;
    private static boolean  modelReady   = false;
    private static String   trainError   = null;

    public IntelligentFilter() {
        if (!modelReady && trainError == null) {
            trainModel();
        }
    }

    // ------------------------------------------------------------------
    // Addestramento
    // ------------------------------------------------------------------
    private static synchronized void trainModel() {
        if (modelReady || trainError != null) return;

        // Prova a caricare il modello già addestrato dal disco
        java.io.File modelFile = new java.io.File(MODEL_PATH);
        if (modelFile.exists()) {
            try {
                Object[] loaded = weka.core.SerializationHelper.readAll(MODEL_PATH);
                classifier    = (J48) loaded[0];
                datasetHeader = (Instances) loaded[1];
                modelReady    = true;
                System.out.println("[IntelligentFilter] Modello caricato dal disco (" + MODEL_PATH + ") -> training saltato.");
                return;
            } catch (Exception e) {
                System.out.println("[IntelligentFilter] Modello su disco non valido, riaddestro...");
            }
        }

        System.out.println("[IntelligentFilter] Avvio addestramento Decision Tree (J48)...");
        try {
            // 1. Definisci la struttura del dataset Weka
            ArrayList<Attribute> attrs = new ArrayList<>();
            for (String name : FEATURE_NAMES) {
                attrs.add(new Attribute(name)); // attributo numerico
            }
            ArrayList<String> classValues = new ArrayList<>();
            classValues.add("normal");
            classValues.add("anomaly");
            attrs.add(new Attribute("label", classValues));

            Instances dataset = new Instances("KDD", attrs, 125000);
            dataset.setClassIndex(FEATURE_NAMES.length); // ultima colonna = classe

            // 2. Leggi il CSV riga per riga con BufferedReader
            int righeCaricate = 0;
            try (BufferedReader br = new BufferedReader(new FileReader(DATASET_PATH))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String[] cols = line.split(",");
                    if (cols.length < 42) continue; // riga incompleta, salta

                    // Estrai le 11 feature numeriche
                    double[] vals = new double[FEATURE_NAMES.length + 1];
                    boolean valid = true;
                    for (int j = 0; j < COL_INDICES.length; j++) {
                        try {
                            vals[j] = Double.parseDouble(cols[COL_INDICES[j]].trim());
                        } catch (NumberFormatException e) {
                            valid = false;
                            break;
                        }
                    }
                    if (!valid) continue;

                    // Etichetta: "normal" → "normal", tutto il resto → "anomaly"
                    String rawLabel   = cols[LABEL_COL].trim();
                    String binaryLabel = rawLabel.equals("normal") ? "normal" : "anomaly";
                    int labelIndex = dataset.attribute("label").indexOfValue(binaryLabel);
                    if (labelIndex < 0) continue;
                    vals[FEATURE_NAMES.length] = labelIndex;

                    dataset.add(new DenseInstance(1.0, vals));
                    righeCaricate++;
                }
            }

            System.out.println("[IntelligentFilter] Righe caricate: " + righeCaricate);
            if (righeCaricate == 0) {
                throw new Exception("Dataset vuoto o percorso errato: " + DATASET_PATH);
            }

            // 3. Salva la struttura vuota per le istanze a runtime
            datasetHeader = new Instances(dataset, 0);

            // 4. Addestra J48
            classifier = new J48();
            classifier.setConfidenceFactor(0.25f);
            classifier.setMinNumObj(2);
            classifier.buildClassifier(dataset);

            modelReady = true;
            System.out.println("[IntelligentFilter] Modello pronto!");

            // Salva il modello su disco per i riavvii successivi
            try {
                weka.core.SerializationHelper.writeAll(MODEL_PATH, new Object[]{classifier, datasetHeader});
                System.out.println("[IntelligentFilter] Modello salvato su disco (" + MODEL_PATH + ").");
            } catch (Exception se) {
                System.out.println("[IntelligentFilter] Avviso: impossibile salvare il modello: " + se.getMessage());
            }

        } catch (Exception e) {
            trainError = e.getMessage();
            System.err.println("[IntelligentFilter] Errore addestramento: " + trainError);
        }
    }

    // ------------------------------------------------------------------
    // Classificazione
    // ------------------------------------------------------------------
    @Override
    public boolean analyzePacket(NetworkPacket packet) {
        if (!modelReady) {
            System.err.println("[IntelligentFilter] Modello non disponibile: " + trainError);
            return false;
        }
        try {
            double[] vals = buildValues(packet);
            DenseInstance instance = new DenseInstance(1.0, vals);
            instance.setDataset(datasetHeader);

            double[] dist       = classifier.distributionForInstance(instance);
            int      normalIdx  = datasetHeader.attribute("label").indexOfValue("normal");
            int      anomalyIdx = datasetHeader.attribute("label").indexOfValue("anomaly");

            boolean safe = dist[normalIdx] >= dist[anomalyIdx];
            System.out.printf("[IntelligentFilter] %s → normal=%.1f%% anomaly=%.1f%%%n",
                    packet.getDescription(), dist[normalIdx]*100, dist[anomalyIdx]*100);
            return safe;

        } catch (Exception e) {
            System.err.println("[IntelligentFilter] Errore classificazione: " + e.getMessage());
            return false;
        }
    }

    /** Percentuale di confidenza che il pacchetto sia un'anomalia (per la UI). */
    public double getConfidence(NetworkPacket packet) {
        if (!modelReady) return 0.0;
        try {
            DenseInstance instance = new DenseInstance(1.0, buildValues(packet));
            instance.setDataset(datasetHeader);
            double[] dist = classifier.distributionForInstance(instance);
            int anomalyIdx = datasetHeader.attribute("label").indexOfValue("anomaly");
            return Math.round(dist[anomalyIdx] * 1000.0) / 10.0;
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double[] buildValues(NetworkPacket p) {
        double[] vals = new double[FEATURE_NAMES.length + 1];
        vals[0]  = p.getDuration();
        vals[1]  = p.getSrc_bytes();
        vals[2]  = p.getDst_bytes();
        vals[3]  = p.getCount();
        vals[4]  = p.getSrv_count();
        vals[5]  = p.getSerror_rate();
        vals[6]  = p.getRerror_rate();
        vals[7]  = p.getSame_srv_rate();
        vals[8]  = p.getDiff_srv_rate();
        vals[9]  = p.getDst_host_count();
        vals[10] = p.getDst_host_srv_count();
        vals[11] = weka.core.Utils.missingValue(); // classe da predire
        return vals;
    }

    public static boolean isModelReady() { return modelReady; }
    public static String  getTrainError() { return trainError; }
}