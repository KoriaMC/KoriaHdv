package fr.koriamc.hdv.gui;

import fr.koriamc.hdv.KoriaHdv;
import fr.koriamc.hdv.hook.GuiHook;
import fr.koriamc.hdv.model.Currency;
import fr.koriamc.hdv.util.ItemCodec;
import fr.koriamc.hdv.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Écran de vente de l'objet en main (layout KoriaMC, koriamc/guis/hdv_vente.json) : un prix en dollars et/ou en
 * points boutique, la taxe de chaque monnaie est calculée en direct par le client. Le bouton Vendre lance
 * /hdv sell gui &lt;jeton&gt; d:&lt;dollars&gt; p:&lt;points&gt;.
 */
public final class SellMenu {

    private final KoriaHdv plugin;

    public SellMenu(KoriaHdv plugin) {
        this.plugin = plugin;
    }

    /**
     * Ouvre l'écran de vente.
     *
     * @return false si le joueur n'a pas le client KoriaMC (la vente se fait alors par commande)
     */
    @SuppressWarnings("deprecation")
    public boolean open(Player player) {
        GuiHook gui = plugin.gui();
        if (!gui.canDisplay(player)) {
            return false;
        }
        ItemStack hand = player.getItemInHand();
        if (hand == null || hand.getTypeId() == 0 || hand.getAmount() <= 0) {
            plugin.market().inform(player, true, "sell.empty-hand");
            return true;
        }
        if (plugin.settings().blacklist.contains(hand)) {
            plugin.market().inform(player, true, "sell.forbidden");
            return true;
        }
        String spec = plugin.settings().sellInventory;
        Inventory inventory = gui.hasInventories() ? gui.getInventory(player, spec) : null;
        if (inventory != null) {
            inventory.clear();
            inventory.setItem(0, hand.clone());
        }
        gui.setVariable(player, "hdv_commande", plugin.commands().getLabel());
        gui.setVariable(player, "hdv_jeton", plugin.menus().hdvMenu().token(player));
        gui.setVariable(player, "hdv_vente_inventaire", spec);
        gui.setVariable(player, "hdv_objets_reels", String.valueOf(inventory != null));
        gui.setVariable(player, "hdv_vente_objet", Text.color(plugin.msg().text("menu.sell-item", "objet", ItemCodec.name(hand),
                "quantite", hand.getAmount())));
        gui.setVariable(player, "hdv_vente_icone", ItemCodec.id(hand));
        gui.setVariable(player, "hdv_taux_dollars", plain(plugin.settings().taxMoney));
        gui.setVariable(player, "hdv_taux_points", plain(plugin.settings().taxPoints));
        // avec le signe : "%var_x%%" serait mal découpé en placeholders
        gui.setVariable(player, "hdv_taxe_dollars", Text.number(plugin.settings().taxMoney, 2) + "%");
        gui.setVariable(player, "hdv_taxe_points", Text.number(plugin.settings().taxPoints, 2) + "%");
        gui.setVariable(player, "hdv_decimales_points", String.valueOf(plugin.settings().decimals(Currency.POINTS)));
        gui.setVariable(player, "hdv_symbole_dollars", plugin.market().format(0, Currency.MONEY).replaceAll("[0-9.,\\s  ]", "").trim());
        gui.setVariable(player, "hdv_symbole_points", plugin.market().format(0, Currency.POINTS).replaceAll("[0-9.,\\s  ]", "").trim());
        error(player, "");
        String layout = plugin.settings().sellLayout;
        if (!gui.open(player, layout)) {
            plugin.getLogger().warning("Le menu " + layout + " n'a pas pu s'ouvrir : le fichier koriamc/guis/" + layout
                    + ".json existe-t-il ?");
            return false;
        }
        return true;
    }

    /** Affiche un message d'erreur dans l'écran de vente (vide = aucun). */
    public void error(Player player, String message) {
        GuiHook gui = plugin.gui();
        String text = message == null ? "" : message.replace('\n', ' ').trim();
        gui.setVariable(player, "hdv_vente_erreur", text);
        gui.setVariable(player, "hdv_vente_erreur_oui", String.valueOf(!text.isEmpty()));
    }

    /** Nombre sans séparateur de milliers, lisible par les expressions du client. */
    private static String plain(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
