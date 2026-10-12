import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * STEP 6 - Serves the website itself (the files in the frontend folder),
 * so one server gives you both the pages and the API.
 */
public final class StaticFiles {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("json", "application/json; charset=utf-8"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("md", "text/plain; charset=utf-8"),
            Map.entry("txt", "text/plain; charset=utf-8"));

    private final Path root;

    public StaticFiles(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!method.equals("GET") && !method.equals("HEAD")) {
            reply(ex, 405, "text/plain; charset=utf-8", "Method not allowed".getBytes(StandardCharsets.UTF_8), false);
            return;
        }
        String requested = ex.getRequestURI().getPath();
        if (requested.equals("/")) requested = "/index.html";

        // Only ever serve files that are really inside the frontend folder.
        Path file = root.resolve(requested.substring(1)).normalize();
        if (!file.startsWith(root)) {
            reply(ex, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8), false);
            return;
        }
        if (Files.isDirectory(file)) file = file.resolve("index.html");
        if (!Files.isRegularFile(file)) {
            reply(ex, 404, "text/plain; charset=utf-8", "Page not found".getBytes(StandardCharsets.UTF_8), method.equals("HEAD"));
            return;
        }
        String name = file.getFileName().toString();
        String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String type = TYPES.getOrDefault(extension, "application/octet-stream");
        reply(ex, 200, type, Files.readAllBytes(file), method.equals("HEAD"));
    }

    private static void reply(HttpExchange ex, int status, String type, byte[] bytes, boolean headOnly) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        if (headOnly) {
            ex.sendResponseHeaders(status, -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
