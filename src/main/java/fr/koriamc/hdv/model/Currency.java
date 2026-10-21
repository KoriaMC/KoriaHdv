package fr.koriamc.hdv.model;

import java.util.Locale;

/**
 * Monnaie d'une vente : dollars (Vault) ou points boutique.
 */
public enum Currency {
    /** Dollars : la taxe est payée par le vendeur au moment de la mise en vente. */
    MONEY,
    /** Points boutique : la taxe est retenue sur le montant reçu par le vendeur. */
    POINTS;

    /** money, dollars, $ / points, pts, pb (null si inconnu). */
    public static Currency parse(String input) {
        if (input == null) {
            return null;
        }
        String v = input.trim().toLowerCase(Locale.ROOT);
        if (v.equals("money") || v.equals("dollars") || v.equals("dollar") || v.equals("$") || v.equals("coins")) {
            return MONEY;
        }
        if (v.equals("points") || v.equals("point") || v.equals("pts") || v.equals("pb") || v.equals("shop")) {
            return POINTS;
        }
        return null;
    }

    /** Argument des commandes (money, points). */
    public String argument() {
        return this == MONEY ? "money" : "points";
    }
}
