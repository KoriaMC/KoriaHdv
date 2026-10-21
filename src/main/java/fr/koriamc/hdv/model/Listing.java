package fr.koriamc.hdv.model;

import fr.koriamc.hdv.util.ItemCodec;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Un objet mis en vente à l'hôtel des ventes (donnees.json).
 */
public final class Listing {

    private int id;
    private String seller;
    private String sellerName;
    private String item;
    /** Anciennes ventes (une seule monnaie) : monnaie et prix. Gardés à jour avec la monnaie principale. */
    private Currency currency;
    private double price;
    /** Prix en dollars (0 = pas payable en dollars). */
    private double moneyPrice;
    /** Prix en points boutique (0 = pas payable en points). */
    private double pointsPrice;
    /** Taxe déjà payée par le vendeur (ventes en dollars). */
    private double taxPaid;
    private long createdAt;
    private long expiresAt;

    private transient ItemStack cached;

    public Listing() {
    }

    /** Vente payable en dollars et/ou en points (au choix de l'acheteur) ; un prix &lt;= 0 = monnaie refusée. */
    public Listing(int id, UUID seller, String sellerName, ItemStack item, double moneyPrice, double pointsPrice, double taxPaid,
                   long durationMs) {
        this.id = id;
        this.seller = seller.toString();
        this.sellerName = sellerName;
        this.item = ItemCodec.encode(item);
        this.cached = item.clone();
        this.moneyPrice = Math.max(0, moneyPrice);
        this.pointsPrice = Math.max(0, pointsPrice);
        this.currency = this.moneyPrice > 0 ? Currency.MONEY : Currency.POINTS;
        this.price = this.moneyPrice > 0 ? this.moneyPrice : this.pointsPrice;
        this.taxPaid = taxPaid;
        this.createdAt = System.currentTimeMillis();
        this.expiresAt = createdAt + durationMs;
    }

    public int getId() {
        return id;
    }

    public UUID getSeller() {
        try {
            return UUID.fromString(seller);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public String getSellerName() {
        return sellerName == null ? "?" : sellerName;
    }

    /** Copie de l'objet vendu, ou null s'il est illisible. */
    public ItemStack getItem() {
        if (cached == null) {
            cached = ItemCodec.decode(item);
        }
        return cached == null ? null : cached.clone();
    }

    public String getEncodedItem() {
        return item;
    }

    /** Prix dans cette monnaie, 0 si la vente ne l'accepte pas. */
    public double getPrice(Currency c) {
        if (moneyPrice > 0 || pointsPrice > 0) {
            return c == Currency.POINTS ? pointsPrice : moneyPrice;
        }
        return (currency == null ? Currency.MONEY : currency) == c ? price : 0;
    }

    public boolean accepts(Currency c) {
        return getPrice(c) > 0;
    }

    /** Payable dans les deux monnaies (l'acheteur choisit). */
    public boolean isDual() {
        return accepts(Currency.MONEY) && accepts(Currency.POINTS);
    }

    /** Seule monnaie acceptée, ou les dollars si les deux le sont. */
    public Currency getMainCurrency() {
        return accepts(Currency.MONEY) ? Currency.MONEY : Currency.POINTS;
    }

    public double getTaxPaid() {
        return taxPaid;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }
}
