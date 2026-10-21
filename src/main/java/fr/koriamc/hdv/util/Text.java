package fr.koriamc.hdv.util;

import org.bukkit.ChatColor;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Outils de manipulation de texte : couleurs, remplacement des variables,
 * formatage des nombres.
 */
public final class Text {

    private static final DecimalFormatSymbols SYMBOLS = new DecimalFormatSymbols(Locale.FRANCE);

    static {
        SYMBOLS.setGroupingSeparator(' ');
        SYMBOLS.setDecimalSeparator(',');
    }

    private Text() {
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    public static List<String> color(List<String> lines) {
        List<String> out = new ArrayList<String>(lines.size());
        for (String line : lines) {
            out.add(color(line));
        }
        return out;
    }

    /** Retire les couleurs, y compris les codes "&" non traduits. */
    public static String strip(String s) {
        return s == null ? "" : ChatColor.stripColor(color(s));
    }

    /**
     * Remplace les variables {cle} par leur valeur. Les arguments vont par paires :
     * replace(texte, "player", nom, "faction", tag).
     */
    public static String replace(String s, Object... kv) {
        if (s == null || kv == null || kv.length < 2 || s.indexOf('{') < 0) {
            return s;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            String key = "{" + kv[i] + "}";
            if (s.contains(key)) {
                Object value = kv[i + 1];
                s = s.replace(key, value == null ? "" : String.valueOf(value));
            }
        }
        return s;
    }

    public static List<String> replace(List<String> lines, Object... kv) {
        List<String> out = new ArrayList<String>(lines.size());
        for (String line : lines) {
            out.add(replace(line, kv));
        }
        return out;
    }

    /** Nombre avec au plus {@code decimals} décimales et séparateur de milliers. */
    public static String number(double value, int decimals) {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.');
            for (int i = 0; i < decimals; i++) {
                pattern.append('#');
            }
        }
        return new DecimalFormat(pattern.toString(), SYMBOLS).format(value);
    }

    public static String power(double value) {
        return number(value, 1);
    }

    public static String join(Collection<String> items, String separator) {
        StringBuilder sb = new StringBuilder();
        Iterator<String> it = items.iterator();
        while (it.hasNext()) {
            sb.append(it.next());
            if (it.hasNext()) {
                sb.append(separator);
            }
        }
        return sb.toString();
    }

    public static String join(String[] args, int from, String separator) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                sb.append(separator);
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }

    /** Tronque une chaîne colorée à {@code max} caractères sans couper un code couleur. */
    public static String truncate(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        String cut = s.substring(0, max);
        if (cut.endsWith(String.valueOf(ChatColor.COLOR_CHAR))) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    public static Double parseDouble(String s) {
        if (s == null) {
            return null;
        }
        try {
            double d = Double.parseDouble(s.replace(',', '.'));
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return null;
            }
            return d;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Integer parseInt(String s) {
        if (s == null) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static boolean parseBoolean(String s, boolean def) {
        if (s == null) {
            return def;
        }
        String v = s.toLowerCase(Locale.ROOT);
        if (v.equals("on") || v.equals("true") || v.equals("oui") || v.equals("yes") || v.equals("1")) {
            return true;
        }
        if (v.equals("off") || v.equals("false") || v.equals("non") || v.equals("no") || v.equals("0")) {
            return false;
        }
        return def;
    }

    /** Filtre les suggestions d'autocomplétion commençant par le texte saisi. */
    public static List<String> complete(String input, Collection<String> options) {
        List<String> out = new ArrayList<String>();
        String lower = input == null ? "" : input.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option != null && option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
