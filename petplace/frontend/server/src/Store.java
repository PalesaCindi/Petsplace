import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * STEP 4 - Where accounts, reviews, newsletter sign-ups, messages and orders are kept.
 * Everything is saved to server/data/store.json, so it survives a restart.
 * (For a bigger site you would swap this file for a real database.)
 */
public final class Store {

    private final Path file;
    private Map<String, Map<String, Object>> users = new LinkedHashMap<>();       // by email
    private Map<String, Map<String, Object>> subscribers = new LinkedHashMap<>(); // by email
    private List<Object> reviews = new ArrayList<>();
    private List<Object> messages = new ArrayList<>();
    private List<Object> orders = new ArrayList<>();
    private final Map<String, String> sessions = new LinkedHashMap<>();            // token -> email

    public Store(Path file) {
        this.file = file;
        load();
    }

    // ---- accounts
    public synchronized boolean addUser(String name, String email, String passwordHash) {
        if (users.containsKey(email)) return false;
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("name", name);
        user.put("email", email);
        user.put("passwordHash", passwordHash);
        user.put("createdAt", Instant.now().toString());
        users.put(email, user);
        save();
        return true;
    }

    public synchronized Map<String, Object> user(String email) {
        return users.get(email);
    }

    public synchronized String newSession(String email) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, email);
        return token;
    }

    // ---- everything else
    public synchronized void addReview(Map<String, Object> review) {
        reviews.add(review);
        save();
    }

    /** Newest first. */
    public synchronized List<Object> reviews() {
        List<Object> copy = new ArrayList<>(reviews);
        java.util.Collections.reverse(copy);
        return copy;
    }

    public synchronized void addSubscriber(String email, Map<String, Object> subscriber) {
        subscribers.put(email, subscriber);
        save();
    }

    public synchronized void addMessage(Map<String, Object> message) {
        messages.add(message);
        save();
    }

    public synchronized void addOrder(Map<String, Object> order) {
        orders.add(order);
        save();
    }

    // ---- saving and loading
    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            Map<String, Object> root = (Map<String, Object>) Json.parse(
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            if (root.get("users") != null) users = (Map<String, Map<String, Object>>) root.get("users");
            if (root.get("subscribers") != null) subscribers = (Map<String, Map<String, Object>>) root.get("subscribers");
            if (root.get("reviews") != null) reviews = (List<Object>) root.get("reviews");
            if (root.get("messages") != null) messages = (List<Object>) root.get("messages");
            if (root.get("orders") != null) orders = (List<Object>) root.get("orders");
        } catch (IOException | RuntimeException e) {
            System.err.println("Could not read " + file + " (" + e.getMessage() + "). Starting with empty data.");
        }
    }

    private void save() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("users", users);
        root.put("subscribers", subscribers);
        root.put("reviews", reviews);
        root.put("messages", messages);
        root.put("orders", orders);
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, Json.stringify(root).getBytes(StandardCharsets.UTF_8));
            Files.move(temp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Could not save " + file + ": " + e.getMessage());
        }
    }
}
