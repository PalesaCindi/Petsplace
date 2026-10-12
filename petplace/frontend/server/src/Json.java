import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * STEP 1 - A tiny JSON reader and writer, so the server needs no outside libraries.
 *
 * parse("{\"a\": 1}")  ->  a Map   (objects become Map, arrays become List,
 *                                    numbers become Double, text becomes String)
 * stringify(map)      ->  JSON text
 */
public final class Json {

    private final String s;
    private int i = 0;
    private int depth = 0;

    private Json(String s) {
        this.s = s;
    }

    // ------------------------------------------------------------------ reading

    public static Object parse(String text) {
        Json p = new Json(text);
        p.skipSpaces();
        Object value = p.value();
        p.skipSpaces();
        if (p.i != p.s.length()) throw p.error("Unexpected text after the JSON");
        return value;
    }

    private Object value() {
        if (i >= s.length()) throw error("Unexpected end of JSON");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        if (++depth > 50) throw error("JSON is nested too deeply");
        Map<String, Object> map = new LinkedHashMap<>();
        i++; // {
        skipSpaces();
        if (peek('}')) { i++; depth--; return map; }
        while (true) {
            skipSpaces();
            String key = string();
            skipSpaces();
            need(':');
            skipSpaces();
            map.put(key, value());
            skipSpaces();
            if (peek(',')) { i++; continue; }
            need('}');
            depth--;
            return map;
        }
    }

    private List<Object> array() {
        if (++depth > 50) throw error("JSON is nested too deeply");
        List<Object> list = new ArrayList<>();
        i++; // [
        skipSpaces();
        if (peek(']')) { i++; depth--; return list; }
        while (true) {
            skipSpaces();
            list.add(value());
            skipSpaces();
            if (peek(',')) { i++; continue; }
            need(']');
            depth--;
            return list;
        }
    }

    private String string() {
        need('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            if (i >= s.length()) throw error("Text is not closed");
            char c = s.charAt(i++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (i >= s.length()) throw error("Bad escape");
            char e = s.charAt(i++);
            switch (e) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (i + 4 > s.length()) throw error("Bad \\u escape");
                    out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: throw error("Bad escape");
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error("Unexpected character '" + s.charAt(i) + "'");
        try {
            return Double.parseDouble(s.substring(start, i));
        } catch (NumberFormatException e) {
            throw error("Bad number");
        }
    }

    private void skipSpaces() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private boolean peek(char c) {
        return i < s.length() && s.charAt(i) == c;
    }

    private void need(char c) {
        if (!peek(c)) throw error("Expected '" + c + "'");
        i++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " (at position " + i + ")");
    }

    // ------------------------------------------------------------------ writing

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object v, StringBuilder out) {
        if (v == null) {
            out.append("null");
        } else if (v instanceof String) {
            quote((String) v, out);
        } else if (v instanceof Boolean) {
            out.append(v);
        } else if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) out.append((long) d); // 500.0 -> 500
            else out.append(d);
        } else if (v instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : ((Map<?, ?>) v).entrySet()) {
                if (!first) out.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), out);
                out.append(':');
                write(e.getValue(), out);
            }
            out.append('}');
        } else if (v instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) v) {
                if (!first) out.append(',');
                first = false;
                write(item, out);
            }
            out.append(']');
        } else {
            quote(v.toString(), out);
        }
    }

    private static void quote(String text, StringBuilder out) {
        out.append('"');
        for (int k = 0; k < text.length(); k++) {
            char c = text.charAt(k);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
            }
        }
        out.append('"');
    }
}
