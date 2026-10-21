package fr.koriamc.hdv.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.config.Settings;
import fr.koriamc.hdv.gui.HdvToast;
import fr.koriamc.hdv.model.Claim;
import fr.koriamc.hdv.model.Currency;
import fr.koriamc.hdv.model.HistoryEntry;
import fr.koriamc.hdv.model.Listing;
import fr.koriamc.hdv.model.MarketData;
import fr.koriamc.hdv.util.ItemCodec;
import fr.koriamc.hdv.util.Sounds;
import fr.koriamc.hdv.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Hôtel des ventes : mise en vente, achat, retrait, expiration et récupération des objets.
 *
 * Taxe (config.json : taxe) : en dollars, le vendeur la paie au moment de la mise en vente ;
 * en points boutique, elle est retenue sur le montant qu'il reçoit à la vente.
 * Tout est enregistré dans donnees.json, et chaque opération dans historique.log.
 */
public final class MarketManager {

    /** Donne le droit de retirer la vente d'un autre joueur (/hdv cancel). */
    public static final String ADMIN_CANCEL = "koriahdv.admin.cancel";
    /** koriahdv.limit.&lt;n&gt; : nombre de ventes simultanées (la plus haute l'emporte). */
    public static final String LIMIT_PREFIX = "koriahdv.limit.";

    private final KoriaHdv plugin;
    private final File file;
    private final File history;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Wallet money;
    private final Wallet points;
    private MarketData data = new MarketData();
    private boolean readOnly;

    public MarketManager(KoriaHdv plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "donnees.json");
        this.history = new File(plugin.getDataFolder(), "historique.log");
        this.money = new MoneyWallet(plugin);
        this.points = new PointsWallet(plugin);
    }

    // ============================================================= Fichier

    public void load() {
        readOnly = false;
        data = new MarketData();
        if (file.exists()) {
            try {
                MarketData read = gson.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8),
                        MarketData.class);
                if (read != null) {
                    data = read;
                }
            } catch (Exception e) {
                readOnly = true;
                plugin.getLogger().log(Level.SEVERE, "donnees.json est illisible : l'hôtel des ventes n'enregistre plus rien"
                        + " tant que le fichier n'est pas corrigé (puis /hdv reload).", e);
            }
        }
        data.ensureInit();
        int broken = 0;
        for (Listing l : data.listings) {
            if (l.getItem() == null) {
                broken++;
                plugin.getLogger().warning("Vente n°" + l.getId() + " de " + l.getSellerName() + " : objet illisible.");
            }
        }
        if (broken > 0) {
            plugin.getLogger().warning(broken + " vente(s) avec un objet illisible (objet d'un mod retiré ?).");
        }
    }

    /** Le fichier était-il illisible au dernier chargement ? */
    public boolean isReadOnly() {
        return readOnly;
    }

    public void save() {
        if (readOnly) {
            return;
        }
        try {
            File folder = file.getParentFile();
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("dossier " + folder.getPath() + " impossible à créer");
            }
            File temp = new File(folder, "donnees.json.tmp");
            Files.write(temp.toPath(), gson.toJson(data).getBytes(StandardCharsets.UTF_8));
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Impossible d'enregistrer donnees.json", e);
        }
    }

    private void log(String line) {
        Writer out = null;
        try {
            out = new OutputStreamWriter(new FileOutputStream(history, true), StandardCharsets.UTF_8);
            out.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.FRANCE).format(new Date()) + " " + line + "\r\n");
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "historique.log non écrit", e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // rien à faire
                }
            }
        }
    }

    // ============================================================= Lecture

    public Wallet wallet(Currency currency) {
        return currency == Currency.POINTS ? points : money;
    }

    /** Ventes en cours (non expirées), les plus récentes d'abord ; currency null = toutes. */
    public List<Listing> listings(Currency currency) {
        long now = System.currentTimeMillis();
        List<Listing> list = new ArrayList<Listing>();
        for (Listing l : data.listings) {
            if (!l.isExpired(now) && (currency == null || l.accepts(currency)) && l.getItem() != null) {
                list.add(l);
            }
        }
        Collections.sort(list, new Comparator<Listing>() {
            @Override
            public int compare(Listing a, Listing b) {
                return Long.compare(b.getCreatedAt(), a.getCreatedAt());
            }
        });
        return list;
    }

    /** Ventes en cours d'un joueur. */
    public List<Listing> listingsOf(UUID seller) {
        List<Listing> list = new ArrayList<Listing>();
        for (Listing l : listings(null)) {
            if (seller.equals(l.getSeller())) {
                list.add(l);
            }
        }
        return list;
    }

    public Listing get(int id) {
        for (Listing l : data.listings) {
            if (l.getId() == id) {
                return l;
            }
        }
        return null;
    }

    /** Achats et ventes du joueur, les plus récents d'abord. */
    public List<HistoryEntry> history(UUID uuid) {
        List<HistoryEntry> list = data.history.get(uuid.toString());
        return list == null ? Collections.<HistoryEntry>emptyList() : list;
    }

    private void addHistory(UUID uuid, HistoryEntry entry) {
        String key = uuid.toString();
        List<HistoryEntry> list = data.history.get(key);
        if (list == null) {
            list = new ArrayList<HistoryEntry>();
            data.history.put(key, list);
        }
        list.add(0, entry);
        int max = plugin.settings().historySize;
        while (list.size() > max) {
            list.remove(list.size() - 1);
        }
    }

    public List<Claim> claims(UUID uuid) {
        List<Claim> list = data.claims.get(uuid.toString());
        return list == null ? Collections.<Claim>emptyList() : list;
    }

    /** Ventes simultanées autorisées : koriahdv.limit.&lt;n&gt; le plus haut, sinon ventes.max-par-joueur. */
    public int limit(Player player) {
        int best = -1;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String node = info.getPermission().toLowerCase(Locale.ROOT);
            if (info.getValue() && node.startsWith(LIMIT_PREFIX)) {
                Integer n = Text.parseInt(node.substring(LIMIT_PREFIX.length()));
                if (n != null && n > best) {
                    best = n;
                }
            }
        }
        return best >= 0 ? best : plugin.settings().maxListings;
    }

    // ============================================================= Montants

    /** Arrondi au nombre de décimales de la monnaie. */
    public double round(double amount, Currency currency) {
        return BigDecimal.valueOf(amount).setScale(plugin.settings().decimals(currency), RoundingMode.HALF_UP).doubleValue();
    }

    /** Taxe d'une vente à ce prix. */
    public double tax(double price, Currency currency) {
        return round(price * plugin.settings().tax(currency) / 100.0, currency);
    }

    /** Montant affiché avec la monnaie ("1 250 $", "40 pts"). */
    public String format(double amount, Currency currency) {
        String number = Text.number(amount, plugin.settings().decimals(currency));
        return plugin.msg().text(currency == Currency.POINTS ? "values.points-format" : "values.money-format", "montant", number);
    }

    /** Montant court pour les infobulles : "2,00K$", "1,50M$", "40 PB". */
    public String compact(double amount, Currency currency) {
        String[] suffixes = {"", "K", "M", "B"};
        double value = amount;
        int i = 0;
        while (Math.abs(value) >= 1000 && i < suffixes.length - 1) {
            value /= 1000;
            i++;
        }
        String number = i == 0 ? Text.number(value, plugin.settings().decimals(currency))
                : String.format(Locale.FRANCE, "%.2f", value) + suffixes[i];
        return plugin.msg().text(currency == Currency.POINTS ? "values.points-compact" : "values.money-compact", "montant", number);
    }

    /** Prix d'une vente : "1 250 $", "40 pts" ou "1 250 $ ou 40 pts". */
    public String priceText(Listing l) {
        if (l.isDual()) {
            return plugin.msg().text("values.price-or", "dollars", format(l.getPrice(Currency.MONEY), Currency.MONEY),
                    "points", format(l.getPrice(Currency.POINTS), Currency.POINTS));
        }
        Currency c = l.getMainCurrency();
        return format(l.getPrice(c), c);
    }

    /** "12,5k" / "2m" / "1500" -> nombre, ou null. */
    public static Double parsePrice(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        double factor = 1;
        if (s.endsWith("k")) {
            factor = 1000;
            s = s.substring(0, s.length() - 1);
        } else if (s.endsWith("m")) {
            factor = 1000000;
            s = s.substring(0, s.length() - 1);
        }
        Double value = Text.parseDouble(s);
        return value == null || value <= 0 ? null : value * factor;
    }

    // =========================================================== Opérations

    /**
     * Met en vente l'objet tenu en main (quantité : tout le paquet si amount &lt;= 0).
     *
     * @return la vente créée, ou null (message déjà envoyé)
     */
    public Listing sell(Player player, double rawPrice, Currency currency, int amount) {
        return sell(player, currency == Currency.MONEY ? rawPrice : 0, currency == Currency.POINTS ? rawPrice : 0, amount);
    }

    /**
     * Met en vente l'objet tenu en main, payable en dollars et/ou en points (prix &lt;= 0 = monnaie refusée).
     *
     * @return la vente créée, ou null (message déjà envoyé)
     */
    @SuppressWarnings("deprecation")
    public Listing sell(Player player, double rawMoney, double rawPoints, int amount) {
        Settings s = plugin.settings();
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getTypeId() == 0 || hand.getAmount() <= 0) {
            refuse(player, "sell.empty-hand");
            return null;
        }
        if (s.blacklist.contains(hand)) {
            refuse(player, "sell.forbidden");
            return null;
        }
        int count = amount <= 0 ? hand.getAmount() : amount;
        if (count > hand.getAmount()) {
            refuse(player, "sell.not-enough", "quantite", hand.getAmount());
            return null;
        }
        double moneyPrice = rawMoney > 0 ? round(rawMoney, Currency.MONEY) : 0;
        double pointsPrice = rawPoints > 0 ? round(rawPoints, Currency.POINTS) : 0;
        if (moneyPrice <= 0 && pointsPrice <= 0) {
            refuse(player, "sell.no-price");
            return null;
        }
        for (Currency currency : Currency.values()) {
            double price = currency == Currency.POINTS ? pointsPrice : moneyPrice;
            if (price <= 0) {
                continue;
            }
            if (price < s.min(currency) || price > s.max(currency)) {
                refuse(player, "sell.price-range", "min", format(s.min(currency), currency), "max", format(s.max(currency), currency));
                return null;
            }
            if (!wallet(currency).isAvailable()) {
                refuse(player, "market.currency-unavailable", "monnaie", plugin.msg().text("values.currency-" + currency.argument()));
                return null;
            }
        }
        int limit = limit(player);
        if (listingsOf(player.getUniqueId()).size() >= limit) {
            refuse(player, "sell.limit", "max", limit);
            return null;
        }
        if (readOnly) {
            refuse(player, "market.read-only");
            return null;
        }
        // dollars : la taxe est payée maintenant (points : retenue à la vente)
        Wallet wallet = wallet(Currency.MONEY);
        double taxPaid = moneyPrice > 0 ? tax(moneyPrice, Currency.MONEY) : 0;
        if (taxPaid > 0 && !wallet.has(player, taxPaid)) {
            refuse(player, "sell.tax-too-poor", "taxe", format(taxPaid, Currency.MONEY));
            return null;
        }
        ItemStack sold = hand.clone();
        sold.setAmount(count);
        // l'objet est enregistré avant tout paiement : s'il ne peut pas l'être, rien n'est pris au joueur
        Listing listing;
        try {
            listing = new Listing(data.takeId(), player.getUniqueId(), player.getName(), sold, moneyPrice, pointsPrice, taxPaid,
                    s.durationMs);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Objet de " + player.getName() + " impossible à mettre en vente", e);
            refuse(player, "market.broken-item");
            return null;
        }
        if (taxPaid > 0 && !wallet.withdraw(player, taxPaid)) {
            refuse(player, "market.payment-failed");
            return null;
        }
        if (count >= hand.getAmount()) {
            player.setItemInHand(null);
        } else {
            hand.setAmount(hand.getAmount() - count);
            player.setItemInHand(hand);
        }
        data.listings.add(listing);
        save();
        log("VENTE #" + listing.getId() + " " + player.getName() + " " + ItemCodec.id(sold) + " x" + count + " "
                + priceText(listing) + " (taxe payée " + format(taxPaid, Currency.MONEY) + ")");

        String objet = ItemCodec.name(sold);
        if (toasts().canShow(player)) {
            String taxe = taxPaid > 0 ? plugin.msg().text("toast.mise-en-vente.taxe", "taxe", format(taxPaid, Currency.MONEY)) : "";
            event(player, HdvToast.Type.INFO, ItemCodec.id(sold), "mise-en-vente", null, "objet", objet, "quantite", count,
                    "prix", priceText(listing), "taxe", taxe);
        } else {
            sellChat(player, listing, objet, count, moneyPrice, pointsPrice, taxPaid);
        }
        Sounds.play(player, s.sound("mise-en-vente"));
        return listing;
    }

    private void sellChat(Player player, Listing listing, String objet, int count, double moneyPrice, double pointsPrice,
                          double taxPaid) {
        Settings s = plugin.settings();
        plugin.msg().send(player, "sell.listed", "objet", objet, "quantite", count, "prix", priceText(listing));
        if (moneyPrice > 0) {
            plugin.msg().send(player, "sell.tax-money", "taxe", format(taxPaid, Currency.MONEY), "pourcent", Text.number(s.taxMoney, 2));
        }
        if (pointsPrice > 0) {
            double tax = tax(pointsPrice, Currency.POINTS);
            plugin.msg().send(player, "sell.tax-points", "taxe", format(tax, Currency.POINTS),
                    "net", format(pointsPrice - tax, Currency.POINTS), "pourcent", Text.number(s.taxPoints, 2));
        }
    }

    /**
     * Achète une vente dans une monnaie (null : la seule acceptée par la vente).
     *
     * @return true si l'achat est fait
     */
    public boolean buy(Player buyer, int id, Currency chosen) {
        Listing l = get(id);
        long now = System.currentTimeMillis();
        if (l == null || l.isExpired(now)) {
            refuse(buyer, "buy.gone");
            return false;
        }
        if (buyer.getUniqueId().equals(l.getSeller())) {
            refuse(buyer, "buy.own");
            return false;
        }
        ItemStack item = l.getItem();
        if (item == null) {
            refuse(buyer, "market.broken-item");
            return false;
        }
        if (!fits(buyer.getInventory(), item)) {
            refuse(buyer, "buy.no-space");
            return false;
        }
        if (chosen == null && l.isDual()) {
            refuse(buyer, "buy.choose");
            return false;
        }
        Currency currency = chosen == null ? l.getMainCurrency() : chosen;
        if (!l.accepts(currency)) {
            refuse(buyer, "buy.wrong-currency", "monnaie", plugin.msg().text("values.currency-" + currency.argument()));
            return false;
        }
        Wallet wallet = wallet(currency);
        if (!wallet.isAvailable()) {
            refuse(buyer, "market.currency-unavailable", "monnaie", plugin.msg().text("values.currency-" + currency.argument()));
            return false;
        }
        if (readOnly) {
            refuse(buyer, "market.read-only");
            return false;
        }
        double price = l.getPrice(currency);
        if (!wallet.has(buyer, price)) {
            refuse(buyer, "buy.too-poor", "prix", format(price, currency));
            return false;
        }
        // la vente est retirée avant le paiement : personne d'autre ne peut l'acheter en même temps
        data.listings.remove(l);
        if (!wallet.withdraw(buyer, price)) {
            data.listings.add(l);
            refuse(buyer, "market.payment-failed");
            return false;
        }
        for (ItemStack rest : buyer.getInventory().addItem(item).values()) {
            buyer.getWorld().dropItemNaturally(buyer.getLocation(), rest);
        }

        // vendeur : prix complet en dollars (taxe déjà payée), prix moins la taxe en points
        double tax = currency == Currency.POINTS ? tax(price, currency) : 0;
        double net = price - tax;
        UUID sellerId = l.getSeller();
        OfflinePlayer seller = sellerId == null ? null : Bukkit.getOfflinePlayer(sellerId);
        boolean paid = seller != null && net > 0 && wallet.deposit(seller, l.getSellerName(), net);
        if (!paid && seller != null && net > 0) {
            if (currency == Currency.POINTS) {
                Double pending = data.pendingPoints.get(sellerId.toString());
                data.pendingPoints.put(sellerId.toString(), (pending == null ? 0 : pending) + net);
            } else {
                plugin.getLogger().severe("Vente n°" + l.getId() + " : " + format(net, currency) + " n'ont pas pu être versés à "
                        + l.getSellerName() + " (économie indisponible).");
            }
        }
        String objet = ItemCodec.name(item);
        event(buyer, HdvToast.Type.ACHAT, ItemCodec.id(item), "achat", "buy.done", "objet", objet, "quantite", item.getAmount(),
                "prix", format(price, currency), "vendeur", l.getSellerName());
        Sounds.play(buyer, plugin.settings().sound("achat"));
        Object[] kv = {"acheteur", buyer.getName(), "objet", objet, "quantite", item.getAmount(), "prix", format(price, currency),
                "net", format(net, currency), "taxe", format(tax, currency)};
        String key = currency == Currency.POINTS ? "sold.notice-points" : "sold.notice-money";
        Player online = seller == null ? null : seller.getPlayer();
        if (online != null) {
            event(online, HdvToast.Type.VENTE, ItemCodec.id(item), "vente", key, kv);
            Sounds.play(online, plugin.settings().sound("vendu"));
        } else if (sellerId != null) {
            notice(sellerId, plugin.msg().get(key, kv));
        }
        addHistory(buyer.getUniqueId(), new HistoryEntry(data.takeId(), HistoryEntry.Type.PURCHASE, l.getEncodedItem(), currency,
                price, price, l.getSellerName()));
        if (sellerId != null) {
            addHistory(sellerId, new HistoryEntry(data.takeId(), HistoryEntry.Type.SALE, l.getEncodedItem(), currency, price, net,
                    buyer.getName()));
        }
        save();
        log("ACHAT #" + l.getId() + " " + buyer.getName() + " <- " + l.getSellerName() + " " + ItemCodec.id(item) + " x"
                + item.getAmount() + " " + format(price, currency) + " (vendeur reçoit " + format(net, currency)
                + (paid ? "" : ", en attente") + ")");
        return true;
    }

    /**
     * Retire une vente : l'objet part dans les objets à récupérer du vendeur.
     *
     * @param admin retrait par un admin (vente d'un autre joueur)
     */
    public boolean cancel(Player player, int id) {
        Listing l = get(id);
        if (l == null) {
            refuse(player, "cancel.not-found", "id", id);
            return false;
        }
        boolean own = player.getUniqueId().equals(l.getSeller());
        if (!own && !player.hasPermission(ADMIN_CANCEL)) {
            refuse(player, "cancel.not-yours");
            return false;
        }
        data.listings.remove(l);
        addClaim(l, own ? Claim.Reason.CANCELLED : Claim.Reason.REMOVED);
        if (plugin.settings().refundTaxOnCancel) {
            refundTax(l);
        }
        save();
        log((own ? "RETRAIT #" : "RETRAIT-ADMIN #") + l.getId() + " par " + player.getName());
        ItemStack item = l.getItem();
        String objet = item == null ? "?" : ItemCodec.name(item);
        String icon = item == null ? "" : ItemCodec.id(item);
        if (own) {
            event(player, HdvToast.Type.INFO, icon, "retrait", "cancel.done", "objet", objet, "label", plugin.commands().getLabel());
        } else {
            plugin.msg().send(player, "cancel.done-admin", "objet", objet, "vendeur", l.getSellerName(),
                    "label", plugin.commands().getLabel());
        }
        if (!own) {
            Player seller = l.getSeller() == null ? null : Bukkit.getPlayer(l.getSeller());
            if (seller != null) {
                event(seller, HdvToast.Type.ERREUR, icon, "retrait-admin", "cancel.removed-by-admin", "objet", objet,
                        "label", plugin.commands().getLabel());
            }
        }
        return true;
    }

    /** Récupère un objet (id) ou tous (id &lt; 0). @return nombre d'objets récupérés */
    public int claim(Player player, int id) {
        List<Claim> list = data.claims.get(player.getUniqueId().toString());
        if (list == null || list.isEmpty()) {
            refuse(player, "claim.nothing");
            return 0;
        }
        int taken = 0;
        String icon = "";
        boolean full = false;
        for (Iterator<Claim> it = list.iterator(); it.hasNext(); ) {
            Claim c = it.next();
            if (id >= 0 && c.getId() != id) {
                continue;
            }
            ItemStack item = c.getItem();
            if (item == null) {
                continue;
            }
            if (!fits(player.getInventory(), item)) {
                full = true;
                break;
            }
            player.getInventory().addItem(item);
            it.remove();
            if (taken == 0) {
                icon = ItemCodec.id(item);
            }
            taken++;
        }
        if (list.isEmpty()) {
            data.claims.remove(player.getUniqueId().toString());
        }
        if (taken > 0) {
            save();
            log("RECUPERATION " + player.getName() + " : " + taken + " objet(s)");
            event(player, HdvToast.Type.VENTE, icon, "recuperation", "claim.done", "nombre", taken);
            Sounds.play(player, plugin.settings().sound("recuperation"));
        } else if (!full && id >= 0) {
            refuse(player, "claim.not-found", "id", id);
        }
        if (full) {
            refuse(player, "claim.no-space");
        }
        return taken;
    }

    /** Rend au vendeur la taxe en dollars payée à la mise en vente (vente retirée ou expirée). */
    private void refundTax(Listing l) {
        double tax = l.getTaxPaid();
        if (tax <= 0 || l.getSeller() == null) {
            return;
        }
        if (!money.deposit(Bukkit.getOfflinePlayer(l.getSeller()), l.getSellerName(), tax)) {
            plugin.getLogger().severe("Vente n°" + l.getId() + " : taxe de " + format(tax, Currency.MONEY) + " non remboursée à "
                    + l.getSellerName() + " (économie indisponible).");
            return;
        }
        log("REMBOURSEMENT-TAXE #" + l.getId() + " " + l.getSellerName() + " " + format(tax, Currency.MONEY));
        Player seller = Bukkit.getPlayer(l.getSeller());
        if (seller != null) {
            event(seller, HdvToast.Type.VENTE, "minecraft:gold_ingot", "taxe-remboursee", "cancel.tax-refunded",
                    "taxe", format(tax, Currency.MONEY));
        }
    }

    // ============================================================ Tâches

    /** Ventes expirées : l'objet part dans les objets à récupérer du vendeur (toutes les minutes). */
    public void expireListings() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Iterator<Listing> it = data.listings.iterator(); it.hasNext(); ) {
            Listing l = it.next();
            if (!l.isExpired(now)) {
                continue;
            }
            it.remove();
            addClaim(l, Claim.Reason.EXPIRED);
            if (plugin.settings().refundTaxOnExpire) {
                refundTax(l);
            }
            changed = true;
            ItemStack item = l.getItem();
            String objet = item == null ? "?" : ItemCodec.name(item);
            Player seller = l.getSeller() == null ? null : Bukkit.getPlayer(l.getSeller());
            if (seller != null) {
                event(seller, HdvToast.Type.INFO, item == null ? "" : ItemCodec.id(item), "expiration", "expired.notice",
                        "objet", objet, "label", plugin.commands().getLabel());
            } else if (l.getSeller() != null) {
                notice(l.getSeller(), plugin.msg().get("expired.notice", "objet", objet, "label", plugin.commands().getLabel()));
            }
            log("EXPIRATION #" + l.getId() + " " + l.getSellerName());
        }
        if (changed) {
            save();
        }
    }

    /** Connexion : points en attente, messages des ventes faites hors ligne, objets à récupérer. */
    public void onJoin(Player player) {
        String key = player.getUniqueId().toString();
        boolean changed = false;
        Double pending = data.pendingPoints.get(key);
        if (pending != null && pending > 0 && points.deposit(player, player.getName(), pending)) {
            data.pendingPoints.remove(key);
            changed = true;
            event(player, HdvToast.Type.VENTE, "minecraft:emerald", "gains", "sold.pending-paid", "net", format(pending, Currency.POINTS));
            log("VERSEMENT " + player.getName() + " " + format(pending, Currency.POINTS) + " (gains en attente)");
        }
        List<String> notices = data.notices.remove(key);
        if (notices != null) {
            changed = true;
            if (toasts().canShow(player)) {
                event(player, HdvToast.Type.VENTE, "minecraft:gold_ingot", "absence", null, "nombre", notices.size());
            } else {
                plugin.msg().send(player, "sold.offline-header", "nombre", notices.size());
                for (String line : notices) {
                    plugin.msg().sendLine(player, line);
                }
            }
        }
        int claims = claims(player.getUniqueId()).size();
        if (claims > 0) {
            event(player, HdvToast.Type.INFO, "minecraft:chest", "rappel", "claim.reminder", "nombre", claims,
                    "label", plugin.commands().getLabel());
        }
        if (changed) {
            save();
        }
    }

    // ============================================================ Outils

    private void addClaim(Listing l, Claim.Reason reason) {
        if (l.getSeller() == null) {
            return;
        }
        String key = l.getSeller().toString();
        List<Claim> list = data.claims.get(key);
        if (list == null) {
            list = new ArrayList<Claim>();
            data.claims.put(key, list);
        }
        list.add(new Claim(data.takeId(), l.getEncodedItem(), reason));
    }

    private void notice(UUID uuid, String line) {
        List<String> list = data.notices.get(uuid.toString());
        if (list == null) {
            list = new ArrayList<String>();
            data.notices.put(uuid.toString(), list);
        }
        if (list.size() < 50) {
            list.add(line);
        }
    }

    /** L'objet entre-t-il entièrement dans l'inventaire (sac et barre) ? */
    @SuppressWarnings("deprecation")
    public static boolean fits(PlayerInventory inventory, ItemStack item) {
        int left = item.getAmount();
        int max = Math.max(1, item.getMaxStackSize());
        for (int slot = 0; slot < 36 && left > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current == null || current.getTypeId() == 0) {
                left -= max;
            } else if (current.isSimilar(item)) {
                left -= Math.max(0, max - current.getAmount());
            }
        }
        return left <= 0;
    }

    /** Dernier refus envoyé à chaque joueur (affiché dans l'écran de vente). */
    private final Map<UUID, String> lastRefusal = new HashMap<UUID, String>();

    /** Dernier message de refus du joueur (vide si aucun), effacé après lecture. */
    public String takeRefusal(Player player) {
        String text = lastRefusal.remove(player.getUniqueId());
        return text == null ? "" : text;
    }

    /** Joueurs dont les refus s'affichent dans l'écran ouvert (vente, achat) : pas de toast en plus. */
    private final Set<UUID> silent = new HashSet<UUID>();

    public void setSilent(Player player, boolean value) {
        if (value) {
            silent.add(player.getUniqueId());
        } else {
            silent.remove(player.getUniqueId());
        }
    }

    private HdvToast toasts() {
        return plugin.menus().toast();
    }

    /**
     * Évènement pour un joueur : toast (client KoriaMC, textes toast.&lt;toastKey&gt; de messages.json),
     * sinon le message chatKey dans le chat (null : rien).
     */
    public void event(Player player, HdvToast.Type type, String icon, String toastKey, String chatKey, Object... kv) {
        if (toasts().canShow(player)) {
            toasts().show(player, type, icon, plugin.msg().text("toast." + toastKey + ".categorie", kv),
                    plugin.msg().text("toast." + toastKey + ".titre", kv), plugin.msg().text("toast." + toastKey + ".sous-titre", kv));
        } else if (chatKey != null) {
            plugin.msg().send(player, chatKey, kv);
        }
    }

    /** Message d'erreur ou d'information : toast (client KoriaMC) sinon chat. */
    public void inform(Player player, boolean error, String key, Object... kv) {
        if (toasts().canShow(player)) {
            toasts().message(player, error ? HdvToast.Type.ERREUR : HdvToast.Type.INFO, error ? "minecraft:barrier" : "minecraft:chest",
                    plugin.msg().text(error ? "toast.erreur.categorie" : "toast.info.categorie"), plugin.msg().plain(key, kv));
        } else {
            plugin.msg().send(player, key, kv);
        }
    }

    private void refuse(Player player, String key, Object... kv) {
        lastRefusal.put(player.getUniqueId(), plugin.msg().plain(key, kv));
        if (!silent.contains(player.getUniqueId())) {
            inform(player, true, key, kv);
        }
        Sounds.play(player, plugin.settings().sound("refus"));
    }
}
