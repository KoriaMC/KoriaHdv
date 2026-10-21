package fr.koriamc.hdv.gui;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.config.ConfigDocs;
import fr.koriamc.hdv.config.JsonFile;
import fr.koriamc.hdv.config.Messages;
import fr.koriamc.hdv.util.Items;
import fr.koriamc.hdv.util.Text;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /hdv config : contenu d'une section d'un fichier JSON. L'apparence de chaque type
 * d'option et l'effet de chaque clic viennent de menus.json (editeur.types).
 * Actions propres : OUVRIR, BASCULER, AUGMENTER:n, DIMINUER:n, SAISIR,
 * CHOIX-SUIVANT, CHOIX-PRECEDENT, AJOUTER, RETIRER, REMPLACER, DEFAUT,
 * RETOUR et AJOUTER-ENTREE.
 */
final class ConfigSectionMenu extends Menu {

    private static final long CONFIRM_MS = 5000L;
    /** Suppression en attente de confirmation : joueur -> fichier:chemin et instant. */
    private static final Map<UUID, String> CONFIRM_TARGET = new HashMap<UUID, String>();
    private static final Map<UUID, Long> CONFIRM_TIME = new HashMap<UUID, Long>();

    private final JsonFile file;
    private final List<String> path;

    ConfigSectionMenu(KoriaHdv plugin, Player player, JsonFile file, List<String> path, int page) {
        super(plugin, player);
        this.file = file;
        this.path = new ArrayList<String>(path);
        this.page = page;
    }

    @Override
    protected ConfigurationSection section() {
        return plugin.menus().section("editeur");
    }

    @Override
    protected String titleTemplate() {
        return section().getString("titre-section", "");
    }

    @Override
    protected int rows() {
        return section().getInt("lignes-section", 6);
    }

    @Override
    protected Object[] vars() {
        return new Object[]{"file", file.getName(), "path", path.isEmpty() ? "/" : pathText(path),
                "section", path.isEmpty() ? file.getName() : path.get(path.size() - 1),
                "page", page + 1, "pages", pages, "player", player.getName(), "label", plugin.commands().getLabel()};
    }

    private static String pathText(List<String> p) {
        return Text.join(p, ".");
    }

    private List<String> child(String key) {
        List<String> p = new ArrayList<String>(path);
        p.add(key);
        return p;
    }

    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? JsonFile.castMap(o) : new LinkedHashMap<String, Object>();
    }

    private boolean isFreeForm() {
        return !path.isEmpty() && file.isFreeForm(pathText(path));
    }

    // ================================================================ Rendu

    @Override
    protected void render() {
        ConfigurationSection s = section();
        boolean freeForm = isFreeForm();
        Map<String, Object> user = asMap(JsonFile.get(file.getTree(), path));
        Map<String, Object> def = asMap(JsonFile.get(file.getDefaults(), path));
        List<String> keys = new ArrayList<String>(user.keySet());
        if (!freeForm) {
            for (String key : def.keySet()) {
                if (!user.containsKey(key)) {
                    keys.add(key);
                }
            }
        }
        List<Integer> slots = s.getIntegerList("cases-options");
        int perPage = Math.max(1, slots.size());
        pages = Math.max(1, (keys.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < slots.size() && page * perPage + i < keys.size(); i++) {
            String key = keys.get(page * perPage + i);
            boolean isDefault = !user.containsKey(key);
            Object value = isDefault ? def.get(key) : user.get(key);
            entry(slots.get(i), key, value, isDefault, freeForm);
        }
        Object[] vars = vars();
        List<Object> info = new ArrayList<Object>(Arrays.asList(vars));
        info.add("doc");
        info.add(wrap(plugin.configDocs().get(file.getName(), pathText(path)).text));
        setConfigured(s.getConfigurationSection("info"), info.toArray());
        setConfigured(s.getConfigurationSection("retour"), vars);
        if (page > 0) {
            setConfigured(s.getConfigurationSection("precedent"), vars);
        }
        if (page < pages - 1) {
            setConfigured(s.getConfigurationSection("suivant"), vars);
        }
        if (freeForm) {
            setConfigured(s.getConfigurationSection("ajouter"), vars);
        }
    }

    /** Type d'option : section, section-libre, oui, non, nombre, texte, choix, liste ou complexe. */
    private static String typeOf(Object value, boolean freeFormEntry, List<String> choices) {
        if (value instanceof Map) {
            return freeFormEntry ? "section-libre" : "section";
        }
        if (value instanceof Boolean) {
            return (Boolean) value ? "oui" : "non";
        }
        if (value instanceof Number) {
            return "nombre";
        }
        if (value instanceof String) {
            return choices.isEmpty() ? "texte" : "choix";
        }
        if (value instanceof List && isSimpleList((List<?>) value)) {
            return "liste";
        }
        return "complexe";
    }

    private void entry(int slot, final String key, final Object value, boolean isDefault, final boolean freeForm) {
        ConfigurationSection s = section();
        final List<String> target = child(key);
        ConfigDocs.Doc doc = plugin.configDocs().get(file.getName(), pathText(target));
        ConfigurationSection template = s.getConfigurationSection("types." + typeOf(value, freeForm, doc.choices));
        int count = value instanceof Map ? ((Map<?, ?>) value).size() : value instanceof List ? ((List<?>) value).size() : 0;
        double step = value instanceof Number ? step((Number) value) : 0;
        List<String> list = new ArrayList<String>();
        if (value instanceof List) {
            int max = s.getInt("liste-lignes-max", 8);
            List<?> values = (List<?>) value;
            for (int i = 0; i < values.size() && i < max; i++) {
                list.add(String.valueOf(values.get(i)));
            }
            if (values.size() > max) {
                list.add(s.getString("liste-suite", "..."));
            }
        }
        Object[] kv = {
                "key", key,
                "path", pathText(target),
                "file", file.getName(),
                "value", display(key, value),
                "count", count,
                "step", format(step, value),
                "big", format(step * 10, value),
                "choices", Text.join(doc.choices, ", "),
                "doc", wrap(doc.text),
                "list", list,
                "defaut", isDefault ? s.getString("texte-defaut", "") : "",
                "effacer", s.getString(freeForm ? "texte-supprimer" : "texte-reinitialiser", ""),
                "label", plugin.commands().getLabel()
        };
        set(slot, Items.fromSection(template, kv), clickHandler(template, new CustomAction() {
            @Override
            public boolean run(String type, String arg) {
                return entryAction(type, arg, target, value, freeForm);
            }
        }, kv));
    }

    private static boolean isSimpleList(List<?> list) {
        for (Object o : list) {
            if (o instanceof Map || o instanceof List) {
                return false;
            }
        }
        return true;
    }

    /** Valeur affichable (les mots de passe restent masqués). */
    private String display(String key, Object value) {
        if (key.contains("mot-de-passe") && value != null && !value.toString().isEmpty()) {
            return section().getString("mot-de-passe-masque", "******");
        }
        if (value instanceof List) {
            List<String> out = new ArrayList<String>();
            for (Object o : (List<?>) value) {
                out.add(String.valueOf(o));
            }
            return Text.join(out, ", ");
        }
        if (value instanceof Double) {
            return BigDecimal.valueOf((Double) value).stripTrailingZeros().toPlainString();
        }
        return value instanceof Map ? "" : String.valueOf(value);
    }

    /** Coupe un texte en lignes d'environ 40 caractères. */
    static List<String> wrap(String text) {
        List<String> lines = new ArrayList<String>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() > 0 && line.length() + word.length() > 40) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    // ========================================================== Modification

    @Override
    protected boolean menuAction(String type, String value) {
        if (type.equals("RETOUR")) {
            if (path.isEmpty()) {
                new ConfigFilesMenu(plugin, player).open();
            } else {
                new ConfigSectionMenu(plugin, player, file, path.subList(0, path.size() - 1), 0).open();
            }
            return true;
        }
        if (type.equals("AJOUTER-ENTREE")) {
            addEntry();
            return true;
        }
        return false;
    }

    private boolean entryAction(String type, String arg, List<String> target, Object value, boolean freeForm) {
        switch (type) {
            case "OUVRIR":
                if (value instanceof Map) {
                    new ConfigSectionMenu(plugin, player, file, target, 0).open();
                }
                return true;
            case "DEFAUT":
                resetOrRemove(target, freeForm || value instanceof Map);
                return true;
            case "BASCULER":
                if (value instanceof Boolean) {
                    save(target, !(Boolean) value);
                }
                return true;
            case "AUGMENTER":
            case "DIMINUER":
                if (value instanceof Number) {
                    Double factor = Text.parseDouble(arg.isEmpty() ? "1" : arg);
                    double delta = step((Number) value) * (factor == null ? 1 : factor) * (type.equals("DIMINUER") ? -1 : 1);
                    save(target, add((Number) value, delta));
                }
                return true;
            case "SAISIR":
                promptValue(target, value);
                return true;
            case "CHOIX-SUIVANT":
            case "CHOIX-PRECEDENT":
                List<String> choices = plugin.configDocs().get(file.getName(), pathText(target)).choices;
                if (value instanceof String && !choices.isEmpty()) {
                    int index = indexOfIgnoreCase(choices, (String) value);
                    int next = type.equals("CHOIX-PRECEDENT") ? index - 1 : index + 1;
                    save(target, choices.get(((next % choices.size()) + choices.size()) % choices.size()));
                }
                return true;
            case "AJOUTER":
            case "REMPLACER":
                if (value instanceof List) {
                    promptList(target, new ArrayList<Object>((List<?>) value), type.equals("REMPLACER"));
                }
                return true;
            case "RETIRER":
                if (value instanceof List) {
                    List<Object> list = new ArrayList<Object>((List<?>) value);
                    if (!list.isEmpty()) {
                        list.remove(list.size() - 1);
                    }
                    save(target, list);
                }
                return true;
            default:
                return false;
        }
    }

    private static int indexOfIgnoreCase(List<String> choices, String value) {
        for (int i = 0; i < choices.size(); i++) {
            if (choices.get(i).equalsIgnoreCase(value)) {
                return i;
            }
        }
        return -1;
    }

    /** Pas d'un clic : environ un dixième de l'ordre de grandeur de la valeur. */
    private static double step(Number value) {
        double v = Math.abs(value.doubleValue());
        boolean decimal = value instanceof Double || value instanceof Float;
        if (v == 0) {
            return decimal ? 0.1 : 1;
        }
        double step = Math.pow(10, Math.floor(Math.log10(v)) - 1);
        return decimal ? Math.max(0.1, step) : Math.max(1, step);
    }

    private static String format(double step, Object value) {
        return value instanceof Double ? BigDecimal.valueOf(step).stripTrailingZeros().toPlainString()
                : String.valueOf((long) step);
    }

    private static Object add(Number value, double delta) {
        if (value instanceof Double || value instanceof Float) {
            return BigDecimal.valueOf(value.doubleValue() + delta).setScale(6, RoundingMode.HALF_UP).doubleValue();
        }
        long result = value.longValue() + Math.round(delta);
        return result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE ? (Object) (int) result : (Object) result;
    }

    /** Texte ou nombre tapé dans le chat. */
    private void promptValue(final List<String> target, final Object current) {
        if (current instanceof Map || current instanceof List || current instanceof Boolean) {
            return;
        }
        Messages msg = plugin.msg();
        msg.send(player, "config-editor.prompt", "path", pathText(target), "cancel", msg.text("config-editor.cancel-word"),
                "empty", msg.text("config-editor.empty-word"));
        player.sendMessage(msg.text("config-editor.prompt-current") + display(target.get(target.size() - 1), current));
        plugin.menus().prompt().ask(player, new ChatPrompt.Callback() {
            @Override
            public void answer(String text) {
                if (current instanceof Number) {
                    Double parsed = Text.parseDouble(text.replace(" ", ""));
                    if (parsed == null) {
                        plugin.msg().send(player, "config-editor.invalid-number", "input", text);
                        open();
                        return;
                    }
                    save(target, current instanceof Double ? (Object) parsed : (Object) (int) Math.round(parsed));
                } else {
                    save(target, text.equalsIgnoreCase(plugin.msg().text("config-editor.empty-word")) ? "" : text);
                }
            }
        }, reopen());
    }

    /** Ajout d'une valeur à une liste, ou remplacement de toute la liste (valeurs séparées par des virgules). */
    private void promptList(final List<String> target, final List<Object> list, final boolean replace) {
        Messages msg = plugin.msg();
        msg.send(player, replace ? "config-editor.prompt-list-replace" : "config-editor.prompt-list-add",
                "path", pathText(target), "cancel", msg.text("config-editor.cancel-word"));
        final boolean numbers = !list.isEmpty() && list.get(0) instanceof Number;
        plugin.menus().prompt().ask(player, new ChatPrompt.Callback() {
            @Override
            public void answer(String text) {
                List<Object> result = replace ? new ArrayList<Object>() : list;
                String[] parts = replace ? text.split(",") : new String[]{text};
                for (String part : parts) {
                    String p = part.trim();
                    if (p.isEmpty()) {
                        continue;
                    }
                    if (numbers) {
                        Integer n = Text.parseInt(p);
                        if (n == null) {
                            plugin.msg().send(player, "config-editor.invalid-number", "input", p);
                            open();
                            return;
                        }
                        result.add(n);
                    } else {
                        result.add(p);
                    }
                }
                save(target, result);
            }
        }, reopen());
    }

    private void addEntry() {
        final Map<String, Object> current = asMap(JsonFile.get(file.getTree(), path));
        if (current.isEmpty()) {
            plugin.msg().send(player, "config-editor.empty-section");
            return;
        }
        final Object template = new ArrayList<Object>(current.values()).get(current.size() - 1);
        Messages msg = plugin.msg();
        msg.send(player, "config-editor.prompt-add", "path", pathText(path), "cancel", msg.text("config-editor.cancel-word"));
        plugin.menus().prompt().ask(player, new ChatPrompt.Callback() {
            @Override
            public void answer(String text) {
                Map<String, Object> now = asMap(JsonFile.get(file.getTree(), path));
                if (!text.matches("[A-Za-z0-9_:-]+")) {
                    plugin.msg().send(player, "config-editor.invalid-key");
                    open();
                    return;
                }
                if (now.containsKey(text)) {
                    plugin.msg().send(player, "config-editor.exists", "key", text);
                    open();
                    return;
                }
                List<String> target = child(text);
                file.set(target, copy(template));
                persist("config-editor.added", target, null);
            }
        }, reopen());
    }

    private Runnable reopen() {
        return new Runnable() {
            @Override
            public void run() {
                open();
            }
        };
    }

    private static Object copy(Object o) {
        if (o instanceof Map) {
            Map<String, Object> out = new LinkedHashMap<String, Object>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                out.put(String.valueOf(e.getKey()), copy(e.getValue()));
            }
            return out;
        }
        if (o instanceof List) {
            List<Object> out = new ArrayList<Object>();
            for (Object v : (List<?>) o) {
                out.add(copy(v));
            }
            return out;
        }
        return o;
    }

    /** Suppression d'une entrée libre ou retour à la valeur du jar (confirmation pour une section). */
    private void resetOrRemove(List<String> target, boolean needsConfirm) {
        String token = file.getName() + ":" + pathText(target);
        UUID uuid = player.getUniqueId();
        Long time = CONFIRM_TIME.get(uuid);
        if (needsConfirm && (!token.equals(CONFIRM_TARGET.get(uuid)) || time == null
                || System.currentTimeMillis() - time > CONFIRM_MS)) {
            CONFIRM_TARGET.put(uuid, token);
            CONFIRM_TIME.put(uuid, System.currentTimeMillis());
            plugin.msg().send(player, "config-editor.confirm-remove", "path", pathText(target));
            return;
        }
        CONFIRM_TARGET.remove(uuid);
        CONFIRM_TIME.remove(uuid);
        boolean freeForm = isFreeForm();
        file.remove(target);
        persist(freeForm ? "config-editor.removed" : "config-editor.reset", target, null);
    }

    private void save(List<String> target, Object value) {
        file.set(target, value);
        persist("config-editor.saved", target, display(target.get(target.size() - 1), value));
    }

    /** Enregistre le fichier, recharge la configuration puis rouvre le menu. */
    private void persist(String messageKey, List<String> target, String value) {
        Messages msg = plugin.msg();
        try {
            file.save();
        } catch (IOException e) {
            msg.send(player, "config-editor.save-error", "file", file.getName(), "error", e.getMessage());
            return;
        }
        List<String> problems = plugin.reload();
        String targetText = pathText(target);
        plugin.msg().send(player, messageKey, "file", file.getName(), "path", targetText, "value", value == null ? "" : value);
        if (file.getName().equals("config.json") && (targetText.startsWith("stockage") || targetText.startsWith("commande.nom")
                || targetText.startsWith("commande.alias"))) {
            plugin.msg().send(player, "config-editor.restart");
        }
        if (!problems.isEmpty()) {
            plugin.msg().send(player, "general.reload-problems", "count", problems.size());
            for (String problem : problems) {
                plugin.msg().send(player, "general.reload-problem", "problem", problem);
            }
        }
        new ConfigSectionMenu(plugin, player, file, path, page).open();
    }
}
