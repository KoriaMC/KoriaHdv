package fr.koriamc.hdv.gui;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.hook.GuiHook;
import fr.koriamc.hdv.market.MarketManager;
import fr.koriamc.hdv.market.Wallet;
import fr.koriamc.hdv.model.Currency;
import fr.koriamc.hdv.model.Listing;
import fr.koriamc.hdv.util.ItemCodec;
import fr.koriamc.hdv.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Écran d'achat d'une vente (layout KoriaMC, koriamc/guis/hdv_achat.json), ouvert au clic sur une vente du menu :
 * l'objet, ses prix et un bouton « Acheter en » par monnaie acceptée. Les boutons lancent
 * /hdv buy &lt;id&gt; &lt;jeton&gt; &lt;money|points&gt; confirm.
 */
public final class BuyMenu {

    private final KoriaHdv plugin;

    public BuyMenu(KoriaHdv plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre l'écran d'achat.
     *
     * @return false si le joueur n'a pas le client KoriaMC ou si l'écran n'a pas pu s'ouvrir
     */
    public boolean open(Player player, Listing listing) {
        GuiHook gui = plugin.gui();
        if (!gui.canDisplay(player)) {
            return false;
        }
        ItemStack item = listing.getItem();
        if (item == null) {
            plugin.market().inform(player, true, "market.broken-item");
            return true;
        }
        MarketManager market = plugin.market();
        String spec = plugin.settings().sellInventory;
        Inventory inventory = gui.hasInventories() ? gui.getInventory(player, spec) : null;
        if (inventory != null) {
            inventory.clear();
            inventory.setItem(0, item);
        }
        gui.setVariable(player, "hdv_commande", plugin.commands().getLabel());
        gui.setVariable(player, "hdv_jeton", plugin.menus().hdvMenu().token(player));
        gui.setVariable(player, "hdv_achat_id", String.valueOf(listing.getId()));
        gui.setVariable(player, "hdv_vente_inventaire", spec);
        gui.setVariable(player, "hdv_objets_reels", String.valueOf(inventory != null));
        gui.setVariable(player, "hdv_achat_objet", Text.color(plugin.msg().text("menu.sell-item", "objet", ItemCodec.name(item),
                "quantite", item.getAmount())));
        gui.setVariable(player, "hdv_achat_icone", ItemCodec.id(item));
        gui.setVariable(player, "hdv_achat_vendeur", Text.color(plugin.msg().text("menu.buy-seller", "vendeur", listing.getSellerName(),
                "temps", plugin.msg().duration(listing.getExpiresAt() - System.currentTimeMillis()))));
        for (Currency c : Currency.values()) {
            String key = c == Currency.POINTS ? "points" : "dollars";
            boolean accepted = listing.accepts(c);
            gui.setVariable(player, "hdv_achat_" + key + "_oui", String.valueOf(accepted));
            gui.setVariable(player, "hdv_achat_prix_" + key, accepted ? market.format(listing.getPrice(c), c)
                    : Text.color(plugin.msg().text("menu.buy-refused")));
            gui.setVariable(player, "hdv_solde_" + key, balance(player, c));
        }
        error(player, "");
        String layout = plugin.settings().buyLayout;
        if (!gui.open(player, layout)) {
            plugin.getLogger().warning("Le menu " + layout + " n'a pas pu s'ouvrir : le fichier koriamc/guis/" + layout
                    + ".json existe-t-il ?");
            return false;
        }
        return true;
    }

    /** Affiche un message d'erreur dans l'écran d'achat (vide = aucun). */
    public void error(Player player, String message) {
        GuiHook gui = plugin.gui();
        String text = message == null ? "" : message.replace('\n', ' ').trim();
        gui.setVariable(player, "hdv_achat_erreur", text);
        gui.setVariable(player, "hdv_achat_erreur_oui", String.valueOf(!text.isEmpty()));
    }

    private String balance(Player player, Currency currency) {
        Wallet wallet = plugin.market().wallet(currency);
        double value = wallet.isAvailable() ? wallet.balance(player) : -1;
        return value < 0 ? "?" : plugin.market().format(value, currency);
    }
}
