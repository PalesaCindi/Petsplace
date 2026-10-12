package za.co.petplace;

import java.time.Instant;
import java.util.List;

/** Plain data types used by the API. */
public final class Models {

    private Models() {}

    // ----- catalogue (loaded from frontend/data/catalog.json) -----
    public record Breed(String slug, String name, String image, String description) {}

    public record Category(String slug, String name, String image) {}

    public record Product(String id, String name, double price, String category, String image, List<String> sizes) {}

    public record Puppy(String id, String name, String breed, int ageWeeks, String status) {}

    public record Catalog(List<Breed> breeds, List<Category> categories, List<Product> products, List<Puppy> puppies) {}

    // ----- requests -----
    public record RegisterRequest(String name, String email, String password) {}

    public record LoginRequest(String email, String password) {}

    public record NewsletterRequest(String firstName, String lastName, String email, String country) {}

    public record ContactRequest(String name, String email, String message) {}

    public record ReviewRequest(String author, int dogs, int product, int page, String comment) {}

    public record Address(String street, String city, String postal, String coords) {}

    public record OrderItemRequest(String productId, String size, int quantity) {}

    public record OrderRequest(String email, List<OrderItemRequest> items, Address address,
                               String deliveryMethod, String paymentMethod) {}

    // ----- stored / returned -----
    public record User(String name, String email, String passwordHash) {}

    public record AuthResponse(String name, String email, String token) {}

    public record Review(String author, int dogs, int product, int page, String comment, Instant createdAt) {}

    public record OrderLine(String productId, String name, String size, int quantity, double unitPrice, double lineTotal) {}

    public record Order(String orderId, String email, List<OrderLine> lines, Address address,
                        String deliveryMethod, String paymentMethod,
                        double subtotal, double delivery, double total, double vatIncluded,
                        Instant createdAt) {}
}
