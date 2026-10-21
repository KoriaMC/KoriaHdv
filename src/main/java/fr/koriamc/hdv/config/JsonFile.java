package fr.koriamc.hdv.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fichier de configuration JSON du dossier du plugin. Les options absentes
 * prennent la valeur du fichier embarqué dans le jar. Le menu /f config modifie
 * l'arbre puis réécrit le fichier.
 */
public final class JsonFile {

    private static final Pattern POSITION = Pattern.compile("line (\\d+) column (\\d+)");

    private final JavaPlugin plugin;
    private final String name;
    private final File file;
    private final List<String> freeForm;
    private Map<String, Object> tree = new LinkedHashMap<String, Object>();
    private Map<String, Object> defaults = new LinkedHashMap<String, Object>();
    private boolean loadedOnce;

    /**
     * @param freeForm sections dont les entrées peuvent être ajoutées ou supprimées
     *                 (les valeurs du jar ne les complètent pas)
     */
    public JsonFile(JavaPlugin plugin, String name, String... freeForm) {
        this.plugin = plugin;
        this.name = name;
        this.file = new File(plugin.getDataFolder(), name);
        this.freeForm = Arrays.asList(freeForm);
    }

    public String getName() {
        return name;
    }

    /**
     * Lit le fichier. En cas d'erreur de syntaxe lors d'un rechargement, la
     * version précédente reste en place (au premier chargement : valeurs du jar).
     *
     * @return une description courte de l'erreur, ou null si le fichier est valide
     */
    public String load() {
        if (!file.exists()) {
            plugin.saveResource(name, false);
        }
        InputStream in = plugin.getResource(name);
        if (in != null) {
            try {
                defaults = parse(readAll(in));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Fichier par défaut " + name + " illisible", e);
            }
        }
        String error = null;
        Map<String, Object> read = null;
        try {
            read = parse(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        } catch (IOException e) {
            error = "lecture impossible (" + e.getMessage() + ")";
        } catch (JsonParseException e) {
            error = summarize(e.getMessage());
        } catch (IllegalStateException e) {
            error = "le fichier doit contenir un objet JSON { ... }";
        }
        if (error != null) {
            plugin.getLogger().severe("Erreur dans " + name + " : " + error);
            if (loadedOnce) {
                plugin.getLogger().severe("L'ancienne version de " + name + " reste utilisée tant que l'erreur n'est pas corrigée.");
                return error;
            }
            plugin.getLogger().severe("Les valeurs par défaut de " + name + " sont utilisées tant que l'erreur n'est pas corrigée.");
            read = new LinkedHashMap<String, Object>();
        }
        tree = read;
        List<String> missing = new ArrayList<String>();
        collectMissing(defaults, tree, "", missing);
        if (!missing.isEmpty()) {
            plugin.getLogger().info(name + " : " + missing.size() + " option(s) absente(s), valeur par défaut utilisée : "
                    + (missing.size() > 8 ? missing.subList(0, 8) + "..." : missing));
        }
        loadedOnce = true;
        return error;
    }

    private void collectMissing(Map<String, Object> def, Map<String, Object> user, String prefix, List<String> out) {
        for (Map.Entry<String, Object> e : def.entrySet()) {
            String path = prefix + e.getKey();
            if (!user.containsKey(e.getKey())) {
                out.add(path);
            } else if (e.getValue() instanceof Map && user.get(e.getKey()) instanceof Map && !isFreeForm(path)) {
                collectMissing(castMap(e.getValue()), castMap(user.get(e.getKey())), path + ".", out);
            }
        }
    }

    /** Une section libre (ex. liste des améliorations) n'est pas complétée par le jar. */
    public boolean isFreeForm(String path) {
        for (String section : freeForm) {
            if (matches(section, path)) {
                return true;
            }
        }
        return false;
    }

    /** Comparaison de chemins avec le joker "*" pour un segment. */
    private static boolean matches(String pattern, String path) {
        String[] p = pattern.split("\\.");
        String[] s = path.split("\\.");
        if (p.length != s.length) {
            return false;
        }
        for (int i = 0; i < p.length; i++) {
            if (!p[i].equals("*") && !p[i].equals(s[i])) {
                return false;
            }
        }
        return true;
    }

    // ================================================================= Arbre

    public Map<String, Object> getTree() {
        return tree;
    }

    public Map<String, Object> getDefaults() {
        return defaults;
    }

    public static Object get(Map<String, Object> root, List<String> path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<?, ?>) current).get(key);
        }
        return current;
    }

    /** Valeur effective : celle du fichier, sinon celle du jar. */
    public Object getEffective(List<String> path) {
        Object value = get(tree, path);
        return value != null ? value : get(defaults, path);
    }

    public void set(List<String> path, Object value) {
        Map<String, Object> current = tree;
        for (int i = 0; i < path.size() - 1; i++) {
            Object next = current.get(path.get(i));
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(path.get(i), next);
            }
            current = castMap(next);
        }
        current.put(path.get(path.size() - 1), value);
    }

    public boolean remove(List<String> path) {
        Object parent = get(tree, path.subList(0, path.size() - 1));
        return parent instanceof Map && ((Map<?, ?>) parent).remove(path.get(path.size() - 1)) != null;
    }

    /** Réécrit le fichier (écriture dans un fichier temporaire puis remplacement). */
    public void save() throws IOException {
        File temp = new File(file.getParentFile(), name + ".tmp");
        Files.write(temp.toPath(), JsonWriter.write(tree).getBytes(StandardCharsets.UTF_8));
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    // ======================================================= Configuration

    /** Vue Bukkit (getString, getInt...) avec les valeurs du jar par défaut. */
    public Configuration toConfiguration() {
        return combine(Collections.singletonList(this));
    }

    /** Réunit plusieurs fichiers dans une seule vue (leurs sections racines sont distinctes). */
    public static Configuration combine(List<JsonFile> files) {
        MemoryConfiguration config = new MemoryConfiguration();
        MemoryConfiguration defaults = new MemoryConfiguration();
        for (JsonFile f : files) {
            fill(config, f.tree);
            fill(defaults, f.defaults);
        }
        config.setDefaults(defaults);
        return config;
    }

    private static void fill(ConfigurationSection target, Map<String, Object> map) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (e.getValue() instanceof Map) {
                target.createSection(e.getKey(), (Map<?, ?>) e.getValue());
            } else if (e.getValue() != null) {
                target.set(e.getKey(), e.getValue());
            }
        }
    }

    // ================================================================ Lecture

    private static Map<String, Object> parse(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        JsonElement root = new JsonParser().parse(new StringReader(text));
        if (!root.isJsonObject()) {
            throw new IllegalStateException("objet attendu");
        }
        return castMap(convert(root));
    }

    private static Object convert(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonObject()) {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            for (Map.Entry<String, JsonElement> e : ((JsonObject) element).entrySet()) {
                Object value = convert(e.getValue());
                if (value != null) {
                    map.put(e.getKey(), value);
                }
            }
            return map;
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<Object>();
            for (JsonElement e : (JsonArray) element) {
                Object value = convert(e);
                if (value != null) {
                    list.add(value);
                }
            }
            return list;
        }
        JsonPrimitive p = (JsonPrimitive) element;
        if (p.isBoolean()) {
            return p.getAsBoolean();
        }
        if (p.isNumber()) {
            return number(p.getAsString());
        }
        return p.getAsString();
    }

    /** Entier si le nombre n'a pas de partie décimale, sinon décimal. */
    static Object number(String raw) {
        if (raw.matches("-?\\d+")) {
            long l = Long.parseLong(raw);
            return l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Object) (int) l : (Object) l;
        }
        return Double.parseDouble(raw);
    }

    /** Message de Gson ramené à une phrase en français avec la position. */
    private static String summarize(String message) {
        String m = message == null ? "" : message;
        String cause;
        if (m.contains("Unterminated object")) {
            cause = "accolade } ou virgule manquante";
        } else if (m.contains("Unterminated array")) {
            cause = "crochet ] ou virgule manquant";
        } else if (m.contains("Unterminated string")) {
            cause = "guillemet \" non fermé";
        } else if (m.contains("Expected ':'")) {
            cause = "deux-points : manquant après un nom d'option";
        } else if (m.contains("Expected name")) {
            cause = "nom d'option attendu (virgule en trop avant } ?)";
        } else if (m.contains("End of input")) {
            cause = "fin de fichier inattendue (accolade } manquante ?)";
        } else {
            cause = "syntaxe JSON invalide";
        }
        Matcher pos = POSITION.matcher(m);
        return pos.find() ? cause + " (ligne " + pos.group(1) + ", colonne " + pos.group(2) + ")" : cause;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static String readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }
}
