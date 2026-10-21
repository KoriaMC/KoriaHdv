package fr.koriamc.hdv.config;

import fr.koriamc.hdv.util.Nms;
import fr.koriamc.hdv.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Accès aux textes de messages.json.
 * Les variables sont passées par paires : send(joueur, "cle", "player", nom).
 */
public final class Messages {

    private static final String ACTIONBAR = "[actionbar]";
    private static final String TITLE = "[title]";

    private ConfigurationSection cfg;
    private String prefix = "";
    private int titleFadeIn = 5;
    private int titleStay = 40;
    private int titleFadeOut = 10;

    public void load(ConfigurationSection cfg, Settings settings) {
        this.cfg = cfg;
        this.prefix = cfg.getString("prefix", "");
        this.titleFadeIn = settings.titleFadeIn;
        this.titleStay = settings.titleStay;
        this.titleFadeOut = settings.titleFadeOut;
    }

    public boolean has(String key) {
        return cfg.contains(key);
    }

    public ConfigurationSection config() {
        return cfg;
    }

    /** Lignes brutes (non colorées) d'un message. Une clé absente renvoie son nom. */
    public List<String> raw(String key) {
        if (cfg.isList(key)) {
            return cfg.getStringList(key);
        }
        String value = cfg.getString(key);
        if (value == null) {
            return Collections.singletonList("<" + key + ">");
        }
        return Collections.singletonList(value);
    }

    /** Remplace le préfixe et les variables d'un texte puis le colore. */
    public String format(String template, Object... kv) {
        if (template == null) {
            return "";
        }
        String s = template.replace("{prefix}", prefix);
        return Text.color(Text.replace(s, kv));
    }

    public List<String> lines(String key, Object... kv) {
        List<String> raw = raw(key);
        List<String> out = new ArrayList<String>(raw.size());
        for (String line : raw) {
            out.add(format(line, kv));
        }
        return out;
    }

    /** Message sans le préfixe, sur une seule chaîne (affichage dans un menu). */
    public String plain(String key, Object... kv) {
        List<String> out = new ArrayList<String>();
        for (String line : raw(key)) {
            out.add(format(line.replace("{prefix}", ""), kv));
        }
        return Text.join(out, "\n");
    }

    /** Message sur une seule chaîne (les lignes sont jointes par un retour à la ligne). */
    public String get(String key, Object... kv) {
        return Text.join(lines(key, kv), "\n");
    }

    /** Valeur simple sans préfixe spécial, utile pour les fragments (noms, états...). */
    public String text(String key, Object... kv) {
        String value = cfg.getString(key);
        return value == null ? "" : format(value, kv);
    }

    public void send(CommandSender to, String key, Object... kv) {
        if (to == null) {
            return;
        }
        for (String line : lines(key, kv)) {
            sendLine(to, line);
        }
    }

    public void send(Collection<? extends CommandSender> targets, String key, Object... kv) {
        List<String> lines = lines(key, kv);
        for (CommandSender to : targets) {
            for (String line : lines) {
                sendLine(to, line);
            }
        }
    }

    public void broadcast(String key, Object... kv) {
        List<String> lines = lines(key, kv);
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (String line : lines) {
                sendLine(player, line);
            }
        }
        for (String line : lines) {
            if (!line.isEmpty()) {
                Bukkit.getConsoleSender().sendMessage(stripSpecial(line));
            }
        }
    }

    /** Envoie une ligne déjà formatée en gérant [actionbar] et [title]. */
    public void sendLine(CommandSender to, String line) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if (!(to instanceof Player)) {
            to.sendMessage(stripSpecial(line));
            return;
        }
        Player player = (Player) to;
        if (line.startsWith(ACTIONBAR)) {
            Nms.actionBar(player, line.substring(ACTIONBAR.length()).trim());
        } else if (line.startsWith(TITLE)) {
            String[] parts = line.substring(TITLE.length()).trim().split(";;", 2);
            Nms.title(player, parts[0], parts.length > 1 ? parts[1] : "", titleFadeIn, titleStay, titleFadeOut);
        } else {
            player.sendMessage(line);
        }
    }

    private static String stripSpecial(String line) {
        if (line.startsWith(ACTIONBAR)) {
            return line.substring(ACTIONBAR.length()).trim();
        }
        if (line.startsWith(TITLE)) {
            return line.substring(TITLE.length()).trim().replace(";;", " - ");
        }
        return line;
    }

    // --------------------------------------------------------------- Formats

    public String duration(long millis) {
        if (millis <= 0) {
            return text("time.now");
        }
        long seconds = (millis + 999) / 1000;
        long days = seconds / 86400;
        seconds %= 86400;
        long hours = seconds / 3600;
        seconds %= 3600;
        long minutes = seconds / 60;
        seconds %= 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append(text("time.days")).append(' ');
        }
        if (hours > 0) {
            sb.append(hours).append(text("time.hours")).append(' ');
        }
        if (minutes > 0) {
            sb.append(minutes).append(text("time.minutes")).append(' ');
        }
        if (seconds > 0 && days == 0) {
            sb.append(seconds).append(text("time.seconds"));
        }
        return sb.toString().trim();
    }

    /** Durée écoulée depuis un instant ("jamais" si 0). */
    public String since(long time) {
        if (time <= 0) {
            return text("time.never");
        }
        return duration(System.currentTimeMillis() - time);
    }

    public String yesNo(boolean value) {
        return text(value ? "values.yes-text" : "values.no-text");
    }
}
