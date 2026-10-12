package za.co.petplace;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import za.co.petplace.Models.*;

/**
 * Holds the catalogue (read from frontend/data/catalog.json) and keeps users, reviews,
 * subscribers, messages and orders in memory. Data is lost when the server restarts;
 * swap these collections for a database (e.g. Spring Data JPA + H2/PostgreSQL) for production.
 */
@Component
public class DataStore {

    private final String catalogPath;
    private Catalog catalog;

    private final Map<String, User> users = new ConcurrentHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final Map<String, NewsletterRequest> subscribers = new ConcurrentHashMap<>();
    private final List<Review> reviews = new CopyOnWriteArrayList<>();
    private final List<ContactRequest> messages = new CopyOnWriteArrayList<>();
    private final List<Order> orders = new CopyOnWriteArrayList<>();

    public DataStore(@Value("${petplace.catalog-path}") String catalogPath) {
        this.catalogPath = catalogPath;
    }

    @PostConstruct
    void loadCatalog() throws IOException {
        File file = new File(catalogPath);
        if (!file.isFile()) {
            // Also work when started from the project root (e.g. the Run button in VS Code).
            File fromRoot = new File("frontend/data/catalog.json");
            if (fromRoot.isFile()) file = fromRoot;
        }
        if (!file.isFile()) {
            throw new IllegalStateException(
                    "Catalogue file not found at " + file.getAbsolutePath()
                            + ". Run the app from the backend folder, or set petplace.catalog-path.");
        }
        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.catalog = mapper.readValue(file, Catalog.class);
    }

    public Catalog catalog() { return catalog; }

    public Optional<Product> product(String id) {
        return catalog.products().stream().filter(p -> p.id().equals(id)).findFirst();
    }

    // ----- users -----
    public boolean addUser(User user) { return users.putIfAbsent(user.email(), user) == null; }

    public Optional<User> user(String email) { return Optional.ofNullable(users.get(email)); }

    public String newSession(String email) {
        String token = java.util.UUID.randomUUID().toString();
        sessions.put(token, email);
        return token;
    }

    // ----- other collections -----
    public void addReview(Review r) { reviews.add(r); }

    public List<Review> reviews() {
        return reviews.stream().sorted((a, b) -> b.createdAt().compareTo(a.createdAt())).toList();
    }

    public boolean subscribe(NewsletterRequest r) { return subscribers.putIfAbsent(r.email(), r) == null; }

    public void addMessage(ContactRequest m) { messages.add(m); }

    public void addOrder(Order o) { orders.add(o); }
}
