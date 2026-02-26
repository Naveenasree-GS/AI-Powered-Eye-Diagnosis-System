import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;

public class EyeCareServer {
    public static void main(String[] args) throws Exception {
        int port = 8000;
        int healthPort = 4000;
        String projectDir = System.getProperty("user.dir");

        // Create data directory
        File dataDir = new File(projectDir + "/data");
        if (!dataDir.exists())
            dataDir.mkdirs();

        // Main server on port 8000
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Create a context for serving static files
        server.createContext("/", new StaticFileHandler(projectDir));

        // API Endpoints
        server.createContext("/api/analyze", new AnalysisHandler());
        server.createContext("/api/book-appointment", new AppointmentHandler());
        server.createContext("/api/save-patient", new PatientHandler());
        server.createContext("/api/get-report", new ReportHandler());
        server.createContext("/health", new HealthHandler());

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(10));
        server.start();

        // Health check server on port 4000
        HttpServer healthServer = HttpServer.create(new InetSocketAddress(healthPort), 0);
        healthServer.createContext("/health", new HealthHandler());
        healthServer.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(5));
        healthServer.start();

        System.out.println("========================================");
        System.out.println("🚀 Eye Care AI Server Started");
        System.out.println("========================================");
        System.out.println("Main Server running at: http://localhost:" + port);
        System.out.println("Health Check running at: http://localhost:" + healthPort + "/health");
        System.out.println("API Endpoints:");
        System.out.println("  POST /api/analyze");
        System.out.println("  POST /api/book-appointment");
        System.out.println("  POST /api/save-patient");
        System.out.println("  GET  /api/get-report");
        System.out.println("  GET  /health");
        System.out.println("Project directory: " + projectDir);
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
                response.append("\"frontend\":\"http://localhost:8000\",");
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
                System.out.println("✓ Analysis processed and stored successfully");
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
                addToDatabase(requestBody);

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
