package edu.tdse.lab5;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MicroWeb {

    private static final Map<String, Route> getRoutes = new ConcurrentHashMap<>();
    private static String staticRoot = null; // ej: "/webroot" dentro de target/classes

    public static void get(String path, Route handler) {
        getRoutes.put(path, handler);
    }

    public static void staticfiles(String path) {
        staticRoot = path;
    }

    public static void start(int port) throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("MicroWeb running on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                handleClient(clientSocket);
            }
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
                OutputStream rawOut = clientSocket.getOutputStream();
                PrintWriter out = new PrintWriter(new OutputStreamWriter(rawOut, StandardCharsets.UTF_8), true)
        ) {
            String requestLine = in.readLine();
            if (requestLine == null || requestLine.isBlank()) return;

            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) { /* ignore */ }

            String[] parts = requestLine.split(" ");
            String method = parts[0];
            String fullUri = parts[1];

            URI uri = new URI(fullUri);
            String path = uri.getPath();
            Map<String, String> query = parseQuery(uri.getRawQuery());

            Request req = new Request(path, query);
            Response res = new Response();

            if ("GET".equals(method) && getRoutes.containsKey(path)) {
                Route route = getRoutes.get(path);
                String body;
                try {
                    body = route.handle(req, res);
                } catch (Exception e) {
                    res.status(500);
                    res.type("text/plain; charset=UTF-8");
                    body = "Internal Server Error: " + e.getMessage();
                }
                sendString(out, res.getStatus(), res.getContentType(), body);
                return;
            }

            // Si no hubo ruta REST, intentar estáticos:
            if ("GET".equals(method) && staticRoot != null) {
                boolean served = tryServeStatic(path, rawOut, out);
                if (served) return;
            }

            sendString(out, 404, "text/plain; charset=UTF-8", "Not Found");
        } catch (Exception e) {
            // Si algo explota, no podemos garantizar respuesta (socket puede estar mal),
            // pero evitamos tumbar el server.
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) throws UnsupportedEncodingException {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return result;

        String[] pairs = rawQuery.split("&");
        for (String pair : pairs) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            result.put(key, value);
        }
        return result;
    }

    private static boolean tryServeStatic(String requestPath, OutputStream rawOut, PrintWriter out) throws IOException {
        // Normalizar:
        if (requestPath.equals("/")) requestPath = "/index.html";

        // staticRoot apunta a algo dentro de classpath: target/classes + staticRoot + requestPath
        // Lo buscamos con ClassLoader:
        String resourcePath = staticRoot.startsWith("/") ? staticRoot.substring(1) : staticRoot;
        String filePathInResources = resourcePath + (requestPath.startsWith("/") ? requestPath.substring(1) : requestPath);

        // Leer como resource (desde target/classes)
        InputStream is = MicroWeb.class.getClassLoader().getResourceAsStream(filePathInResources);
        if (is == null) return false;

        byte[] bytes = is.readAllBytes();
        String contentType = guessContentType(requestPath);

        sendBytes(rawOut, out, 200, contentType, bytes);
        return true;
    }

    private static String guessContentType(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=UTF-8";
        if (p.endsWith(".css")) return "text/css; charset=UTF-8";
        if (p.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".svg")) return "image/svg+xml; charset=UTF-8";
        return "application/octet-stream";
    }

    private static void sendString(PrintWriter out, int status, String contentType, String body) {
        byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        out.print("HTTP/1.1 " + status + " " + reasonPhrase(status) + "\r\n");
        out.print("Content-Type: " + contentType + "\r\n");
        out.print("Content-Length: " + bytes.length + "\r\n");
        out.print("\r\n");
        out.print(body != null ? body : "");
        out.flush();
    }

    private static void sendBytes(OutputStream rawOut, PrintWriter out, int status, String contentType, byte[] bytes) throws IOException {
        out.print("HTTP/1.1 " + status + " " + reasonPhrase(status) + "\r\n");
        out.print("Content-Type: " + contentType + "\r\n");
        out.print("Content-Length: " + bytes.length + "\r\n");
        out.print("\r\n");
        out.flush();
        rawOut.write(bytes);
        rawOut.flush();
    }

    private static String reasonPhrase(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            default -> "";
        };
    }
}
