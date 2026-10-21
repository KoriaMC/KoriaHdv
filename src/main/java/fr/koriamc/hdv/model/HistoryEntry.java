package fr.koriamc.hdv.model;

import fr.koriamc.hdv.util.ItemCodec;
import org.bukkit.inventory.ItemStack;

/**
 * Ligne de l'historique d'un joueur : un achat ou une vente (donnees.json).
 */
public final class HistoryEntry {

    /** Achat (le joueur a payé) ou vente (le joueur a reçu). */
    public enum Type {
        PURCHASE,
        SALE
    }

    private int id;
    private Type type;
    private String item;
    private Currency currency;
    private double price;
    /** Montant réellement payé ou reçu (vente en points : prix moins la taxe). */
    private double amount;
    /** Nom de l'autre joueur (vendeur pour un achat, acheteur pour une vente). */
    private String other;
    private long date;

    private transient ItemStack cached;

    public HistoryEntry() {
    }

    public HistoryEntry(int id, Type type, String encodedItem, Currency currency, double price, double amount, String other) {
        this.id = id;
        this.type = type;
        this.item = encodedItem;
        this.currency = currency;
        this.price = price;
        this.amount = amount;
        this.other = other;
        this.date = System.currentTimeMillis();
    }

    public int getId() {
        return id;
    }

    public Type getType() {
        return type == null ? Type.PURCHASE : type;
    }

    public ItemStack getItem() {
        if (cached == null) {
            cached = ItemCodec.decode(item);
        }
        return cached == null ? null : cached.clone();
    }

    public Currency getCurrency() {
        return currency == null ? Currency.MONEY : currency;
    }

    public double getPrice() {
        return price;
    }

    public double getAmount() {
        return amount;
    }

    public String getOther() {
        return other == null ? "?" : other;
    }

    public long getDate() {
        return date;
    }
}
