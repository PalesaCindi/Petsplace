import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * STEP 7 - Start here. This builds the pieces and starts the web server.
 *
 * Run it from the "server" folder:   run.bat   (Windows)   or   ./run.sh   (Mac/Linux)
 * Then open http://localhost:8080
 */
public final class Main {

    public static void main(String[] args) throws IOException {
        Path frontend = findFrontend();
        Catalog catalog = new Catalog(frontend.resolve("data").resolve("catalog.json"));
        Store store = new Store(Path.of("data", "store.json"));
        Api api = new Api(catalog, store);
        StaticFiles site = new StaticFiles(frontend);

        int port = 8080;
        String fromEnv = System.getenv("PORT");
        if (fromEnv != null && fromEnv.matches("\\d{2,5}")) port = Integer.parseInt(fromEnv);

        // Only this computer can reach the server (127.0.0.1), which is safest while developing.
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/api", api::handle);
        server.createContext("/", site::handle);
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("PetPlace is running.");
        System.out.println("  Website folder: " + frontend.toAbsolutePath().normalize());
        System.out.println("  Open:           http://localhost:" + port);
        System.out.println("  Stop with:      Ctrl + C");
    }

    /** Looks for the website folder next to the server folder. */
    private static Path findFrontend() {
        for (String candidate : new String[]{"../frontend", "frontend", "../../frontend"}) {
            Path p = Path.of(candidate);
            if (Files.isRegularFile(p.resolve("data").resolve("catalog.json"))) return p;
        }
        throw new IllegalStateException(
                "Could not find the frontend folder. Run the server from the petplace/server folder.");
    }
}
