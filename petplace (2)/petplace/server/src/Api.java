import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * STEP 5 - The API. Every address that starts with /api is handled here.
 * The website's JavaScript (frontend/js/app.js) calls these.
 *
 *   GET  /api/breeds   /api/categories   /api/products   /api/puppies   /api/reviews
 *   POST /api/auth/register   /api/auth/login
 *   POST /api/reviews   /api/newsletter   /api/contact   /api/orders
 */
public final class Api {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Map<String, Double> DELIVERY = Map.of("standard", 75.0, "collect", 0.0);
    private static final List<String> PAYMENTS = List.of("Credit card", "EFT", "Samsung Pay", "Apple Pay");
    private static final double VAT_RATE = 0.15;
    private static final SecureRandom RANDOM = new SecureRandom();
    // Used so that a wrong email takes as long to reject as a wrong password.
    private static final String DUMMY_HASH = Passwords.hash("not-a-real-password");

    private final Catalog catalog;
    private final Store store;

    public Api(Catalog catalog, Store store) {
        this.catalog = catalog;
        this.store = store;
    }

    /** What a route returns: an HTTP status and something to turn into JSON. */
    private static final class Result {
        final int status;
        final Object body;
        Result(int status, Object body) { this.status = status; this.body = body; }
    }

    // ------------------------------------------------------------------ entry point

    public void handle(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        if (ex.getRequestMethod().equals("OPTIONS")) {   // the browser's "may I?" check
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }
        Result result;
        try {
            result = route(ex);
        } catch (ApiError e) {
            result = new Result(e.status, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            result = new Result(500, Map.of("error", "Something went wrong on our side. Please try again."));
        }
        send(ex, result);
    }

    private Result route(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);

        if (method.equals("GET")) {
            switch (path) {
                case "/api/breeds": return ok(catalog.breeds());
                case "/api/categories": return ok(catalog.categories());
                case "/api/puppies": return ok(catalog.puppies());
                case "/api/reviews": return ok(store.reviews());
                case "/api/products": return ok(productsIn(queryParam(ex, "category")));
                default: break;
            }
            if (path.startsWith("/api/products/")) {
                Map<String, Object> product = catalog.product(path.substring("/api/products/".length()));
                if (product == null) throw new ApiError(404, "Product not found.");
                return ok(product);
            }
        } else if (method.equals("POST")) {
            switch (path) {
                case "/api/auth/register": return register(readBody(ex));
                case "/api/auth/login": return login(readBody(ex));
                case "/api/reviews": return addReview(readBody(ex));
                case "/api/newsletter": return subscribe(readBody(ex));
                case "/api/contact": return contact(readBody(ex));
                case "/api/orders": return placeOrder(readBody(ex));
                default: break;
            }
        }
        throw new ApiError(404, "That address doesn't exist.");
    }

    // ------------------------------------------------------------------ the routes

    private List<Map<String, Object>> productsIn(String category) {
        if (category == null) return catalog.products();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> p : catalog.products()) {
            if (category.equals(p.get("category"))) list.add(p);
        }
        return list;
    }

    private Result register(Map<String, Object> in) {
        String name = text(in, "name", 80);
        String email = email(in);
        Object password = in.get("password");
        if (name.isEmpty()) throw bad("Please enter your full name.");
        if (!(password instanceof String) || ((String) password).length() < 8 || ((String) password).length() > 200) {
            throw bad("Password must be at least 8 characters.");
        }
        if (!store.addUser(name, email, Passwords.hash((String) password))) {
            throw new ApiError(409, "An account with that email already exists.");
        }
        return new Result(201, session(name, email));
    }

    private Result login(Map<String, Object> in) {
        String email = email(in);
        Object password = in.get("password");
        Map<String, Object> user = store.user(email);
        boolean ok = password instanceof String
                && Passwords.matches((String) password, user == null ? DUMMY_HASH : (String) user.get("passwordHash"))
                && user != null;
        if (!ok) throw new ApiError(401, "Incorrect email or password.");
        return ok(session((String) user.get("name"), email));
    }

    private Map<String, Object> session(String name, String email) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("email", email);
        out.put("token", store.newSession(email));
        return out;
    }

    private Result addReview(Map<String, Object> in) {
        int dogs = whole(in, "dogs"), product = whole(in, "product"), page = whole(in, "page");
        for (int rating : new int[]{dogs, product, page}) {
            if (rating < 1 || rating > 5) throw bad("Ratings must be between 1 and 5 stars.");
        }
        String author = text(in, "author", 60);
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("author", author.isEmpty() ? "Guest" : author);
        review.put("dogs", dogs);
        review.put("product", product);
        review.put("page", page);
        review.put("comment", text(in, "comment", 1000));
        review.put("createdAt", Instant.now().toString());
        store.addReview(review);
        return new Result(201, review);
    }

    private Result subscribe(Map<String, Object> in) {
        String first = text(in, "firstName", 60);
        if (first.isEmpty()) throw bad("Please enter your name.");
        String email = email(in);
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("firstName", first);
        sub.put("lastName", text(in, "lastName", 60));
        sub.put("email", email);
        sub.put("country", text(in, "country", 60));
        sub.put("subscribedAt", Instant.now().toString());
        store.addSubscriber(email, sub);   // signing up twice just updates the entry
        return ok(Map.of("status", "subscribed"));
    }

    private Result contact(Map<String, Object> in) {
        String name = text(in, "name", 80);
        String message = text(in, "message", 2000);
        if (name.isEmpty()) throw bad("Please enter your name.");
        String email = email(in);
        if (message.isEmpty()) throw bad("Please type a message.");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("email", email);
        m.put("message", message);
        m.put("receivedAt", Instant.now().toString());
        store.addMessage(m);
        return new Result(201, Map.of("status", "received"));
    }

    @SuppressWarnings("unchecked")
    private Result placeOrder(Map<String, Object> in) {
        Object rawItems = in.get("items");
        if (!(rawItems instanceof List) || ((List<Object>) rawItems).isEmpty()) throw bad("Your cart is empty.");
        List<Object> items = (List<Object>) rawItems;
        if (items.size() > 50) throw bad("That's too many different items in one order.");

        String method = text(in, "deliveryMethod", 20);
        if (!DELIVERY.containsKey(method)) throw bad("Please choose a delivery method.");
        String payment = text(in, "paymentMethod", 30);
        if (!PAYMENTS.contains(payment)) throw bad("Please choose a payment method.");

        Map<String, Object> address = null;
        if (in.get("address") instanceof Map) {
            Map<String, Object> a = (Map<String, Object>) in.get("address");
            address = new LinkedHashMap<>();
            address.put("street", text(a, "street", 200));
            address.put("city", text(a, "city", 100));
            address.put("postal", text(a, "postal", 12));
            address.put("coords", text(a, "coords", 40));
        }
        if (method.equals("standard") && (address == null
                || ((String) address.get("street")).isEmpty() || ((String) address.get("city")).isEmpty())) {
            throw bad("Please enter your delivery address.");
        }

        // Prices always come from OUR catalogue, never from the browser.
        List<Object> lines = new ArrayList<>();
        double subtotal = 0;
        for (Object raw : items) {
            if (!(raw instanceof Map)) throw bad("Something is wrong with an item in your cart.");
            Map<String, Object> item = (Map<String, Object>) raw;
            Map<String, Object> product = catalog.product(text(item, "productId", 40));
            if (product == null) throw bad("An item in your cart is no longer available.");
            int quantity = whole(item, "quantity");
            if (quantity < 1 || quantity > 20) throw bad("Quantity must be between 1 and 20.");
            String size = text(item, "size", 30);
            List<Object> sizes = (List<Object>) product.get("sizes");
            if (sizes != null && !sizes.isEmpty() && !sizes.contains(size)) {
                throw bad("Please choose a valid size for " + product.get("name") + ".");
            }
            double price = ((Number) product.get("price")).doubleValue();
            double lineTotal = round(price * quantity);
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productId", product.get("id"));
            line.put("name", product.get("name"));
            line.put("size", size);
            line.put("quantity", quantity);
            line.put("unitPrice", price);
            line.put("lineTotal", lineTotal);
            lines.add(line);
            subtotal += lineTotal;
        }

        double delivery = DELIVERY.get(method);
        double total = round(subtotal + delivery);
        double vat = round(total * VAT_RATE / (1 + VAT_RATE));   // VAT is already inside the total
        String orderId = "PP-" + (100000 + RANDOM.nextInt(900000));

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", orderId);
        order.put("email", in.get("email") instanceof String ? ((String) in.get("email")).trim().toLowerCase() : "");
        order.put("lines", lines);
        order.put("address", address);
        order.put("deliveryMethod", method);
        order.put("paymentMethod", payment);
        order.put("subtotal", round(subtotal));
        order.put("delivery", delivery);
        order.put("total", total);
        order.put("vatIncluded", vat);
        order.put("createdAt", Instant.now().toString());
        store.addOrder(order);

        Map<String, Object> reply = new LinkedHashMap<>();
        reply.put("orderId", orderId);
        reply.put("subtotal", round(subtotal));
        reply.put("delivery", delivery);
        reply.put("total", total);
        reply.put("vatIncluded", vat);
        return new Result(201, reply);
    }

    // ------------------------------------------------------------------ small helpers

    private static Result ok(Object body) { return new Result(200, body); }

    // ApiError (a problem we want to tell the visitor about) lives in its own file, ApiError.java.
    private static ApiError bad(String message) { return ApiError.badRequest(message); }

    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    /** A text field, trimmed and cut to a maximum length. Missing or not-text becomes "". */
    private static String text(Map<String, Object> map, String key, int max) {
        Object v = map.get(key);
        if (!(v instanceof String)) return "";
        String t = ((String) v).trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static int whole(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private static String email(Map<String, Object> map) {
        String e = text(map, "email", 120).toLowerCase();
        if (!EMAIL.matcher(e).matches()) throw bad("Please enter a valid email address.");
        return e;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readBody(HttpExchange ex) throws IOException {
        byte[] bytes = ex.getRequestBody().readNBytes(100_001);
        if (bytes.length > 100_000) throw new ApiError(413, "That request is too large.");
        try {
            Object value = Json.parse(new String(bytes, StandardCharsets.UTF_8));
            if (value instanceof Map) return (Map<String, Object>) value;
        } catch (IllegalArgumentException e) {
            // fall through to the message below
        }
        throw bad("That request couldn't be read. Please check the form and try again.");
    }

    private static String queryParam(HttpExchange ex, String name) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void send(HttpExchange ex, Result result) throws IOException {
        byte[] bytes = Json.stringify(result.body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(result.status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Lets the website call this server even when the page was opened from another local port. */
    private static void addCorsHeaders(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin != null && (origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:"))) {
            ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            ex.getResponseHeaders().set("Vary", "Origin");
            ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
        }
    }
}
