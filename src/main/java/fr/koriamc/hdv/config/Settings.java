package fr.koriamc.hdv.config;

import fr.koriamc.hdv.model.Currency;
import fr.koriamc.hdv.util.MaterialSet;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Réglages typés de config.json, relus à chaque rechargement.
 */
public final class Settings {

    /** Façon de lire et modifier les points boutique. */
    public enum PointsMode {
        /** Solde Azuriom : lu avec PlaceholderAPI (%azlink_money%), modifié avec /azlink money. */
        AZLINK,
        /** Variable persistante KoriaMC du joueur (%pvar_points%). */
        VARIABLE
    }

    public boolean debug;

    // Taxe
    /** Taxe sur les ventes en dollars, en % du prix, payée à la mise en vente. */
    public double taxMoney;
    /** Taxe sur les ventes en points boutique, en % du prix, retenue sur le montant reçu. */
    public double taxPoints;
    public boolean refundTaxOnCancel;
    public boolean refundTaxOnExpire;

    // Ventes
    public int maxListings;
    /** Lignes gardées dans l'historique de chaque joueur. */
    public int historySize;
    public long durationMs;
    public double minMoney;
    public double maxMoney;
    public double minPoints;
    public double maxPoints;
    public MaterialSet blacklist = new MaterialSet();
    public Currency defaultCurrency;

    // Points boutique
    public String pointsModeSetting;
    public String pointsPlaceholder;
    public String pointsAddCommand;
    public String pointsRemoveCommand;
    public String pointsVariable;
    public int pointsDecimals;

    // Menu
    public String layout;
    public boolean installLayout;
    public String inventory;
    /** Copies des objets avec l'infobulle de vente (survol du prix). */
    public String hoverInventory;
    public int perPage;
    /** Écran de vente (/hdv sell sans argument). */
    public String sellLayout;
    /** Onglet ouvert après une vente : all, money, points, mine, history, ou "precedent" (là où le joueur était). */
    public String afterSell;
    /** Écran d'achat (clic sur une vente). */
    public String buyLayout;
    /** Toasts de notification (client KoriaMC). */
    public String toastLayout;
    public int toastDurationMs;
    public String sellInventory;

    // Titres
    public int titleFadeIn;
    public int titleStay;
    public int titleFadeOut;

    private final Map<String, String> sounds = new HashMap<String, String>();

    public void load(ConfigurationSection c, Logger logger) {
        debug = c.getBoolean("debug");
        taxMoney = clampPercent(c.getDouble("taxe.dollars", 10));
        taxPoints = clampPercent(c.getDouble("taxe.points", 10));
        refundTaxOnCancel = c.getBoolean("taxe.rembourser-si-annule", false);
        refundTaxOnExpire = c.getBoolean("taxe.rembourser-a-expiration", true);

        maxListings = Math.max(1, c.getInt("ventes.max-par-joueur", 10));
        historySize = Math.max(1, Math.min(500, c.getInt("ventes.historique-max", 50)));
        durationMs = Math.max(1, c.getInt("ventes.expiration-heures", 8)) * 3600000L;
        minMoney = Math.max(0.01, c.getDouble("ventes.prix.dollars-min", 1));
        maxMoney = Math.max(minMoney, c.getDouble("ventes.prix.dollars-max", 100000000));
        minPoints = Math.max(0.01, c.getDouble("ventes.prix.points-min", 1));
        maxPoints = Math.max(minPoints, c.getDouble("ventes.prix.points-max", 1000000));
        blacklist = MaterialSet.of(c.getStringList("ventes.objets-interdits"), logger, "config.json : ventes.objets-interdits");
        Currency def = Currency.parse(c.getString("ventes.monnaie-par-defaut", "money"));
        defaultCurrency = def == null ? Currency.MONEY : def;

        pointsModeSetting = str(c, "points-boutique.mode", "AUTO").trim().toUpperCase(Locale.ROOT);
        pointsPlaceholder = str(c, "points-boutique.placeholder", "%azlink_money%").trim();
        pointsAddCommand = str(c, "points-boutique.commande-ajouter", "azlink money add {player} {amount}").trim();
        pointsRemoveCommand = str(c, "points-boutique.commande-retirer", "azlink money remove {player} {amount}").trim();
        pointsVariable = str(c, "points-boutique.variable", "points").trim();
        pointsDecimals = Math.max(0, Math.min(2, c.getInt("points-boutique.decimales", 0)));

        layout = str(c, "menu.layout", "hdv").trim().toLowerCase(Locale.ROOT);
        if (layout.isEmpty()) {
            layout = "hdv";
        }
        installLayout = c.getBoolean("menu.installer-layout", true);
        inventory = str(c, "menu.inventaire", "pvirtual:koriahdv").trim().toLowerCase(Locale.ROOT);
        sellLayout = str(c, "menu.layout-vente", "hdv_vente").trim().toLowerCase(Locale.ROOT);
        if (sellLayout.isEmpty()) {
            sellLayout = "hdv_vente";
        }
        buyLayout = str(c, "menu.layout-achat", "hdv_achat").trim().toLowerCase(Locale.ROOT);
        if (buyLayout.isEmpty()) {
            buyLayout = "hdv_achat";
        }
        toastLayout = str(c, "notifications.layout", "hdv_notification").trim().toLowerCase(Locale.ROOT);
        if (toastLayout.isEmpty()) {
            toastLayout = "hdv_notification";
        }
        toastDurationMs = Math.max(1000, Math.min(20000, c.getInt("notifications.duree-ms", 4000)));
        afterSell = str(c, "menu.apres-vente", "precedent").trim().toLowerCase(Locale.ROOT);
        sellInventory = str(c, "menu.inventaire-vente", "pvirtual:koriahdvvente:9").trim().toLowerCase(Locale.ROOT);
        hoverInventory = str(c, "menu.inventaire-infobulle", "pvirtual:koriahdvinfo").trim().toLowerCase(Locale.ROOT);
        perPage = Math.max(1, Math.min(54, c.getInt("menu.objets-max", 54)));

        titleFadeIn = c.getInt("titres.apparition", 5);
        titleStay = c.getInt("titres.duree", 40);
        titleFadeOut = c.getInt("titres.disparition", 10);
        sounds.clear();
        ConfigurationSection soundSection = c.getConfigurationSection("sons");
        if (soundSection != null) {
            for (String key : soundSection.getKeys(false)) {
                sounds.put(key, soundSection.getString(key, ""));
            }
        }
    }

    /** Mode des points boutique : AUTO choisit AZLINK si le plugin AzLink est présent. */
    public PointsMode pointsMode(boolean azlinkInstalled) {
        if (pointsModeSetting.equals("AZLINK")) {
            return PointsMode.AZLINK;
        }
        if (pointsModeSetting.equals("VARIABLE")) {
            return PointsMode.VARIABLE;
        }
        return azlinkInstalled ? PointsMode.AZLINK : PointsMode.VARIABLE;
    }

    public double tax(Currency currency) {
        return currency == Currency.POINTS ? taxPoints : taxMoney;
    }

    public int decimals(Currency currency) {
        return currency == Currency.POINTS ? pointsDecimals : 2;
    }

    public double min(Currency currency) {
        return currency == Currency.POINTS ? minPoints : minMoney;
    }

    public double max(Currency currency) {
        return currency == Currency.POINTS ? maxPoints : maxMoney;
    }

    /** Son de la section sons (chaîne vide si absent). */
    public String sound(String key) {
        String value = sounds.get(key);
        return value == null ? "" : value;
    }

    private static double clampPercent(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private static String str(ConfigurationSection c, String path, String def) {
        String value = c.getString(path);
        return value == null ? def : value;
    }
}
