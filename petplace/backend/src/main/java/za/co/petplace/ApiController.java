package za.co.petplace;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

import za.co.petplace.Models.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final double VAT_RATE = 0.15;
    private static final Map<String, Double> DELIVERY = Map.of("standard", 75.0, "collect", 0.0);
    private static final Set<String> PAYMENTS = Set.of("Credit card", "EFT", "Samsung Pay", "Apple Pay");

    private final DataStore store;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public ApiController(DataStore store) {
        this.store = store;
    }

    // ---------- catalogue ----------
    @GetMapping("/breeds")
    public List<Breed> breeds() { return store.catalog().breeds(); }

    @GetMapping("/breeds/{slug}")
    public Breed breed(@PathVariable String slug) {
        return store.catalog().breeds().stream().filter(b -> b.slug().equals(slug)).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Breed not found."));
    }

    @GetMapping("/categories")
    public List<Category> categories() { return store.catalog().categories(); }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) String category) {
        return store.catalog().products().stream()
                .filter(p -> category == null || p.category().equals(category)).toList();
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable String id) {
        return store.product(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found."));
    }

    @GetMapping("/puppies")
    public List<Puppy> puppies() { return store.catalog().puppies(); }

    // ---------- accounts ----------
    @PostMapping("/auth/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest req) {
        String name = clean(req.name(), 80);
        String email = normaliseEmail(req.email());
        if (name.isEmpty()) throw bad("Please enter your full name.");
        if (req.password() == null || req.password().length() < 8) throw bad("Password must be at least 8 characters.");
        User user = new User(name, email, encoder.encode(req.password()));
        if (!store.addUser(user)) throw new ApiException(HttpStatus.CONFLICT, "An account with that email already exists.");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(name, email, store.newSession(email)));
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@RequestBody LoginRequest req) {
        String email = normaliseEmail(req.email());
        User user = store.user(email).orElse(null);
        if (user == null || req.password() == null || !encoder.matches(req.password(), user.passwordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        }
        return new AuthResponse(user.name(), user.email(), store.newSession(email));
    }

    // ---------- reviews ----------
    @GetMapping("/reviews")
    public List<Review> reviews() { return store.reviews(); }

    @PostMapping("/reviews")
    public ResponseEntity<Review> addReview(@RequestBody ReviewRequest req) {
        for (int rating : new int[]{req.dogs(), req.product(), req.page()}) {
            if (rating < 1 || rating > 5) throw bad("Ratings must be between 1 and 5 stars.");
        }
        String author = clean(req.author(), 60);
        Review review = new Review(author.isEmpty() ? "Guest" : author, req.dogs(), req.product(), req.page(),
                clean(req.comment(), 1000), Instant.now());
        store.addReview(review);
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    // ---------- newsletter & contact ----------
    @PostMapping("/newsletter")
    public Map<String, String> newsletter(@RequestBody NewsletterRequest req) {
        String first = clean(req.firstName(), 60);
        if (first.isEmpty()) throw bad("Please enter your name.");
        NewsletterRequest sub = new NewsletterRequest(first, clean(req.lastName(), 60),
                normaliseEmail(req.email()), clean(req.country(), 60));
        store.subscribe(sub); // subscribing twice is harmless
        return Map.of("status", "subscribed");
    }

    @PostMapping("/contact")
    public ResponseEntity<Map<String, String>> contact(@RequestBody ContactRequest req) {
        String name = clean(req.name(), 80);
        String message = clean(req.message(), 2000);
        if (name.isEmpty()) throw bad("Please enter your name.");
        if (message.isEmpty()) throw bad("Please type a message.");
        store.addMessage(new ContactRequest(name, normaliseEmail(req.email()), message));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("status", "received"));
    }

    // ---------- orders ----------
    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> placeOrder(@RequestBody OrderRequest req) {
        if (req.items() == null || req.items().isEmpty()) throw bad("Your cart is empty.");
        String method = req.deliveryMethod() == null ? "" : req.deliveryMethod();
        if (!DELIVERY.containsKey(method)) throw bad("Please choose a delivery method.");
        if (req.paymentMethod() == null || !PAYMENTS.contains(req.paymentMethod())) throw bad("Please choose a payment method.");
        Address a = req.address();
        if (method.equals("standard") && (a == null || clean(a.street(), 200).isEmpty() || clean(a.city(), 100).isEmpty())) {
            throw bad("Please enter your delivery address.");
        }

        List<OrderLine> lines = new ArrayList<>();
        double subtotal = 0;
        for (OrderItemRequest item : req.items()) {
            Product p = store.product(item.productId()).orElseThrow(() -> bad("An item in your cart is no longer available."));
            if (item.quantity() < 1 || item.quantity() > 20) throw bad("Quantity must be between 1 and 20.");
            if (p.sizes() != null && !p.sizes().isEmpty() && !p.sizes().contains(item.size())) throw bad("Please choose a valid size for " + p.name() + ".");
            double lineTotal = round(p.price() * item.quantity()); // price always comes from the server
            lines.add(new OrderLine(p.id(), p.name(), item.size(), item.quantity(), p.price(), lineTotal));
            subtotal += lineTotal;
        }
        double delivery = DELIVERY.get(method);
        double total = round(subtotal + delivery);
        double vat = round(total * VAT_RATE / (1 + VAT_RATE));
        Address cleanAddress = a == null ? null : new Address(clean(a.street(), 200), clean(a.city(), 100), clean(a.postal(), 12), clean(a.coords(), 40));
        String orderId = "PP-" + (100000 + new Random().nextInt(900000));
        store.addOrder(new Order(orderId, req.email() == null ? "" : req.email().trim().toLowerCase(Locale.ROOT),
                lines, cleanAddress, method, req.paymentMethod(), round(subtotal), delivery, total, vat, Instant.now()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderId", orderId);
        body.put("subtotal", round(subtotal));
        body.put("delivery", delivery);
        body.put("total", total);
        body.put("vatIncluded", vat);
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    // ---------- helpers ----------
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    private static String clean(String s, int max) {
        if (s == null) return "";
        String t = s.strip();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String normaliseEmail(String email) {
        String e = clean(email, 120).toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(e).matches()) throw bad("Please enter a valid email address.");
        return e;
    }

    private static ApiException bad(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }

    // ---------- errors ----------
    public static class ApiException extends RuntimeException {
        final HttpStatus status;
        ApiException(HttpStatus status, String message) { super(message); this.status = status; }
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> handle(ApiException e) {
        return ResponseEntity.status(e.status).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, String>> unreadable() {
        return ResponseEntity.badRequest().body(Map.of("error", "That request couldn't be read. Please check the form and try again."));
    }
}
