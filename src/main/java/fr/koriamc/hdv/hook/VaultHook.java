package fr.koriamc.hdv.hook;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Seule classe qui référence l'API Vault : elle n'est chargée que si Vault est
 * installé, ce qui évite toute erreur de classe manquante.
 */
public final class VaultHook {

    private final Economy economy;

    private VaultHook(Economy economy) {
        this.economy = economy;
    }

    /** @return le pont vers l'économie, ou null si aucun fournisseur n'est enregistré. */
    public static VaultHook create() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null || rsp.getProvider() == null ? null : new VaultHook(rsp.getProvider());
    }

    public String getName() {
        return economy.getName();
    }

    public double getBalance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public void deposit(OfflinePlayer player, double amount) {
        economy.depositPlayer(player, amount);
    }
}
