package fr.koriamc.hdv.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contenu de donnees.json : ventes en cours, objets à récupérer, gains en points en attente
 * et messages pour les joueurs hors ligne.
 */
public final class MarketData {

    public int nextId = 1;
    public List<Listing> listings = new ArrayList<Listing>();
    /** UUID -> objets à récupérer. */
    public Map<String, List<Claim>> claims = new LinkedHashMap<String, List<Claim>>();
    /** UUID -> points boutique à verser à la prochaine connexion (mode VARIABLE). */
    public Map<String, Double> pendingPoints = new LinkedHashMap<String, Double>();
    /** UUID -> messages à afficher à la prochaine connexion (ventes faites hors ligne). */
    public Map<String, List<String>> notices = new LinkedHashMap<String, List<String>>();
    /** UUID -> achats et ventes du joueur, les plus récents d'abord. */
    public Map<String, List<HistoryEntry>> history = new LinkedHashMap<String, List<HistoryEntry>>();

    public void ensureInit() {
        if (listings == null) {
            listings = new ArrayList<Listing>();
        }
        if (claims == null) {
            claims = new LinkedHashMap<String, List<Claim>>();
        }
        if (pendingPoints == null) {
            pendingPoints = new LinkedHashMap<String, Double>();
        }
        if (notices == null) {
            notices = new LinkedHashMap<String, List<String>>();
        }
        if (history == null) {
            history = new LinkedHashMap<String, List<HistoryEntry>>();
        }
        for (Listing l : listings) {
            if (l.getId() >= nextId) {
                nextId = l.getId() + 1;
            }
        }
        for (List<Claim> list : claims.values()) {
            for (Claim c : list) {
                if (c.getId() >= nextId) {
                    nextId = c.getId() + 1;
                }
            }
        }
    }

    public int takeId() {
        return nextId++;
    }
}
