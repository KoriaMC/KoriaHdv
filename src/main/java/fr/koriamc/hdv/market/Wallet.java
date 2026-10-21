package fr.koriamc.hdv.market;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Une monnaie de l'hôtel des ventes (dollars ou points boutique).
 */
public interface Wallet {

    /** La monnaie peut-elle être utilisée (économie Vault présente...) ? */
    boolean isAvailable();

    /** Solde du joueur connecté (-1 si inconnu). */
    double balance(Player player);

    boolean has(Player player, double amount);

    /** Retire la somme au joueur connecté. @return false si le paiement a échoué */
    boolean withdraw(Player player, double amount);

    /**
     * Verse la somme au joueur, même hors ligne si la monnaie le permet.
     *
     * @return false si le versement doit attendre sa prochaine connexion
     */
    boolean deposit(OfflinePlayer player, String name, double amount);
}
