package fr.koriamc.hdv.market;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.config.Settings;
import fr.koriamc.hdv.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.logging.Level;

/**
 * Points boutique. Deux modes (config.json : points-boutique.mode) :
 * AZLINK : solde du site Azuriom, lu avec PlaceholderAPI (%azlink_money%) et modifié avec les
 * commandes /azlink money add|remove (le versement marche même si le vendeur est hors ligne) ;
 * VARIABLE : variable persistante KoriaMC du joueur (%pvar_points%), modifiable seulement
 * quand il est connecté (sinon le versement attend sa prochaine connexion).
 */
public final class PointsWallet implements Wallet {

    private final KoriaHdv plugin;
    private Method setPlaceholders;
    private boolean papiChecked;

    public PointsWallet(KoriaHdv plugin) {
        this.plugin = plugin;
    }

    private Settings.PointsMode mode() {
        return plugin.settings().pointsMode(Bukkit.getPluginManager().getPlugin("AzLink") != null);
    }

    @Override
    public boolean isAvailable() {
        if (mode() == Settings.PointsMode.AZLINK) {
            return Bukkit.getPluginManager().getPlugin("AzLink") != null && papi() != null;
        }
        return plugin.gui().isAvailable();
    }

    @Override
    public double balance(Player player) {
        if (mode() == Settings.PointsMode.AZLINK) {
            Method method = papi();
            if (method == null) {
                return -1;
            }
            try {
                return parse(String.valueOf(method.invoke(null, player, plugin.settings().pointsPlaceholder)));
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Lecture des points boutique impossible", t);
                return -1;
            }
        }
        return parse(plugin.gui().getPlayerVar(player, plugin.settings().pointsVariable));
    }

    @Override
    public boolean has(Player player, double amount) {
        return balance(player) >= amount - 1e-9;
    }

    @Override
    public boolean withdraw(Player player, double amount) {
        if (mode() == Settings.PointsMode.AZLINK) {
            return command(plugin.settings().pointsRemoveCommand, player.getName(), amount);
        }
        double balance = balance(player);
        if (balance < amount - 1e-9) {
            return false;
        }
        plugin.gui().setPlayerVar(player, plugin.settings().pointsVariable, format(balance - amount));
        return true;
    }

    @Override
    public boolean deposit(OfflinePlayer player, String name, double amount) {
        if (mode() == Settings.PointsMode.AZLINK) {
            return command(plugin.settings().pointsAddCommand, name, amount);
        }
        Player online = player.getPlayer();
        if (online == null || !plugin.gui().isAvailable()) {
            return false;
        }
        double balance = Math.max(0, balance(online));
        plugin.gui().setPlayerVar(online, plugin.settings().pointsVariable, format(balance + amount));
        return true;
    }

    private boolean command(String template, String player, double amount) {
        if (template.isEmpty()) {
            return false;
        }
        String line = Text.replace(template, "player", player, "amount", format(amount));
        if (line.startsWith("/")) {
            line = line.substring(1);
        }
        try {
            return Bukkit.dispatchCommand(Bukkit.getConsoleSender(), line);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Commande de points boutique en erreur : " + line, e);
            return false;
        }
    }

    /** Montant au format des commandes (point décimal, sans séparateur). */
    public String format(double amount) {
        return BigDecimal.valueOf(amount).setScale(plugin.settings().pointsDecimals, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    private Method papi() {
        if (!papiChecked) {
            papiChecked = true;
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                setPlaceholders = api.getMethod("setPlaceholders", Player.class, String.class);
            } catch (Throwable t) {
                setPlaceholders = null;
            }
        }
        if (setPlaceholders == null && Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            papiChecked = false;
        }
        return setPlaceholders;
    }

    /** "1 250,5" / "1250.5" / "" -> nombre (0 si illisible). */
    static double parse(String raw) {
        if (raw == null) {
            return 0;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (Character.isDigit(c) || c == '.' || c == ',' || c == '-') {
                sb.append(c);
            }
        }
        String s = sb.toString();
        int dot = s.lastIndexOf('.');
        int comma = s.lastIndexOf(',');
        if (dot >= 0 && comma >= 0) {
            // "1,250.50" ou "1.250,50" : le dernier séparateur est la virgule décimale
            s = dot > comma ? s.replace(",", "") : s.replace(".", "").replace(',', '.');
        } else if (comma >= 0) {
            s = s.replace(',', '.');
        }
        Double value = Text.parseDouble(s);
        return value == null ? 0 : value;
    }
}
