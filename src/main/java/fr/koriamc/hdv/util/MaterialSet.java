package fr.koriamc.hdv.util;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Ensemble de blocs / objets défini dans la configuration. Accepte les noms
 * Bukkit (CHEST), les ID numériques (54) et les couples ID:data (35:14), ce qui
 * permet de cibler les blocs moddés de KoriaMC.
 */
@SuppressWarnings("deprecation")
public final class MaterialSet {

    private final Set<Integer> ids = new HashSet<Integer>();
    private final Set<Integer> idData = new HashSet<Integer>();

    public static MaterialSet of(Collection<String> specs, Logger logger, String context) {
        MaterialSet set = new MaterialSet();
        if (specs == null) {
            return set;
        }
        for (String spec : specs) {
            int[] parsed = parse(spec);
            if (parsed == null) {
                if (logger != null) {
                    logger.warning("Matériau inconnu '" + spec + "' dans " + context);
                }
                continue;
            }
            if (parsed[1] < 0) {
                set.ids.add(parsed[0]);
            } else {
                set.idData.add(key(parsed[0], parsed[1]));
            }
        }
        return set;
    }

    /** @return {id, data} avec data = -1 si non précisé, ou null si invalide. */
    public static int[] parse(String spec) {
        if (spec == null) {
            return null;
        }
        String s = spec.trim();
        if (s.isEmpty()) {
            return null;
        }
        String typePart = s;
        int data = -1;
        int colon = s.indexOf(':');
        if (colon > 0) {
            typePart = s.substring(0, colon);
            Integer d = Text.parseInt(s.substring(colon + 1));
            if (d == null) {
                return null;
            }
            data = d;
        }
        Integer id = Text.parseInt(typePart);
        if (id == null) {
            Material material = Material.matchMaterial(typePart.toUpperCase(Locale.ROOT));
            if (material == null) {
                return null;
            }
            id = material.getId();
        }
        return new int[]{id, data};
    }

    public static int key(int id, int data) {
        return (id << 16) | (data & 0xFFFF);
    }

    public boolean contains(int id, int data) {
        return ids.contains(id) || idData.contains(key(id, data));
    }

    public boolean contains(Block block) {
        return block != null && contains(block.getTypeId(), block.getData());
    }

    public boolean contains(ItemStack item) {
        return item != null && contains(item.getTypeId(), item.getDurability());
    }

    public boolean isEmpty() {
        return ids.isEmpty() && idData.isEmpty();
    }
}
