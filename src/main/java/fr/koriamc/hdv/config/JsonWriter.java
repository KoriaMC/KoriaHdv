package fr.koriamc.hdv.config;

import java.math.BigDecimal;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Écriture JSON lisible et compacte : un objet ou une liste tient sur une seule
 * ligne tant qu'elle ne dépasse pas {@link #WIDTH} caractères. Les accents et
 * les codes couleur "&" sont écrits tels quels.
 */
public final class JsonWriter {

    private static final int WIDTH = 130;
    private static final String INDENT = "  ";

    private JsonWriter() {
    }

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, 0, 0, true);
        return sb.append('\n').toString();
    }

    /**
     * @param indent    profondeur d'indentation
     * @param prefix    nombre de caractères déjà écrits sur la ligne (clé)
     * @param forceOpen développe toujours l'objet (racine du fichier)
     */
    private static void write(StringBuilder sb, Object value, int indent, int prefix, boolean forceOpen) {
        String inline = forceOpen ? null : inline(value);
        if (inline != null && indent * INDENT.length() + prefix + inline.length() <= WIDTH) {
            sb.append(inline);
            return;
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                String key = quote(String.valueOf(e.getKey())) + ": ";
                indent(sb, indent + 1);
                sb.append(key);
                write(sb, e.getValue(), indent + 1, key.length(), false);
                sb.append(it.hasNext() ? ",\n" : "\n");
            }
            indent(sb, indent);
            sb.append('}');
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            if (isScalarList(list)) {
                // valeurs simples : plusieurs par ligne tant que la largeur le permet
                int width = (indent + 1) * INDENT.length();
                int used = 0;
                indent(sb, indent + 1);
                for (int i = 0; i < list.size(); i++) {
                    String item = scalar(list.get(i)) + (i + 1 < list.size() ? "," : "");
                    if (used > 0 && width + used + 1 + item.length() > WIDTH) {
                        sb.append('\n');
                        indent(sb, indent + 1);
                        used = 0;
                    }
                    if (used > 0) {
                        sb.append(' ');
                        used++;
                    }
                    sb.append(item);
                    used += item.length();
                }
                sb.append('\n');
            } else {
                for (int i = 0; i < list.size(); i++) {
                    indent(sb, indent + 1);
                    write(sb, list.get(i), indent + 1, 0, false);
                    sb.append(i + 1 < list.size() ? ",\n" : "\n");
                }
            }
            indent(sb, indent);
            sb.append(']');
        } else {
            sb.append(scalar(value));
        }
    }

    /** Liste de valeurs courtes (noms, identifiants) : regroupées sur quelques lignes. */
    private static boolean isScalarList(List<?> list) {
        for (Object o : list) {
            if (o instanceof Map || o instanceof List || scalar(o).length() > 32) {
                return false;
            }
        }
        return true;
    }

    /** Représentation sur une ligne, ou null si l'objet contient un objet imbriqué trop long. */
    private static String inline(Object value) {
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            if (map.isEmpty()) {
                return "{}";
            }
            StringBuilder sb = new StringBuilder("{ ");
            Iterator<? extends Map.Entry<?, ?>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<?, ?> e = it.next();
                String inner = inline(e.getValue());
                if (inner == null) {
                    return null;
                }
                sb.append(quote(String.valueOf(e.getKey()))).append(": ").append(inner);
                if (it.hasNext()) {
                    sb.append(", ");
                }
            }
            return sb.append(" }").toString();
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                String inner = inline(list.get(i));
                if (inner == null) {
                    return null;
                }
                sb.append(inner);
                if (i + 1 < list.size()) {
                    sb.append(", ");
                }
            }
            return sb.append(']').toString();
        }
        return scalar(value);
    }

    private static String scalar(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte) {
            return value.toString();
        }
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return "0";
            }
            if (d == Math.rint(d) && Math.abs(d) < 1e15) {
                return (long) d + ".0";
            }
            return BigDecimal.valueOf(d).toPlainString();
        }
        return quote(value.toString());
    }

    public static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0; i < depth; i++) {
            sb.append(INDENT);
        }
    }
}
