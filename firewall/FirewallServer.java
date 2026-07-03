package firewall;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import strategy.IntelligentFilter;
import strategy.PacketFilterStrategy;
import strategy.SimpleFilter;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * HTTP Server Java puro (com.sun.net.httpserver — incluso nel JDK, nessuna dipendenza).
 * Espone due endpoint usati dalla UI:
 *
 *   POST /simple_analyze      → SimpleFilter (regole statiche)
 *   POST /intelligent_analyze → IntelligentFilter (Decision Tree Weka)
 *   GET  /status              → stato del modello ML
 *
 * Avvio: java -cp ".:lib/weka.jar" firewall.FirewallServer
 * (su Windows usa ; al posto di : nel classpath)
 */
public class FirewallServer {

    private static final int PORT = 8080;

    // Istanze delle strategie (IntelligentFilter addestra al primo uso)
    private static final PacketFilterStrategy simpleFilter      = new SimpleFilter();
    private static final IntelligentFilter    intelligentFilter = new IntelligentFilter();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/simple_analyze",      new AnalyzeHandler("simple"));
        server.createContext("/intelligent_analyze", new AnalyzeHandler("intelligent"));
        server.createContext("/status",              new StatusHandler());
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            if (!exchange.getRequestURI().getPath().equals("/")) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            java.nio.file.Path htmlPath = java.nio.file.Paths.get("ui/index.html");
            byte[] bytes = java.nio.file.Files.readAllBytes(htmlPath);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(bytes); }
        });

        // Gestione CORS per le richieste preflight OPTIONS dalla UI
        server.setExecutor(null);
        server.start();

        System.out.println("==============================================");
        System.out.println("  Firewall Java Server avviato sulla porta " + PORT);
        System.out.println("  http://localhost:" + PORT);
        System.out.println("==============================================");
    }

    // ------------------------------------------------------------------
    // Handler per /simple_analyze e /intelligent_analyze
    // ------------------------------------------------------------------
    static class AnalyzeHandler implements HttpHandler {
        private final String mode;

        AnalyzeHandler(String mode) { this.mode = mode; }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // CORS headers — necessari perché la UI è un file:// locale
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin",  "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");

            // Preflight OPTIONS
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Metodo non consentito\"}");
                return;
            }

            try {
                // Leggi il body JSON
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                NetworkPacket packet = parsePacket(body);

                boolean isSafe;
                double  confidence = -1;
                String label;

                if ("simple".equals(mode)) {
                    isSafe = simpleFilter.analyzePacket(packet);
                    label = isSafe ? "normal" : "Violazione Regole Statiche";
                } else {
                    isSafe     = intelligentFilter.analyzePacket(packet);
                    confidence = intelligentFilter.getConfidence(packet);

                    label = isSafe ? "normal" : "Minaccia AI Rilevata";
                }

                //String label = isSafe ? "normal" : "anomaly";
                String confStr = confidence >= 0
                        ? String.format(java.util.Locale.US, "%.1f", confidence)
                        : "null";

                String json = String.format(
                    "{\"attack\":%s, \"label\":\"%s\", \"confidence\":%s}",
                    isSafe ? "false" : "true", label, confStr
                );
                sendJson(exchange, 200, json);

            } catch (Exception e) {
                sendJson(exchange, 400, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }
    }

    // ------------------------------------------------------------------
    // Handler per /status
    // ------------------------------------------------------------------
    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            boolean ready = IntelligentFilter.isModelReady();
            String  err   = IntelligentFilter.getTrainError();
            String json = String.format(
                "{\"model_ready\":%s, \"error\":%s}",
                ready,
                err != null ? "\"" + err + "\"" : "null"
            );
            sendJson(exchange, 200, json);
        }
    }

    // ------------------------------------------------------------------
    // Parsing JSON minimale (nessuna libreria esterna necessaria)
    // ------------------------------------------------------------------
    private static NetworkPacket parsePacket(String json) {
        return new NetworkPacket(
            "web-request",
            (int) extractDouble(json, "duration"),
            (int) extractDouble(json, "src_bytes"),
            (int) extractDouble(json, "dst_bytes"),
            (int) extractDouble(json, "count"),
            (int) extractDouble(json, "srv_count"),
                  extractDouble(json, "serror_rate"),
                  extractDouble(json, "rerror_rate"),
                  extractDouble(json, "same_srv_rate"),
                  extractDouble(json, "diff_srv_rate"),
            (int) extractDouble(json, "dst_host_count"),
            (int) extractDouble(json, "dst_host_srv_count")
        );
    }

    private static double extractDouble(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) return 0.0;
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) return 0.0;
        int start = colon + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) start++;
        int end = start;
        while (end < json.length() && "0123456789.-eE".indexOf(json.charAt(end)) >= 0) end++;
        try { return Double.parseDouble(json.substring(start, end)); }
        catch (NumberFormatException e) { return 0.0; }
    }

    private static void sendJson(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
    }
}