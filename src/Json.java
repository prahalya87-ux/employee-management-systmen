package src;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * Tiny JSON helper (no external library needed).
 * - quote/num/error/message build JSON text for responses.
 * - parseObject reads a simple, flat JSON object like {"name":"Asha","age":"25"}.
 */
public class Json {

    public static String quote(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Money values are always written with 2 decimals. */
    public static String num(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    public static String error(String msg) {
        return "{\"error\":" + quote(msg) + "}";
    }

    public static String message(String msg) {
        return "{\"message\":" + quote(msg) + "}";
    }

    /** Reads a flat JSON object into a map. All values are returned as text. */
    public static Map<String, String> parseObject(String text) {
        if (text == null || text.trim().isEmpty())
            throw new IllegalArgumentException("Request body is empty.");
        Map<String, String> map = new HashMap<>();
        int n = text.length();
        int i = skipSpaces(text, 0);
        if (i >= n || text.charAt(i) != '{') throw bad();
        i++;
        while (true) {
            i = skipSpaces(text, i);
            if (i >= n) throw bad();
            char c = text.charAt(i);
            if (c == '}') break;
            if (c == ',') { i++; continue; }
            if (c != '"') throw bad();

            int[] pos = {i};
            String key = readString(text, pos);
            i = skipSpaces(text, pos[0]);
            if (i >= n || text.charAt(i) != ':') throw bad();
            i = skipSpaces(text, i + 1);
            if (i >= n) throw bad();

            String value;
            if (text.charAt(i) == '"') {
                pos[0] = i;
                value = readString(text, pos);
                i = pos[0];
            } else {
                int start = i;
                while (i < n && text.charAt(i) != ',' && text.charAt(i) != '}') i++;
                value = text.substring(start, i).trim();
                if (value.equals("null")) value = "";
            }
            map.put(key, value);
        }
        return map;
    }

    private static String readString(String t, int[] pos) {
        StringBuilder sb = new StringBuilder();
        int i = pos[0] + 1;                       // skip the opening quote
        while (i < t.length()) {
            char c = t.charAt(i);
            if (c == '"') {
                pos[0] = i + 1;
                return sb.toString();
            }
            if (c == '\\' && i + 1 < t.length()) {
                char next = t.charAt(++i);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'u':
                        if (i + 4 >= t.length()) throw bad();
                        sb.append((char) Integer.parseInt(t.substring(i + 1, i + 5), 16));
                        i += 4;
                        break;
                    default: sb.append(next);
                }
            } else {
                sb.append(c);
            }
            i++;
        }
        throw bad();
    }

    private static int skipSpaces(String t, int i) {
        while (i < t.length() && Character.isWhitespace(t.charAt(i))) i++;
        return i;
    }

    private static IllegalArgumentException bad() {
        return new IllegalArgumentException("Invalid JSON in request.");
    }
}