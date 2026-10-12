import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * STEP 3 - The shop catalogue: breeds, categories, products and puppies.
 * It is read once from frontend/data/catalog.json, the same file the website uses.
 */
public final class Catalog {

    private final Map<String, Object> data;

    @SuppressWarnings("unchecked")
    public Catalog(Path file) throws IOException {
        String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        this.data = (Map<String, Object>) Json.parse(text);
    }

    public Object breeds() { return data.get("breeds"); }

    public Object categories() { return data.get("categories"); }

    public Object puppies() { return data.get("puppies"); }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> products() {
        return (List<Map<String, Object>>) data.get("products");
    }

    /** Returns the product with this id, or null if there is none. */
    public Map<String, Object> product(String id) {
        for (Map<String, Object> p : products()) {
            if (p.get("id").equals(id)) return p;
        }
        return null;
    }
}
