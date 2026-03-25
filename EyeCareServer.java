import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.spec.KeySpec;
import java.util.Map;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class EyeCareServer {
    public static void main(String[] args) throws Exception {
        // Read environment / system properties
        String projectDir = System.getProperty("user.dir");

        String portEnv = System.getenv("PORT");
        int port = 8000;
        if (portEnv != null && !portEnv.isEmpty()) {
            try {
                port = Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                System.err.println("Invalid PORT value, falling back to 8000");
            }
        }

        String dataDirEnv = System.getenv().getOrDefault("DATA_DIR", "data");
        String appEnv = System.getenv().getOrDefault("APP_ENV", "development");
        String logLevel = System.getenv().getOrDefault("LOG_LEVEL", "info");

        // Create data directory (relative to projectDir)
        File dataDir = new File(projectDir + "/" + dataDirEnv);
        if (!dataDir.exists())
            dataDir.mkdirs();

        // Main server on configured port (Render and many platforms expect a single port)
        HttpServer server = null;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (java.net.BindException be) {
            System.err.println("Port " + port + " is in use. Attempting to find a free port...");
            int freePort = findAvailablePort();
            if (freePort <= 0) {
                System.err.println("Could not find an available port. Exiting.");
                throw be;
            }
            System.err.println("Using fallback port: " + freePort);
            port = freePort;
            server = HttpServer.create(new InetSocketAddress(port), 0);
        }

        // Create a context for serving static files
        server.createContext("/", new StaticFileHandler(projectDir));

        // API Endpoints
        server.createContext("/api/analyze", new AnalysisHandler());
        server.createContext("/api/book-appointment", new AppointmentHandler());
        server.createContext("/api/save-patient", new PatientHandler());
        server.createContext("/api/log", new LogHandler());
        server.createContext("/api/get-report", new ReportHandler());
        // Doctor auth and OTP endpoints
        server.createContext("/api/doctor/request-otp", new DoctorOTPRequestHandler());
        server.createContext("/api/doctor/verify-otp", new DoctorOTPVerifyHandler());
        server.createContext("/api/doctor/register", new DoctorRegisterHandler());
        server.createContext("/api/doctor/login", new DoctorLoginHandler());
        server.createContext("/health", new HealthHandler());

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();

        // Note: using single server for both API and health endpoint (platforms like Render provide one PORT)

        System.out.println("========================================");
        System.out.println("🚀 Eye Care AI Server Started");
        System.out.println("========================================");
        System.out.println("Main Server running at: http://localhost:" + port);
        System.out.println("Health Check available at: http://localhost:" + port + "/health");
        System.out.println("API Endpoints:");
        System.out.println("  POST /api/analyze");
        System.out.println("  POST /api/book-appointment");
        System.out.println("  POST /api/save-patient");
        System.out.println("  GET  /api/get-report");
        System.out.println("  GET  /health");
        System.out.println("Project directory: " + projectDir);
        System.out.println("App environment: " + appEnv);
        System.out.println("Log level: " + logLevel);
        System.out.println("Press Ctrl+C to stop the server");
        System.out.println("========================================");
    }

    // Health Check Handler
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange))
                return;

            if ("GET".equals(exchange.getRequestMethod()) || "POST".equals(exchange.getRequestMethod())) {
                StringBuilder response = new StringBuilder();
                response.append("{");
                response.append("\"status\":\"healthy\",");
                response.append("\"server\":\"Eye Care AI Server\",");
                response.append("\"timestamp\":\"").append(new java.util.Date()).append("\",");
                response.append("\"uptime\":\"").append(getUptime()).append("\",");
                response.append("\"version\":\"1.0.0\",");
                response.append("\"endpoints\":{");
                response.append("\"analyze\":\"/api/analyze\",");
                response.append("\"booking\":\"/api/book-appointment\",");
                response.append("\"patient\":\"/api/save-patient\",");
                response.append("\"reports\":\"/api/get-report\",");
                response.append("\"health\":\"/health\"");
                response.append("},");
                String frontendPort = System.getenv("PORT");
                if (frontendPort == null || frontendPort.isEmpty()) frontendPort = "8000";
                response.append("\"frontend\":\"http://localhost:").append(frontendPort).append("\",");
                response.append("\"message\":\"Backend server is running and operational\"");
                response.append("}");

                sendJsonResponse(exchange, response.toString(), 200);
                System.out.println("✓ Health check request processed");
            } else {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
            }
        }
    }

    static String getUptime() {
        long uptime = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0)
            return days + " days";
        if (hours > 0)
            return hours + " hours";
        if (minutes > 0)
            return minutes + " minutes";
        return seconds + " seconds";
    }

    static int findAvailablePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            return -1;
        }
    }

    // Analysis Handler - Processes eye scan analysis
    static class AnalysisHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange))
                return;

            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = readRequestBody(exchange);
                System.out.println("📊 Analysis request: " + requestBody);

                // Simulate AI analysis
                StringBuilder response = new StringBuilder();
                response.append("{");
                response.append("\"status\":\"success\",");
                response.append("\"timestamp\":\"").append(new java.util.Date()).append("\",");
                response.append("\"diagnosis\":\"AI Eye Analysis Complete\",");
                response.append("\"confidence\":85,");
                response.append("\"findings\":[");
                response.append("\"Normal eye pressure\",");
                response.append("\"Healthy retina\",");
                response.append("\"No signs of cataracts\"");
                response.append("],");
                response.append("\"recommendation\":\"Continue regular checkups\"");
                response.append("}");

                sendJsonResponse(exchange, response.toString(), 200);
                saveToFile("analysis", requestBody);
                // Also persist analysis/scan results into the patient database so
                // scans appear alongside patient records for easier retrieval.
                try {
                    addToDatabase(requestBody);
                    System.out.println("✓ Analysis processed and stored successfully (also added to patients_db)");
                } catch (Exception e) {
                    System.err.println("⚠️ Failed to add analysis to patients_db: " + e.getMessage());
                }
            } else {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
            }
        }
    }

    // Appointment Handler - Handles appointment bookings
    static class AppointmentHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange))
                return;

            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = readRequestBody(exchange);
                System.out.println("📅 Booking request: " + requestBody);

                // Generate booking confirmation
                StringBuilder response = new StringBuilder();
                response.append("{");
                response.append("\"status\":\"success\",");
                response.append("\"bookingId\":\"BK").append(System.currentTimeMillis()).append("\",");
                response.append("\"message\":\"Appointment booked successfully\",");
                response.append("\"timestamp\":\"").append(new java.util.Date()).append("\"");
                response.append("}");

                sendJsonResponse(exchange, response.toString(), 200);
                saveToFile("appointment", requestBody);
                System.out.println("✓ Appointment booked and stored successfully");
            } else {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
            }
        }
    }

    // Patient Handler - Saves patient information
    static class PatientHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange))
                return;

            if ("POST".equals(exchange.getRequestMethod())) {
                String requestBody = readRequestBody(exchange);
                System.out.println("👤 Patient data saved: " + requestBody);

                saveToFile("patient", requestBody);

                // Try to merge this patient/scan into existing patients_db.json
                try {
                    boolean merged = mergeIntoPatientsDb(requestBody);
                    if (!merged) {
                        // Fallback: append as a new record
                        addToDatabase(requestBody);
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Error merging patient data, falling back to append: " + e.getMessage());
                    addToDatabase(requestBody);
                }

                StringBuilder response = new StringBuilder();
                response.append("{");
                response.append("\"status\":\"success\",");
                response.append("\"message\":\"Patient data saved\",");
                response.append("\"timestamp\":\"").append(new java.util.Date()).append("\"");
                response.append("}");

                sendJsonResponse(exchange, response.toString(), 200);
            } else {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
            }
        }
    }

    // Attempt to merge incoming patient JSON into the patients_db.json by matching on patient name.
    // Returns true if a merge/update occurred, false if no matching patient was found.
    static synchronized boolean mergeIntoPatientsDb(String incomingJson) {
        try {
            String dbPath = "data/patients_db.json";
            File dbFile = new File(dbPath);
            String current = "[]";
            if (dbFile.exists()) {
                current = new String(Files.readAllBytes(dbFile.toPath()), java.nio.charset.StandardCharsets.UTF_8);
                if (current.trim().isEmpty()) current = "[]";
            }

            // Try to extract a patient name from the incoming JSON (naive extractor)
            String name = extractJsonField(incomingJson, "name");
            if (name == null || name.trim().isEmpty()) {
                return false; // nothing to match against
            }

            // Look for an object in the array that contains the same "name":"<name>" pattern
            Pattern p = Pattern.compile("\\{[^}]*\\\"name\\\"\\s*:\\s*\\\"" + Pattern.quote(name) + "\\\"[^}]*\\}", Pattern.DOTALL);
            Matcher m = p.matcher(current);
            if (m.find()) {
                String existingObj = m.group();

                String updatedObj = existingObj;
                if (existingObj.contains("\"scans\"")) {
                    // Insert new scan JSON at the start of the scans array
                    updatedObj = existingObj.replaceFirst("(\\\"scans\\\"\\s*:\\s*\\[)(\\s*)", "$1" + incomingJson + ",$2");
                } else {
                    // Add a scans array with the incoming JSON as first element
                    updatedObj = existingObj.replaceFirst("}$", ",\"scans\":[" + incomingJson + "]}");
                }

                // Replace the object in the DB content
                String before = current.substring(0, m.start());
                String after = current.substring(m.end());
                String newContent = before + updatedObj + after;

                Files.write(dbFile.toPath(), newContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                System.out.println("💾 Merged patient scan into: " + dbFile.getPath());
                return true;
            }

            return false;
        } catch (Exception e) {
            System.err.println("⚠️ mergeIntoPatientsDb error: " + e.getMessage());
            return false;
        }
    }

    // Report Handler - Retrieves medical reports
    static class ReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange))
                return;

            if ("GET".equals(exchange.getRequestMethod())) {
                File dbFile = new File("data/patients_db.json");
                String reportsJson = "[]";

                if (dbFile.exists()) {
                    try {
                        reportsJson = new String(Files.readAllBytes(dbFile.toPath()),
                                java.nio.charset.StandardCharsets.UTF_8);
                        // Ensure it's a valid JSON array, if file is empty or corrupted reset to []
                        if (reportsJson.trim().isEmpty())
                            reportsJson = "[]";
                    } catch (Exception e) {
                        System.err.println("Error reading DB: " + e.getMessage());
                    }
                }

                StringBuilder response = new StringBuilder();
                response.append("{");
                response.append("\"status\":\"success\",");
                response.append("\"reports\":").append(reportsJson).append(",");
                response.append("\"message\":\"Reports retrieved\"");
                response.append("}");

                sendJsonResponse(exchange, response.toString(), 200);
            } else {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
            }
        }
    }

    // Database Helper
    static synchronized void addToDatabase(String jsonContent) {
        File dbFile = new File("data/patients_db.json");
        try {
            if (!dbFile.exists()) {
                Files.write(dbFile.toPath(),
                        ("[" + jsonContent + "]").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            } else {
                String currentContent = new String(Files.readAllBytes(dbFile.toPath()),
                        java.nio.charset.StandardCharsets.UTF_8);
                currentContent = currentContent.trim();
                if (currentContent.endsWith("]")) {
                    currentContent = currentContent.substring(0, currentContent.length() - 1);
                    if (currentContent.length() > 1 && !currentContent.trim().equals("[")) {
                        currentContent += ",";
                    }
                    currentContent += jsonContent + "]";
                    Files.write(dbFile.toPath(), currentContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            System.out.println("💾 Added to database: " + dbFile.getPath());
        } catch (Exception e) {
            System.err.println("⚠️ Error updating database: " + e.getMessage());
        }
    }

    // Utility Methods
    static boolean handleOPTIONS(HttpExchange exchange) throws IOException {
        enableCORS(exchange);
        if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    static void enableCORS(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE, PUT");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Requested-With");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "3600");
    }

    static String readRequestBody(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody();
                ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) != -1) {
                bos.write(buffer, 0, length);
            }
            return bos.toString("UTF-8");
        }
    }

    static void sendJsonResponse(HttpExchange exchange, String json, int code) throws IOException {
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    // Very small JSON extractor for top-level string/number/boolean fields (demo only)
    static String extractJsonField(String json, String field) {
        if (json == null || field == null) return null;
        try {
            // look for "field": "value"
            Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
            Matcher m = p.matcher(json);
            if (m.find()) return m.group(1);

            // look for numeric or boolean: "field": 12345 or "field": true
            p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([^,}\n\\r]+)");
            m = p.matcher(json);
            if (m.find()) {
                String val = m.group(1).trim();
                // strip quotes if present
                if (val.startsWith("\"") && val.endsWith("\"")) return val.substring(1, val.length()-1);
                return val.replaceAll("[\n\r\"]", "");
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    static void saveToFile(String type, String content) {
        try {
            String timestamp = String.valueOf(System.currentTimeMillis());
            Path path = Paths.get("data/" + type + "_" + timestamp + ".json");
            Files.write(path, content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("📁 Data stored: " + path);
        } catch (Exception e) {
            System.err.println("⚠️ Error storing data: " + e.getMessage());
        }
    }

    // Simple PBKDF2 password hashing
    static String hashPassword(String password, byte[] salt) {
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 10000, 256);
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return null;
        }
    }

    static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    static String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            return null;
        }
    }

    static String issueToken(Map<String, Object> payload) {
        try {
            String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            StringBuilder pb = new StringBuilder();
            pb.append("{");
            boolean first = true;
            for (Map.Entry<String, Object> e : payload.entrySet()) {
                if (!first) pb.append(',');
                first = false;
                pb.append('"').append(e.getKey()).append('"').append(':');
                Object v = e.getValue();
                if (v instanceof Number) pb.append(v.toString());
                else pb.append('"').append(v.toString()).append('"');
            }
            pb.append('}');
            String payloadEncoded = Base64.getUrlEncoder().withoutPadding().encodeToString(pb.toString().getBytes(StandardCharsets.UTF_8));
            String signingInput = header + "." + payloadEncoded;
            String secret = System.getenv().getOrDefault("JWT_SECRET", "eyecare_dev_secret");
            String sig = hmacSha256(signingInput, secret);
            return signingInput + "." + sig;
        } catch (Exception e) {
            return null;
        }
    }

    // Log Handler - receives client-side logs
    static class LogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange)) return;
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            String body = readRequestBody(exchange);
            System.out.println("📝 Client log: " + body);
            // Save to file for later inspection
            saveToFile("client_log", body);
            sendJsonResponse(exchange, "{\"status\":\"ok\"}", 200);
        }
    }

    // Doctor OTP Request Handler - generates OTP and stores tx
    static class DoctorOTPRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange)) return;
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            String body = readRequestBody(exchange);
            // Expecting JSON: {"mobile":"..."}
            String mobile = extractJsonField(body, "mobile");
            if (mobile == null) {
                sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"mobile required\"}", 400);
                return;
            }

            String txId = "tx_" + System.currentTimeMillis() + "_" + (new SecureRandom().nextInt(900)+100);
            String otp = String.format("%06d", new SecureRandom().nextInt(1000000));

            long expires = System.currentTimeMillis() + 5 * 60 * 1000; // 5 minutes

            String otpRecord = "{\"mobile\":\"" + mobile + "\",\"otp\":\"" + otp + "\",\"expires\":" + expires + ",\"attempts\":0,\"verified\":false}";
            try {
                Path p = Paths.get("data/otp_" + txId + ".json");
                Files.write(p, otpRecord.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) {
                System.err.println("Failed to write OTP file: " + e.getMessage());
            }

            // In production send SMS via provider. Here we log OTP for demo.
            System.out.println("[OTP] txId=" + txId + " mobile=" + mobile + " otp=" + otp);

            String masked = mobile.replaceAll("(\\d{2})\\d+(\\d{2})", "$1****$2");
            sendJsonResponse(exchange, "{\"status\":\"ok\",\"txId\":\"" + txId + "\",\"maskedMobile\":\"" + masked + "\"}", 200);
        }
    }

    // Doctor OTP Verify Handler
    static class DoctorOTPVerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange)) return;
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            String body = readRequestBody(exchange);
            String txId = extractJsonField(body, "txId");
            String otp = extractJsonField(body, "otp");
            if (txId == null || otp == null) {
                sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"txId and otp required\"}", 400);
                return;
            }
            Path p = Paths.get("data/otp_" + txId + ".json");
            if (!Files.exists(p)) {
                sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"Invalid txId\"}", 400);
                return;
            }
            String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
            String storedOtp = extractJsonField(content, "otp");
            String expiresStr = extractJsonField(content, "expires");
            long expires = 0;
            try { expires = Long.parseLong(expiresStr); } catch(Exception e){ }
            if (System.currentTimeMillis() > expires) {
                sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"OTP expired\"}", 400);
                return;
            }
            if (!otp.equals(storedOtp)) {
                // increment attempts
                String attemptsStr = extractJsonField(content, "attempts");
                int attempts = 0; try{ attempts = Integer.parseInt(attemptsStr); } catch(Exception e){}
                attempts++;
                content = content.replaceFirst("\"attempts\":\\d+", "\"attempts\":"+attempts);
                Files.write(p, content.getBytes(StandardCharsets.UTF_8));
                sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"Invalid OTP\"}", 400);
                return;
            }
            // mark verified
            content = content.replaceFirst("\"verified\":false", "\"verified\":true");
            Files.write(p, content.getBytes(StandardCharsets.UTF_8));
            sendJsonResponse(exchange, "{\"status\":\"ok\",\"message\":\"OTP verified\"}", 200);
        }
    }

    // Doctor Register Handler - expects JSON with txId and details (cert as base64 optional)
    static class DoctorRegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange)) return;
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            String body = readRequestBody(exchange);
            String txId = extractJsonField(body, "txId");
            String email = extractJsonField(body, "email");
            String password = extractJsonField(body, "password");
            String name = extractJsonField(body, "name");
            String mbbs = extractJsonField(body, "mbbs");
            String master = extractJsonField(body, "master");
            String certData = extractJsonField(body, "certData");

            if (txId==null || email==null || password==null) {
                sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"txId, email and password required\"}", 400);
                return;
            }
            Path otpPath = Paths.get("data/otp_" + txId + ".json");
            if (!Files.exists(otpPath)) {
                sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"Invalid txId\"}", 400);
                return;
            }
            String otpContent = new String(Files.readAllBytes(otpPath), StandardCharsets.UTF_8);
            String verified = extractJsonField(otpContent, "verified");
            if (!"true".equals(verified)) {
                sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"OTP not verified\"}", 400);
                return;
            }

            // Save certificate if provided
            String doctorId = "doc_" + System.currentTimeMillis();
            String certPath = "";
            if (certData != null && certData.startsWith("data:")) {
                // certData format: data:application/pdf;base64,....
                int comma = certData.indexOf(',');
                String base64 = certData.substring(comma+1);
                byte[] decoded = Base64.getDecoder().decode(base64);
                Path certDir = Paths.get("data/certs"); if (!Files.exists(certDir)) Files.createDirectories(certDir);
                certPath = "data/certs/" + doctorId + "_cert.bin";
                Files.write(Paths.get(certPath), decoded);
            }

            byte[] salt = generateSalt();
            String saltB64 = Base64.getEncoder().encodeToString(salt);
            String hash = hashPassword(password, salt);

            // Build doctor record
            String record = "{" +
                    "\"id\":\""+doctorId+"\"," +
                    "\"email\":\""+email+"\"," +
                    "\"name\":\""+(name==null?"":name)+"\"," +
                    "\"mbbs\":\""+(mbbs==null?"":mbbs)+"\"," +
                    "\"master\":\""+(master==null?"":master)+"\"," +
                    "\"salt\":\""+saltB64+"\"," +
                    "\"hash\":\""+hash+"\"," +
                    "\"certPath\":\""+certPath+"\"," +
                    "\"verified\":true"+
                    "}";

            // Append to doctors_db.json
            Path db = Paths.get("data/doctors_db.json");
            if (!Files.exists(db)) {
                Files.write(db, ("["+record+"]").getBytes(StandardCharsets.UTF_8));
            } else {
                String cur = new String(Files.readAllBytes(db), StandardCharsets.UTF_8).trim();
                if (cur.endsWith("]")) {
                    cur = cur.substring(0, cur.length()-1);
                    if (cur.length()>1 && !cur.trim().equals("[")) cur += ",";
                    cur += record + "]";
                    Files.write(db, cur.getBytes(StandardCharsets.UTF_8));
                }
            }

            sendJsonResponse(exchange, "{\"status\":\"ok\",\"doctorId\":\""+doctorId+"\"}", 200);
        }
    }

    // Doctor Login Handler
    static class DoctorLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleOPTIONS(exchange)) return;
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendJsonResponse(exchange, "{\"error\":\"Method not allowed\"}", 405);
                return;
            }
            String body = readRequestBody(exchange);
            String email = extractJsonField(body, "email");
            String password = extractJsonField(body, "password");
            if (email==null||password==null) { sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"email and password required\"}",400); return; }

            Path db = Paths.get("data/doctors_db.json");
            if (!Files.exists(db)) { sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"no doctors registered\"}",400); return; }
            String cur = new String(Files.readAllBytes(db), StandardCharsets.UTF_8);
            // naive search
            String lower = cur.toLowerCase();
            if (!lower.contains(email.toLowerCase())) { sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"invalid credentials\"}",401); return; }
            // parse entries - simple split by '},{'
            String[] parts = cur.substring(1, cur.length()-1).split("\\},\\{");
            for (String part : parts) {
                String entry = part;
                if (!entry.startsWith("{")) entry = "{"+entry;
                if (!entry.endsWith("}")) entry = entry+"}";
                String eEmail = extractJsonField(entry, "email");
                if (eEmail!=null && eEmail.equalsIgnoreCase(email)) {
                    String saltB64 = extractJsonField(entry, "salt");
                    String hash = extractJsonField(entry, "hash");
                    String id = extractJsonField(entry, "id");
                    String verified = extractJsonField(entry, "verified");
                    byte[] salt = Base64.getDecoder().decode(saltB64);
                    String computed = hashPassword(password, salt);
                    if (computed!=null && computed.equals(hash)) {
                        if (!"true".equals(verified)) { sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"doctor not verified\"}",403); return; }
                        Map<String,Object> payload = new HashMap<>();
                        payload.put("sub", id);
                        payload.put("role", "doctor");
                        payload.put("email", email);
                        payload.put("iat", System.currentTimeMillis());
                        String token = issueToken(payload);
                        sendJsonResponse(exchange, "{\"status\":\"ok\",\"token\":\""+token+"\"}",200);
                        return;
                    } else {
                        sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"invalid credentials\"}",401); return;
                    }
                }
            }
            sendJsonResponse(exchange, "{\"status\":\"error\",\"message\":\"invalid credentials\"}",401);
        }
    }

    static class StaticFileHandler implements HttpHandler {
        private String projectDir;

        public StaticFileHandler(String projectDir) {
            this.projectDir = projectDir;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();
            if (requestPath.equals("/")) {
                requestPath = "/index.html";
            }

            File file = new File(projectDir + requestPath);

            if (file.exists() && file.isFile()) {
                // Determine content type
                String contentType = getContentType(file.getName());

                byte[] fileContent = Files.readAllBytes(file.toPath());

                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, fileContent.length);

                OutputStream os = exchange.getResponseBody();
                os.write(fileContent);
                os.close();

                System.out.println("✓ Served: " + requestPath);
            } else {
                // File not found
                String response = "404 - File not found: " + requestPath;
                exchange.sendResponseHeaders(404, response.length());
                OutputStream os = exchange.getResponseBody();
                os.write(response.getBytes());
                os.close();

                System.out.println("✗ Not found: " + requestPath);
            }
        }

        private String getContentType(String fileName) {
            if (fileName.endsWith(".html"))
                return "text/html; charset=utf-8";
            if (fileName.endsWith(".css"))
                return "text/css; charset=utf-8";
            if (fileName.endsWith(".js"))
                return "application/javascript; charset=utf-8";
            if (fileName.endsWith(".json"))
                return "application/json; charset=utf-8";
            if (fileName.endsWith(".png"))
                return "image/png";
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg"))
                return "image/jpeg";
            if (fileName.endsWith(".gif"))
                return "image/gif";
            if (fileName.endsWith(".webp"))
                return "image/webp";
            if (fileName.endsWith(".svg"))
                return "image/svg+xml";
            if (fileName.endsWith(".ico"))
                return "image/x-icon";
            return "application/octet-stream";
        }
    }
}
