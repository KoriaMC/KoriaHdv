package fr.koriamc.hdv.command.impl;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.command.CommandContext;
import fr.koriamc.hdv.command.SubCommand;
import fr.koriamc.hdv.config.JsonFile;
import fr.koriamc.hdv.gui.HdvMenu;
import fr.koriamc.hdv.market.MarketManager;
import fr.koriamc.hdv.model.Claim;
import fr.koriamc.hdv.model.Currency;
import fr.koriamc.hdv.model.Listing;
import fr.koriamc.hdv.util.ItemCodec;
import fr.koriamc.hdv.util.Text;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Commandes de /hdv : menu, onglets, vente, achat, retrait, récupération, configuration.
 */
public final class MarketCommands {

    private MarketCommands() {
    }

    private static Integer id(CommandContext ctx, String input) {
        Integer id = Text.parseInt(input);
        if (id == null) {
            ctx.msg("general.invalid-number", "input", input);
        }
        return id;
    }

    /** /hdv help */
    public static final class Help extends SubCommand {
        public Help(KoriaHdv plugin) {
            super(plugin, "help", false, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            ctx.msg("help.header", "taxe_dollars", Text.number(plugin.settings().taxMoney, 2),
                    "taxe_points", Text.number(plugin.settings().taxPoints, 2));
            for (SubCommand sub : plugin.commands().visible(ctx.sender)) {
                ctx.msg("help.line", "command", sub.getId(), "usage", sub.getUsage(), "description", sub.getDescription());
            }
            ctx.msg("help.footer");
        }
    }

    /** /hdv menu */
    public static final class OpenMenu extends SubCommand {
        public OpenMenu(KoriaHdv plugin) {
            super(plugin, "menu", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().hdvMenu().open(ctx.player, HdvMenu.Tab.ALL, 1);
        }
    }

    /** /hdv view &lt;all|money|points|mine|claims&gt; [page] : onglet du menu (boutons du menu) */
    public static final class View extends SubCommand {
        public View(KoriaHdv plugin) {
            super(plugin, "view", true, 1, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            HdvMenu.Tab tab = HdvMenu.Tab.parse(ctx.arg(0));
            if (tab == null) {
                ctx.msg("general.usage", "command", ctx.subLabel, "usage", getUsage());
                return;
            }
            Integer page = ctx.size() > 1 ? Text.parseInt(ctx.arg(1)) : Integer.valueOf(1);
            plugin.menus().hdvMenu().show(ctx.player, tab, page == null ? 1 : page);
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            if (ctx.size() == 1) {
                List<String> tabs = new ArrayList<String>();
                for (HdvMenu.Tab tab : HdvMenu.Tab.values()) {
                    tabs.add(tab.argument);
                }
                return Text.complete(ctx.arg(0), tabs);
            }
            return Collections.emptyList();
        }
    }

    /** /hdv search [texte] : recherche dans les ventes (sans texte : plus de recherche) */
    public static final class Search extends SubCommand {
        public Search(KoriaHdv plugin) {
            super(plugin, "search", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().hdvMenu().search(ctx.player, Text.join(ctx.args, 0, " "));
        }
    }

    /** /hdv back : retour au menu depuis l'écran d'achat (bouton, absent de l'aide) */
    public static final class Back extends SubCommand {
        public Back(KoriaHdv plugin) {
            super(plugin, "back", true, 0, false);
        }

        @Override
        public boolean isHidden() {
            return true;
        }

        @Override
        public void execute(CommandContext ctx) {
            plugin.menus().hdvMenu().reopen(ctx.player);
        }
    }

    /**
     * /hdv sell : écran de vente de l'objet en main (client KoriaMC). La vente ne se fait que par cet écran :
     * son bouton lance /hdv sell gui &lt;jeton&gt; d:&lt;dollars&gt; p:&lt;points&gt; (jeton secret du joueur).
     */
    public static final class Sell extends SubCommand {
        public Sell(KoriaHdv plugin) {
            super(plugin, "sell", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (ctx.size() == 0) {
                if (!plugin.menus().sellMenu().open(ctx.player)) {
                    ctx.msg("sell.client-required");
                }
                return;
            }
            // la vente ne passe que par l'écran de vente (bouton Vendre, avec le jeton du joueur)
            if (ctx.size() >= 2 && ctx.arg(0).equalsIgnoreCase("gui")
                    && plugin.menus().hdvMenu().token(ctx.player).equals(ctx.arg(1))) {
                sellBoth(ctx);
                return;
            }
            if (!plugin.menus().sellMenu().open(ctx.player)) {
                ctx.msg("sell.client-required");
            }
        }

        /** Arguments de l'écran de vente : gui &lt;jeton&gt; d:&lt;dollars&gt; p:&lt;points&gt; [quantité] (valeurs vides = monnaie refusée). */
        private void sellBoth(CommandContext ctx) {
            double money = 0;
            double points = 0;
            int amount = 0;
            for (int i = 2; i < ctx.size(); i++) {
                String arg = ctx.arg(i);
                String lower = arg.toLowerCase(Locale.ROOT);
                if (lower.startsWith("d:") || lower.startsWith("p:")) {
                    Double value = MarketManager.parsePrice(arg.substring(2).replace(" ", "").replace(',', '.'));
                    if (lower.startsWith("d:")) {
                        money = value == null ? 0 : value;
                    } else {
                        points = value == null ? 0 : value;
                    }
                } else {
                    Integer n = Text.parseInt(arg);
                    if (n != null && n > 0) {
                        amount = n;
                    }
                }
            }
            plugin.market().takeRefusal(ctx.player);
            plugin.market().setSilent(ctx.player, true);
            Listing listing;
            try {
                listing = plugin.market().sell(ctx.player, money, points, amount);
            } finally {
                plugin.market().setSilent(ctx.player, false);
            }
            if (listing != null) {
                plugin.menus().hdvMenu().refreshAll();
                HdvMenu.Tab tab = HdvMenu.Tab.parse(plugin.settings().afterSell);
                if (tab == null) {
                    plugin.menus().hdvMenu().reopen(ctx.player);
                } else {
                    plugin.menus().hdvMenu().open(ctx.player, tab, 1);
                }
            } else {
                plugin.menus().sellMenu().error(ctx.player, plugin.market().takeRefusal(ctx.player));
            }
        }
    }

    /**
     * /hdv buy &lt;id&gt; &lt;jeton&gt; [money|points] [confirm] : achat, uniquement depuis le menu (le jeton secret du joueur
     * n'est connu que du menu et des lignes cliquables du chat). Sans "confirm", demande de confirmer (chat).
     */
    public static final class Buy extends SubCommand {
        public Buy(KoriaHdv plugin) {
            super(plugin, "buy", true, 0, false);
        }

        @Override
        public boolean isHidden() {
            return true;
        }

        @Override
        public void execute(CommandContext ctx) {
            if (ctx.size() < 2 || !plugin.menus().hdvMenu().token(ctx.player).equals(ctx.arg(1))) {
                ctx.msg("general.unknown-command");
                return;
            }
            Integer id = id(ctx, ctx.arg(0));
            if (id == null) {
                return;
            }
            Currency currency = null;
            boolean confirm = false;
            for (int i = 2; i < ctx.size(); i++) {
                if (ctx.arg(i).equalsIgnoreCase("confirm")) {
                    confirm = true;
                } else if (Currency.parse(ctx.arg(i)) != null) {
                    currency = Currency.parse(ctx.arg(i));
                }
            }
            Listing l = plugin.market().get(id);
            boolean gui = plugin.gui().canDisplay(ctx.player);
            if (gui && !confirm) {
                // clic sur une vente du menu : écran d'achat
                if (l == null || l.isExpired(System.currentTimeMillis()) || l.getItem() == null) {
                    plugin.market().inform(ctx.player, true, "buy.gone");
                    plugin.menus().hdvMenu().refresh(ctx.player);
                } else if (ctx.player.getUniqueId().equals(l.getSeller())) {
                    plugin.market().inform(ctx.player, true, "buy.own");
                } else {
                    plugin.menus().buyMenu().open(ctx.player, l);
                }
                return;
            }
            if (!confirm || (currency == null && l != null && l.isDual())) {
                ItemStack item = l == null ? null : l.getItem();
                if (item == null) {
                    ctx.msg("buy.gone");
                    return;
                }
                String base = "/" + ctx.label + " buy " + id + " " + ctx.arg(1) + " ";
                if (currency == null && l.isDual()) {
                    // l'acheteur choisit la monnaie
                    List<BaseComponent> parts = new ArrayList<BaseComponent>(Arrays.asList(TextComponent.fromLegacyText(
                            plugin.msg().get("buy.choose-header", "objet", ItemCodec.name(item), "quantite", item.getAmount(),
                                    "vendeur", l.getSellerName()))));
                    for (Currency c : Currency.values()) {
                        String button = plugin.msg().text("buy.choose-button-" + c.argument(),
                                "prix", plugin.market().format(l.getPrice(c), c));
                        ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, base + c.argument() + " confirm");
                        for (BaseComponent part : TextComponent.fromLegacyText(" " + button)) {
                            part.setClickEvent(click);
                            parts.add(part);
                        }
                    }
                    ctx.player.spigot().sendMessage(parts.toArray(new BaseComponent[parts.size()]));
                    return;
                }
                Currency c = currency != null && l.accepts(currency) ? currency : l.getMainCurrency();
                String line = plugin.msg().get("buy.confirm", "objet", ItemCodec.name(item), "quantite", item.getAmount(),
                        "prix", plugin.market().format(l.getPrice(c), c), "vendeur", l.getSellerName());
                BaseComponent[] parts = TextComponent.fromLegacyText(line);
                ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, base + c.argument() + " confirm");
                for (BaseComponent part : parts) {
                    part.setClickEvent(click);
                }
                ctx.player.spigot().sendMessage(parts);
                return;
            }
            plugin.market().takeRefusal(ctx.player);
            plugin.market().setSilent(ctx.player, gui);
            boolean bought;
            try {
                bought = plugin.market().buy(ctx.player, id, currency);
            } finally {
                plugin.market().setSilent(ctx.player, false);
            }
            if (bought) {
                plugin.menus().hdvMenu().refreshAll();
                if (gui) {
                    plugin.menus().hdvMenu().reopen(ctx.player);
                }
            } else if (gui) {
                plugin.menus().buyMenu().error(ctx.player, plugin.market().takeRefusal(ctx.player));
            }
        }
    }

    /** /hdv cancel &lt;id&gt; : retire sa vente (l'objet est à récupérer) */
    public static final class Cancel extends SubCommand {
        public Cancel(KoriaHdv plugin) {
            super(plugin, "cancel", true, 1, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            Integer id = id(ctx, ctx.arg(0));
            if (id != null && plugin.market().cancel(ctx.player, id)) {
                plugin.menus().hdvMenu().refreshAll();
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            List<String> ids = new ArrayList<String>();
            for (Listing l : plugin.market().listingsOf(ctx.player.getUniqueId())) {
                ids.add(String.valueOf(l.getId()));
            }
            return ctx.size() == 1 ? Text.complete(ctx.arg(0), ids) : Collections.<String>emptyList();
        }
    }

    /** /hdv claim [id|all] : récupère les objets retirés ou expirés */
    public static final class ClaimItems extends SubCommand {
        public ClaimItems(KoriaHdv plugin) {
            super(plugin, "claim", true, 0, false);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (ctx.size() == 0) {
                plugin.menus().hdvMenu().open(ctx.player, HdvMenu.Tab.CLAIMS, 1);
                return;
            }
            int id = -1;
            if (!ctx.arg(0).equalsIgnoreCase("all")) {
                Integer parsed = id(ctx, ctx.arg(0));
                if (parsed == null) {
                    return;
                }
                id = parsed;
            }
            if (plugin.market().claim(ctx.player, id) > 0) {
                plugin.menus().hdvMenu().refresh(ctx.player);
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            List<String> ids = new ArrayList<String>();
            ids.add("all");
            for (Claim c : plugin.market().claims(ctx.player.getUniqueId())) {
                ids.add(String.valueOf(c.getId()));
            }
            return ctx.size() == 1 ? Text.complete(ctx.arg(0), ids) : Collections.<String>emptyList();
        }
    }

    /** /hdv config [fichier] */
    public static final class Config extends SubCommand {
        public Config(KoriaHdv plugin) {
            super(plugin, "config", true, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            if (!plugin.menus().openConfig(ctx.player, ctx.arg(0))) {
                ctx.msg("config-editor.unknown-file", "file", ctx.arg(0));
            }
        }

        @Override
        public List<String> complete(CommandContext ctx) {
            List<String> names = new ArrayList<String>();
            for (JsonFile file : plugin.configFiles()) {
                names.add(file.getName().replace(".json", ""));
            }
            return ctx.size() == 1 ? Text.complete(ctx.arg(0), names) : Collections.<String>emptyList();
        }
    }

    /** /hdv reload */
    public static final class Reload extends SubCommand {
        public Reload(KoriaHdv plugin) {
            super(plugin, "reload", false, 0, true);
        }

        @Override
        public void execute(CommandContext ctx) {
            List<String> problems = plugin.reload();
            ctx.msg("general.reload-done", "ventes", plugin.market().listings(null).size());
            if (!problems.isEmpty()) {
                ctx.msg("general.reload-problems", "count", problems.size());
                for (String problem : problems) {
                    ctx.msg("general.reload-problem", "problem", problem);
                }
            }
        }
    }
}
