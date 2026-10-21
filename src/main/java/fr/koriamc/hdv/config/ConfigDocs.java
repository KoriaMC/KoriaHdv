package fr.koriamc.hdv.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Explication de chaque option (descriptions.json, dans le jar). Le JSON n'ayant
 * pas de commentaires, ces textes sont affichés dans le menu /hdv config et
 * recopiés dans LISEZMOI.txt.
 */
public final class ConfigDocs {

    /** Aide d'une option : texte et valeurs possibles (vide si libre). */
    public static final class Doc {
        public final String text;
        public final List<String> choices;

        Doc(String text, List<String> choices) {
            this.text = text;
            this.choices = choices;
        }
    }

    private static final Doc NONE = new Doc("", Collections.<String>emptyList());

    /** fichier -> motif de chemin (avec "*") -> aide */
    private final Map<String, Map<String, Doc>> docs = new LinkedHashMap<String, Map<String, Doc>>();

    public void load(JavaPlugin plugin) {
        docs.clear();
        InputStream in = plugin.getResource("descriptions.json");
        if (in == null) {
            return;
        }
        try {
            JsonObject root = new JsonParser().parse(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> file : root.entrySet()) {
                Map<String, Doc> map = new LinkedHashMap<String, Doc>();
                for (Map.Entry<String, JsonElement> e : file.getValue().getAsJsonObject().entrySet()) {
                    map.put(e.getKey(), parse(e.getValue()));
                }
                docs.put(file.getKey(), map);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "descriptions.json illisible", e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // rien à faire
            }
        }
    }

    /** "texte" ou ["texte", "CHOIX1", "CHOIX2", ...] */
    private static Doc parse(JsonElement value) {
        if (value.isJsonArray()) {
            List<String> parts = new ArrayList<String>();
            for (JsonElement e : value.getAsJsonArray()) {
                parts.add(e.getAsString());
            }
            return parts.isEmpty() ? NONE : new Doc(parts.get(0), parts.subList(1, parts.size()));
        }
        return new Doc(value.getAsString(), Collections.<String>emptyList());
    }

    public Doc get(String file, String path) {
        Map<String, Doc> map = docs.get(file);
        if (map == null) {
            return NONE;
        }
        Doc exact = map.get(path);
        if (exact != null) {
            return exact;
        }
        for (Map.Entry<String, Doc> e : map.entrySet()) {
            if (e.getKey().contains("*") && matches(e.getKey(), path)) {
                return e.getValue();
            }
        }
        return NONE;
    }

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

    /** Écrit LISEZMOI.txt : chaque option de chaque fichier, sa valeur par défaut et son rôle. */
    public void writeReadme(JavaPlugin plugin, List<JsonFile> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("KoriaHdv - description des options (fichier régénéré à chaque démarrage)\r\n");
        sb.append("Les options se modifient dans les fichiers .json ou en jeu avec /hdv config.\r\n");
        sb.append("Couleurs : codes & (&a, &l...). Matériaux : nom 1.8.8, ID ou ID:data.\r\n");
        for (JsonFile file : files) {
            if (!docs.containsKey(file.getName())) {
                continue;
            }
            sb.append("\r\n==================== ").append(file.getName()).append(" ====================\r\n");
            if (!file.getName().equals("config.json")) {
                // quêtes, récompenses, textes et menus : seulement l'aide générale, pas chaque valeur
                for (Map.Entry<String, Doc> e : docs.get(file.getName()).entrySet()) {
                    sb.append(e.getKey()).append(" : ").append(e.getValue().text).append("\r\n");
                    if (!e.getValue().choices.isEmpty()) {
                        sb.append("    Valeurs possibles : ").append(String.join(", ", e.getValue().choices)).append("\r\n");
                    }
                }
                continue;
            }
            describe(sb, file.getName(), file.getDefaults(), "");
        }
        try {
            Files.write(new File(plugin.getDataFolder(), "LISEZMOI.txt").toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible d'écrire LISEZMOI.txt : " + e.getMessage());
        }
    }

    private void describe(StringBuilder sb, String file, Map<String, Object> map, String prefix) {
        for (Map.Entry<String, Object> e : map.entrySet()) {
            String path = prefix + e.getKey();
            Doc doc = get(file, path);
            if (e.getValue() instanceof Map) {
                if (!doc.text.isEmpty()) {
                    sb.append("\r\n[").append(path).append("] ").append(doc.text).append("\r\n");
                }
                describe(sb, file, JsonFile.castMap(e.getValue()), path + ".");
                continue;
            }
            sb.append(path).append(" = ").append(JsonWriter.write(e.getValue()).trim());
            if (!doc.text.isEmpty()) {
                sb.append("\r\n    ").append(doc.text);
            }
            if (!doc.choices.isEmpty()) {
                sb.append("\r\n    Valeurs possibles : ").append(String.join(", ", doc.choices));
            }
            sb.append("\r\n");
        }
    }
}
