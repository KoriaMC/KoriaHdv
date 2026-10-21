package fr.koriamc.hdv.gui;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.hook.GuiHook;
import fr.koriamc.hdv.market.MarketManager;
import fr.koriamc.hdv.market.Wallet;
import fr.koriamc.hdv.model.Claim;
import fr.koriamc.hdv.model.Currency;
import fr.koriamc.hdv.model.HistoryEntry;
import fr.koriamc.hdv.model.Listing;
import fr.koriamc.hdv.util.ItemCodec;
import fr.koriamc.hdv.util.KoriaTooltips;
import fr.koriamc.hdv.util.Text;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Menu custom de l'hôtel des ventes (layout KoriaMC, koriamc/guis/hdv.json, textures koriamc:textures/gui/hdv...).
 *
 * Le plugin envoie les données : variables %var_hdv_...%, la liste list:hdv_liste et les objets eux-mêmes dans
 * l'inventaire virtuel du joueur (%var_hdv_inventaire%, case = rang dans la liste), affichés par des éléments SLOT
 * en lecture seule. Les boutons lancent /hdv view|search|buy|cancel|claim. Sans le client KoriaMC, la liste est
 * envoyée dans le chat.
 */
public final class HdvMenu {

    /** Onglets : argument de /hdv view. */
    public enum Tab {
        ALL("all"),
        MONEY("money"),
        POINTS("points"),
        /** Ventes en cours du joueur, puis ses objets à récupérer. */
        MINE("mine"),
        /** Objets à récupérer seulement (/hdv claim). */
        CLAIMS("claims"),
        /** Achats et ventes du joueur. */
        HISTORY("history");

        public final String argument;

        Tab(String argument) {
            this.argument = argument;
        }

        public static Tab parse(String input) {
            if (input == null) {
                return null;
            }
            for (Tab tab : values()) {
                if (tab.argument.equalsIgnoreCase(input.trim())) {
                    return tab;
                }
            }
            return null;
        }

        /** Onglet de ventes du marché (la recherche s'y applique). */
        public boolean isMarket() {
            return this == ALL || this == MONEY || this == POINTS;
        }
    }

    private static final class View {
        Tab tab;
        int page;
        /** Recherche en cours (minuscules, vide = aucune). */
        String query = "";

        View(Tab tab, int page) {
            this.tab = tab;
            this.page = page;
        }
    }

    /** Une carte affichée (vente, objet à récupérer ou ligne d'historique). */
    private static final class Entry {
        final int id;
        final ItemStack item;
        final Map<String, String> data = new HashMap<String, String>();
        /** Lignes de l'infobulle KoriaMC de l'objet (prix, vendeur, expiration...). */
        final List<String> hover = new ArrayList<String>();

        Entry(int id, ItemStack item) {
            this.id = id;
            this.item = item;
        }
    }

    private final KoriaHdv plugin;
    private final Map<UUID, View> views = new HashMap<UUID, View>();
    /** Jeton secret de chaque joueur, exigé par /hdv buy : l'achat ne se fait que depuis le menu. */
    private final Map<UUID, String> tokens = new HashMap<UUID, String>();
    private final SecureRandom random = new SecureRandom();

    public HdvMenu(KoriaHdv plugin) {
        this.plugin = plugin;
    }

    private View view(Player player) {
        View view = views.get(player.getUniqueId());
        if (view == null) {
            view = new View(Tab.ALL, 1);
            views.put(player.getUniqueId(), view);
        }
        return view;
    }

    /** Ouvre le menu sur un onglet et une page (1 = première), sans recherche. */
    public void open(Player player, Tab tab, int page) {
        View view = new View(tab, Math.max(1, page));
        views.put(player.getUniqueId(), view);
        GuiHook gui = plugin.gui();
        if (!gui.canDisplay(player)) {
            chat(player, view);
            return;
        }
        push(player, view);
        String layout = plugin.settings().layout;
        if (!gui.open(player, layout)) {
            plugin.getLogger().warning("Le menu " + layout + " n'a pas pu s'ouvrir : le fichier koriamc/guis/" + layout
                    + ".json existe-t-il ?");
            chat(player, view);
        }
    }

    /** Rouvre le menu là où le joueur l'avait laissé (retour depuis l'écran d'achat). */
    public void reopen(Player player) {
        View view = view(player);
        GuiHook gui = plugin.gui();
        if (!gui.canDisplay(player)) {
            chat(player, view);
            return;
        }
        push(player, view);
        gui.open(player, plugin.settings().layout);
    }

    /** Change d'onglet ou de page dans le menu déjà ouvert (boutons du menu). La recherche est gardée sur le marché. */
    public void show(Player player, Tab tab, int page) {
        View view = view(player);
        if (!tab.isMarket() || !view.tab.isMarket()) {
            view.query = "";
        }
        view.tab = tab;
        view.page = Math.max(1, page);
        display(player, view);
    }

    /** Recherche dans les ventes (nom de l'objet, identifiant, vendeur) ; vide = plus de recherche. */
    public void search(Player player, String query) {
        View view = view(player);
        view.query = query == null ? "" : ChatColor.stripColor(query).trim().toLowerCase(Locale.ROOT);
        if (!view.tab.isMarket()) {
            view.tab = Tab.ALL;
        }
        view.page = 1;
        display(player, view);
    }

    private void display(Player player, View view) {
        if (plugin.gui().canDisplay(player)) {
            push(player, view);
        } else {
            chat(player, view);
        }
    }

    /** Remet à jour le menu du joueur après un achat, un retrait ou une récupération. */
    public void refresh(Player player) {
        View view = views.get(player.getUniqueId());
        if (view != null && plugin.gui().canDisplay(player)) {
            push(player, view);
        }
    }

    /** Remet à jour le menu de tous les joueurs qui l'ont ouvert (une vente a changé). */
    public void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void forget(Player player) {
        views.remove(player.getUniqueId());
        tokens.remove(player.getUniqueId());
    }

    /** Jeton d'achat du joueur (créé au premier besoin, gardé jusqu'à sa déconnexion). */
    public String token(Player player) {
        String token = tokens.get(player.getUniqueId());
        if (token == null) {
            token = Long.toString(random.nextLong() & Long.MAX_VALUE, 36);
            tokens.put(player.getUniqueId(), token);
        }
        return token;
    }

    // ============================================================ Données

    private boolean matches(View view, Listing l, ItemStack item) {
        if (view.query.isEmpty()) {
            return true;
        }
        String name = ChatColor.stripColor(ItemCodec.name(item)).toLowerCase(Locale.ROOT);
        String id = String.valueOf(ItemCodec.id(item)).toLowerCase(Locale.ROOT);
        return name.contains(view.query) || id.contains(view.query) || l.getSellerName().toLowerCase(Locale.ROOT).contains(view.query);
    }

    private List<Entry> entries(Player player, View view) {
        MarketManager market = plugin.market();
        List<Entry> entries = new ArrayList<Entry>();
        Tab tab = view.tab;
        long now = System.currentTimeMillis();

        SimpleDateFormat full = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE);
        if (tab == Tab.HISTORY) {
            SimpleDateFormat date = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
            for (HistoryEntry h : market.history(player.getUniqueId())) {
                ItemStack item = h.getItem();
                if (item == null) {
                    continue;
                }
                boolean sale = h.getType() == HistoryEntry.Type.SALE;
                Entry e = new Entry(h.getId(), item);
                String amount = market.format(sale ? h.getAmount() : h.getPrice(), h.getCurrency());
                e.data.put("action", "aucune");
                e.data.put("prix", amount);
                e.data.put("prix_couleur", (sale ? "&a+" : "&c-") + amount);
                e.data.put("ligne3", plugin.msg().text(sale ? "menu.line-sold" : "menu.line-bought", "autre", h.getOther(),
                        "date", date.format(new Date(h.getDate()))));
                e.data.put("vendeur", sale ? player.getName() : h.getOther());
                e.data.put("temps", date.format(new Date(h.getDate())));
                e.data.put("date", plugin.msg().text(sale ? "menu.tooltip-sold" : "menu.tooltip-bought", "autre", h.getOther(),
                        "date", full.format(new Date(h.getDate()))));
                e.data.put("confirmation", "");
                e.hover.add(hoverPrice(h.getCurrency(), (sale ? "&a+" : "&c-") + market.compact(sale ? h.getAmount() : h.getPrice(),
                        h.getCurrency())));
                e.hover.add("");
                e.hover.add(hover(sale ? "sold" : "bought", "autre", h.getOther(), "date", full.format(new Date(h.getDate()))));
                entries.add(e);
            }
            return entries;
        }

        if (tab == Tab.MINE || tab == Tab.MONEY || tab == Tab.POINTS || tab == Tab.ALL) {
            List<Listing> listings;
            if (tab == Tab.MINE) {
                listings = market.listingsOf(player.getUniqueId());
            } else {
                listings = market.listings(tab == Tab.MONEY ? Currency.MONEY : tab == Tab.POINTS ? Currency.POINTS : null);
            }
            for (Listing l : listings) {
                ItemStack item = l.getItem();
                if (item == null || (tab.isMarket() && !matches(view, l, item))) {
                    continue;
                }
                boolean own = player.getUniqueId().equals(l.getSeller());
                boolean dual = l.isDual();
                Entry e = new Entry(l.getId(), item);
                Currency main = l.getMainCurrency();
                String price = market.format(l.getPrice(main), main);
                String points = dual ? market.format(l.getPrice(Currency.POINTS), Currency.POINTS) : "";
                e.data.put("action", own ? "retirer" : dual ? "acheter_double" : "acheter");
                e.data.put("prix", market.priceText(l));
                e.data.put("prix_couleur", dual ? market.priceText(l) : (main == Currency.POINTS ? "&b" : "&e") + price);
                e.data.put("prix_carte", (main == Currency.POINTS ? "&b" : "&e") + price);
                // une ligne par monnaie (vide si la vente ne l'accepte pas)
                e.data.put("prix_dollars", l.accepts(Currency.MONEY) ? "&e" + market.format(l.getPrice(Currency.MONEY), Currency.MONEY) : "");
                e.data.put("prix_points", l.accepts(Currency.POINTS) ? "&b" + market.format(l.getPrice(Currency.POINTS), Currency.POINTS) : "");
                e.data.put("a_dollars", String.valueOf(l.accepts(Currency.MONEY)));
                e.data.put("a_points", String.valueOf(l.accepts(Currency.POINTS)));
                e.data.put("ligne3", dual ? plugin.msg().text("menu.line-or-points", "points", points)
                        : own ? plugin.msg().text("menu.line-own", "temps", plugin.msg().duration(l.getExpiresAt() - now))
                        : plugin.msg().text("menu.line-seller", "vendeur", l.getSellerName()));
                e.data.put("vendeur", l.getSellerName());
                e.data.put("temps", plugin.msg().duration(l.getExpiresAt() - now));
                e.data.put("date", plugin.msg().text("menu.tooltip-listed", "date", full.format(new Date(l.getCreatedAt()))));
                e.data.put("confirmation", confirm(item, price));
                e.data.put("confirmation_points", dual ? confirm(item, points) : "");
                for (Currency c : Currency.values()) {
                    if (l.accepts(c)) {
                        e.hover.add(hoverPrice(c, (c == Currency.POINTS ? "&b" : "&e") + market.compact(l.getPrice(c), c)));
                    }
                }
                e.hover.add("");
                if (!own) {
                    e.hover.add(hover("seller", "vendeur", l.getSellerName()));
                }
                e.hover.add(hover("expire", "expire", l.getExpiresAt()));
                if (own) {
                    e.hover.add("");
                    e.hover.add(plugin.msg().text("menu.tooltip-retirer"));
                }
                entries.add(e);
            }
            if (tab != Tab.MINE) {
                return entries;
            }
        }

        // objets à récupérer (onglet Mes ventes, après les ventes en cours, et /hdv claim)
        for (Claim c : market.claims(player.getUniqueId())) {
            ItemStack item = c.getItem();
            if (item == null) {
                continue;
            }
            Entry e = new Entry(c.getId(), item);
            String reason = plugin.msg().text("values.reason-" + c.getReason().name().toLowerCase(Locale.ROOT));
            e.data.put("action", "recuperer");
            e.data.put("prix", reason);
            e.data.put("prix_couleur", reason);
            e.data.put("ligne3", plugin.msg().text("menu.line-claim"));
            e.data.put("vendeur", "");
            e.data.put("temps", plugin.msg().since(c.getDate()));
            e.data.put("date", plugin.msg().text("menu.tooltip-claim-date", "date", full.format(new Date(c.getDate()))));
            e.data.put("confirmation", "");
            e.hover.add(hover("claim", "raison", reason, "date", full.format(new Date(c.getDate()))));
            e.hover.add("");
            e.hover.add(plugin.msg().text("menu.tooltip-recuperer"));
            entries.add(e);
        }
        return entries;
    }

    private String confirm(ItemStack item, String price) {
        return plugin.msg().text("menu.confirm-buy", "objet", ChatColor.stripColor(ItemCodec.name(item)),
                "quantite", item.getAmount(), "prix", ChatColor.stripColor(Text.color(price)));
    }

    private void push(Player player, View view) {
        GuiHook gui = plugin.gui();
        List<Entry> all = entries(player, view);
        int perPage = plugin.settings().perPage;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        view.page = Math.min(view.page, pages);
        int from = (view.page - 1) * perPage;
        List<Entry> page = all.subList(from, Math.min(all.size(), from + perPage));

        Inventory inventory = gui.hasInventories() ? gui.getInventory(player, plugin.settings().inventory) : null;
        if (inventory != null) {
            inventory.clear();
        }
        Inventory hoverInventory = gui.hasInventories() ? gui.getInventory(player, plugin.settings().hoverInventory) : null;
        if (hoverInventory != null) {
            hoverInventory.clear();
        }
        List<Map<String, String>> items = new ArrayList<Map<String, String>>();
        for (int i = 0; i < page.size(); i++) {
            Entry e = page.get(i);
            if (inventory != null && i < inventory.getSize()) {
                inventory.setItem(i, displayed(e));
            }
            if (hoverInventory != null && i < hoverInventory.getSize() && !e.hover.isEmpty()) {
                hoverInventory.setItem(i, hoverCard(e));
            }
            Map<String, String> item = new HashMap<String, String>(e.data);
            if (!item.containsKey("prix_carte")) {
                item.put("prix_carte", e.data.get("prix_couleur"));
                item.put("prix_dollars", e.data.get("prix_couleur"));
                item.put("prix_points", "");
                item.put("a_dollars", "false");
                item.put("a_points", "false");
            }
            if (!item.containsKey("confirmation_points")) {
                item.put("confirmation_points", "");
            }
            String name = ItemCodec.name(e.item);
            item.put("id", String.valueOf(e.id));
            item.put("nom", name);
            item.put("nom_court", shorten(name, 12));
            item.put("quantite", String.valueOf(e.item.getAmount()));
            item.put("icone", ItemCodec.id(e.item));
            item.put("infobulle", tooltip(e));
            items.add(item);
        }
        gui.setList(player, "hdv_liste", items);

        MarketManager market = plugin.market();
        String title = plugin.msg().text("menu.title-" + view.tab.argument);
        if (!view.query.isEmpty()) {
            title += plugin.msg().text("menu.title-search", "recherche", view.query);
        }
        int claims = market.claims(player.getUniqueId()).size();
        gui.setVariable(player, "hdv_commande", plugin.commands().getLabel());
        gui.setVariable(player, "hdv_jeton", token(player));
        gui.setVariable(player, "hdv_inventaire", plugin.settings().inventory);
        gui.setVariable(player, "hdv_inventaire_infobulle", plugin.settings().hoverInventory);
        gui.setVariable(player, "hdv_objets_reels", String.valueOf(inventory != null));
        gui.setVariable(player, "hdv_onglet", view.tab.argument);
        gui.setVariable(player, "hdv_vue_carres", String.valueOf(!view.tab.isMarket()));
        for (Tab tab : Tab.values()) {
            gui.setVariable(player, "hdv_onglet_" + tab.argument, String.valueOf(tab == view.tab));
        }
        gui.setVariable(player, "hdv_titre_onglet", Text.color(title));
        gui.setVariable(player, "hdv_recherche_active", String.valueOf(!view.query.isEmpty()));
        gui.setVariable(player, "hdv_vide", Text.color(plugin.msg().text(view.query.isEmpty() ? "menu.empty-" + view.tab.argument
                : "menu.empty-search")));
        gui.setVariable(player, "hdv_page", String.valueOf(view.page));
        gui.setVariable(player, "hdv_pages", String.valueOf(pages));
        gui.setVariable(player, "hdv_precedent", String.valueOf(view.page > 1));
        gui.setVariable(player, "hdv_suivant", String.valueOf(view.page < pages));
        gui.setVariable(player, "hdv_page_precedente", String.valueOf(Math.max(1, view.page - 1)));
        gui.setVariable(player, "hdv_page_suivante", String.valueOf(Math.min(pages, view.page + 1)));
        gui.setVariable(player, "hdv_total", String.valueOf(all.size()));
        gui.setVariable(player, "hdv_mes_ventes", String.valueOf(market.listingsOf(player.getUniqueId()).size()));
        gui.setVariable(player, "hdv_limite", String.valueOf(market.limit(player)));
        gui.setVariable(player, "hdv_a_recuperer", String.valueOf(claims));
        gui.setVariable(player, "hdv_a_recuperer_oui", String.valueOf(claims > 0));
        gui.setVariable(player, "hdv_argent", balance(player, Currency.MONEY));
        gui.setVariable(player, "hdv_points", balance(player, Currency.POINTS));
        // avec le signe : "%var_x%%" serait mal découpé en placeholders
        gui.setVariable(player, "hdv_taxe_dollars", Text.number(plugin.settings().taxMoney, 2) + "%");
        gui.setVariable(player, "hdv_taxe_points", Text.number(plugin.settings().taxPoints, 2) + "%");
    }

    private String balance(Player player, Currency currency) {
        Wallet wallet = plugin.market().wallet(currency);
        double value = wallet.isAvailable() ? wallet.balance(player) : -1;
        return value < 0 ? "?" : plugin.market().format(value, currency);
    }

    /** Coupe un texte coloré à max caractères visibles. */
    private static String shorten(String text, int max) {
        StringBuilder out = new StringBuilder();
        int visible = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < text.length()) {
                out.append(c).append(text.charAt(++i));
                continue;
            }
            if (visible >= max) {
                return out.toString().trim() + "..";
            }
            out.append(c);
            visible++;
        }
        return out.toString();
    }

    /** Lignes ajoutées sous la description de l'objet : prix, vendeur, date, expiration et effet du clic. */
    private List<String> details(Entry e) {
        List<String> lines = new ArrayList<String>();
        String action = e.data.get("action");
        lines.add("");
        lines.add(Text.color(plugin.msg().text("menu.tooltip-price", "prix", Text.color(e.data.get("prix_couleur")))));
        boolean buy = "acheter".equals(action) || "acheter_double".equals(action);
        if (buy && !e.data.get("vendeur").isEmpty()) {
            lines.add(Text.color(plugin.msg().text("menu.tooltip-seller", "vendeur", e.data.get("vendeur"))));
        }
        String date = e.data.get("date");
        if (date != null && !date.isEmpty()) {
            lines.add(Text.color(date));
        }
        if (buy || "retirer".equals(action)) {
            lines.add(Text.color(plugin.msg().text("menu.tooltip-time", "temps", e.data.get("temps"))));
        }
        String hint = plugin.msg().text("menu.tooltip-" + action);
        if (!hint.isEmpty()) {
            lines.add("");
            lines.add(Text.color(hint));
        }
        return lines;
    }

    /** Copie de l'objet montrée dans le menu : sa description suivie des détails de la vente. */
    private String hover(String key, Object... kv) {
        return plugin.msg().text("menu.hover." + key, kv);
    }

    private String hoverPrice(Currency currency, String amount) {
        return hover(currency == Currency.POINTS ? "price-points" : "price-dollars", "prix", Text.color(amount));
    }

    /** Copie de l'objet dont l'infobulle KoriaMC ne montre que la vente (prix, vendeur, expiration) : survol du prix. */
    private ItemStack hoverCard(Entry e) {
        String title = plugin.msg().text("menu.hover.title", "objet", ChatColor.stripColor(ItemCodec.name(e.item)),
                "quantite", e.item.getAmount());
        return KoriaTooltips.with(e.item, title, e.hover, true);
    }

    private ItemStack displayed(Entry e) {
        ItemStack copy = e.item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) {
            return copy;
        }
        List<String> lore = meta.hasLore() ? new ArrayList<String>(meta.getLore()) : new ArrayList<String>();
        lore.addAll(details(e));
        meta.setLore(lore);
        copy.setItemMeta(meta);
        return copy;
    }

    /** Infobulle texte (objet non affiché ou chat) : nom, description et détails. */
    private String tooltip(Entry e) {
        StringBuilder sb = new StringBuilder(Text.color(plugin.msg().text("menu.tooltip-name", "objet", ItemCodec.name(e.item),
                "quantite", e.item.getAmount())));
        ItemMeta meta = e.item.hasItemMeta() ? e.item.getItemMeta() : null;
        if (meta != null && meta.hasLore()) {
            for (String line : meta.getLore()) {
                sb.append('\n').append(line);
            }
        }
        for (String line : details(e)) {
            sb.append('\n').append(line);
        }
        return sb.toString();
    }

    // ============================================================== Chat

    /** Joueur sans le client KoriaMC : liste cliquable dans le chat. */
    private void chat(Player player, View view) {
        List<Entry> all = entries(player, view);
        int perPage = 10;
        int pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        view.page = Math.min(view.page, pages);
        String label = plugin.commands().getLabel();
        plugin.msg().send(player, "menu.chat-header", "onglet", plugin.msg().text("menu.tab-" + view.tab.argument),
                "page", view.page, "pages", pages);
        if (all.isEmpty()) {
            plugin.msg().send(player, "menu.chat-empty");
        }
        int from = (view.page - 1) * perPage;
        for (Entry e : all.subList(from, Math.min(all.size(), from + perPage))) {
            String action = e.data.get("action");
            String line = plugin.msg().get("menu.chat-" + (action.startsWith("acheter") ? "acheter" : action), "objet", ItemCodec.name(e.item),
                    "quantite", e.item.getAmount(), "prix", Text.color(e.data.get("prix_couleur")), "vendeur", e.data.get("vendeur"),
                    "ligne", Text.color(e.data.get("ligne3")));
            BaseComponent[] parts = TextComponent.fromLegacyText(line);
            HoverEvent hover = new HoverEvent(HoverEvent.Action.SHOW_TEXT, TextComponent.fromLegacyText(tooltip(e)));
            ClickEvent click = "aucune".equals(action) ? null : new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + label + " "
                    + (action.startsWith("acheter") ? "buy" : action.equals("retirer") ? "cancel" : "claim") + " " + e.id
                    + (action.startsWith("acheter") ? " " + token(player) : ""));
            for (BaseComponent part : parts) {
                part.setHoverEvent(hover);
                if (click != null) {
                    part.setClickEvent(click);
                }
            }
            player.spigot().sendMessage(parts);
        }
        plugin.msg().send(player, "menu.chat-footer", "label", label, "onglet", view.tab.argument,
                "suivante", Math.min(pages, view.page + 1));
    }
}
