package util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser and writer, written by hand so the project has NO
 * external library dependencies (lab machines only have a plain JDK).
 *
 * Mapping between JSON and Java types:
 *
 *     object  <->  java.util.LinkedHashMap&lt;String,Object&gt; (keeps key order)
 *     array   <->  java.util.ArrayList&lt;Object&gt;
 *     string  <->  String
 *     number  <->  Double
 *     true/false <-> Boolean
 *     null    <->  null
 *
 * Parse errors carry the exact line and column so the user can find the
 * problem in the file.
 */
public final class Json {

    /** Thrown when the input text is not valid JSON. */
    public static class JsonException extends Exception {
        public JsonException(String message) { super(message); }
    }

    // ------------------------------------------------------------- parsing

    /** Parses a complete JSON document. */
    public static Object parse(String text) throws JsonException {
        Json p = new Json(text);
        p.skipWhitespace();
        Object value = p.parseValue();
        p.skipWhitespace();
        if (p.pos < p.text.length()) {
            throw p.error("unexpected extra content after the end of the JSON document");
        }
        return value;
    }

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text == null ? "" : text;
    }

    private Object parseValue() throws JsonException {
        if (pos >= text.length()) throw error("unexpected end of file");
        char c = text.charAt(pos);
        switch (c) {
            case '{': return parseObject();
            case '[': return parseArray();
            case '"': return parseString();
            case 't': expectWord("true");  return Boolean.TRUE;
            case 'f': expectWord("false"); return Boolean.FALSE;
            case 'n': expectWord("null");  return null;
            default:  return parseNumber();
        }
    }

    private Map<String, Object> parseObject() throws JsonException {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        pos++; // consume '{'
        skipWhitespace();
        if (peek() == '}') { pos++; return map; }
        while (true) {
            skipWhitespace();
            if (peek() != '"') throw error("expected a quoted key inside the object");
            String key = parseString();
            skipWhitespace();
            if (peek() != ':') throw error("expected ':' after key \"" + key + "\"");
            pos++;
            skipWhitespace();
            map.put(key, parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == '}') { pos++; return map; }
            throw error("expected ',' or '}' inside the object");
        }
    }

    private List<Object> parseArray() throws JsonException {
        List<Object> list = new ArrayList<>();
        pos++; // consume '['
        skipWhitespace();
        if (peek() == ']') { pos++; return list; }
        while (true) {
            skipWhitespace();
            list.add(parseValue());
            skipWhitespace();
            char c = peek();
            if (c == ',') { pos++; continue; }
            if (c == ']') { pos++; return list; }
            throw error("expected ',' or ']' inside the array");
        }
    }

    private String parseString() throws JsonException {
        pos++; // consume opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) throw error("string is missing its closing quote");
            char c = text.charAt(pos++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                if (pos >= text.length()) throw error("incomplete escape sequence");
                char e = text.charAt(pos++);
                switch (e) {
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case 'u':
                        if (pos + 4 > text.length()) throw error("incomplete \\u escape sequence");
                        try {
                            sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw error("invalid \\u escape sequence");
                        }
                        pos += 4;
                        break;
                    default: throw error("unknown escape sequence \\" + e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private Double parseNumber() throws JsonException {
        int start = pos;
        while (pos < text.length() && "+-.eE0123456789".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        String token = text.substring(start, pos);
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException ex) {
            throw error("\"" + token + "\" is not a valid JSON value");
        }
    }

    private void expectWord(String word) throws JsonException {
        if (!text.startsWith(word, pos)) throw error("expected \"" + word + "\"");
        pos += word.length();
    }

    private char peek() {
        return pos < text.length() ? text.charAt(pos) : '\0';
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
    }

    /** Builds an exception whose message contains the line and column of the error. */
    private JsonException error(String message) {
        int line = 1, column = 1;
        for (int i = 0; i < pos && i < text.length(); i++) {
            if (text.charAt(i) == '\n') { line++; column = 1; } else { column++; }
        }
        return new JsonException("Invalid JSON at line " + line + ", column " + column
                + ": " + message + ".");
    }

    // ------------------------------------------------------------- writing

    /** Serializes a Map/List/String/Number/Boolean/null tree to pretty JSON. */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            // print whole numbers without the trailing ".0" (matches the spec's sample file)
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                sb.append((long) d);
            } else {
                sb.append(d);
            }
        } else if (value instanceof Map) {
            writeObject(sb, (Map<?, ?>) value, indent);
        } else if (value instanceof List) {
            writeArray(sb, (List<?>) value, indent);
        } else {
            throw new IllegalArgumentException("Cannot serialize type " + value.getClass());
        }
    }

    private static void writeObject(StringBuilder sb, Map<?, ?> map, int indent) {
        if (map.isEmpty()) { sb.append("{}"); return; }
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            appendIndent(sb, indent + 1);
            writeString(sb, String.valueOf(entry.getKey()));
            sb.append(": ");
            writeValue(sb, entry.getValue(), indent + 1);
            if (++i < map.size()) sb.append(',');
            sb.append('\n');
        }
        appendIndent(sb, indent);
        sb.append('}');
    }

    private static void writeArray(StringBuilder sb, List<?> list, int indent) {
        if (list.isEmpty()) { sb.append("[]"); return; }
        sb.append("[\n");
        for (int i = 0; i < list.size(); i++) {
            appendIndent(sb, indent + 1);
            writeValue(sb, list.get(i), indent + 1);
            if (i < list.size() - 1) sb.append(',');
            sb.append('\n');
        }
        appendIndent(sb, indent);
        sb.append(']');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) sb.append("  ");
    }
}
