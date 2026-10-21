package fr.koriamc.hdv.model;

import fr.koriamc.hdv.util.ItemCodec;
import org.bukkit.inventory.ItemStack;

/**
 * Objet à récupérer par son propriétaire : vente expirée, retirée ou supprimée par un admin.
 */
public final class Claim {

    /** Raison du retour de l'objet (clé de messages.json : values.reason-...). */
    public enum Reason {
        EXPIRED,
        CANCELLED,
        REMOVED
    }

    private int id;
    private String item;
    private Reason reason;
    private long date;

    private transient ItemStack cached;

    public Claim() {
    }

    public Claim(int id, String encodedItem, Reason reason) {
        this.id = id;
        this.item = encodedItem;
        this.reason = reason;
        this.date = System.currentTimeMillis();
    }

    public int getId() {
        return id;
    }

    public ItemStack getItem() {
        if (cached == null) {
            cached = ItemCodec.decode(item);
        }
        return cached == null ? null : cached.clone();
    }

    public Reason getReason() {
        return reason == null ? Reason.EXPIRED : reason;
    }

    public long getDate() {
        return date;
    }
}
