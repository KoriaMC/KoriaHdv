package fr.koriamc.hdv.util;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Construction d'objets depuis la configuration et sérialisation d'inventaires.
 */
@SuppressWarnings("deprecation")
public final class Items {

    private Items() {
    }

    /** "DIAMOND", "264", "35:14", "WOOL:3" -> ItemStack (STONE si invalide). */
    public static ItemStack parse(String spec) {
        int[] parsed = MaterialSet.parse(spec);
        if (parsed == null) {
            return new ItemStack(Material.STONE);
        }
        int data = parsed[1] < 0 ? 0 : parsed[1];
        return new ItemStack(parsed[0], 1, (short) data);
    }

    public static ItemStack build(ItemStack base, String name, List<String> lore, boolean glow) {
        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null) {
            meta.setDisplayName(Text.color(name));
        }
        if (lore != null && !lore.isEmpty()) {
            List<String> colored = new ArrayList<String>();
            for (String line : lore) {
                // une ligne contenant des retours à la ligne est découpée
                for (String part : Text.color(line).split("\n")) {
                    colored.add(part);
                }
            }
            meta.setLore(colored);
        }
        if (glow) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
        }
        meta.addItemFlags(ItemFlag.values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Crée un objet à partir d'une section (materiau, quantite, nom, description,
     * brillant, tete). Les variables sont remplacées via {@code kv}.
     */
    public static ItemStack fromSection(ConfigurationSection section, Object... kv) {
        return fromSection(section, "materiau", null, kv);
    }

    /**
     * @param materialKey clé du matériau à utiliser (ex. "materiau-autorise"), "materiau" si absente
     * @param glow        brillance imposée par l'état de l'objet, ou null pour l'option "brillant"
     */
    public static ItemStack fromSection(ConfigurationSection section, String materialKey, Boolean glow, Object... kv) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }
        String material = section.getString(materialKey, section.getString("materiau", "STONE"));
        ItemStack base = parse(Text.replace(material, kv));
        base.setAmount(Math.max(1, Math.min(64, section.getInt("quantite", 1))));
        String name = section.contains("nom") ? Text.replace(section.getString("nom"), kv) : null;
        List<String> lore = lore(stringList(section, "description"), kv);
        ItemStack item = build(base, name, lore, glow != null ? glow : section.getBoolean("brillant", false));
        String owner = section.getString("tete", "");
        if (!owner.isEmpty()) {
            setSkullOwner(item, Text.replace(owner, kv));
        }
        return item;
    }

    /**
     * Lignes de description : une variable dont la valeur est une liste produit une
     * ligne par élément (le texte placé avant la variable est répété), et une ligne
     * qui ne contient qu'une variable vide disparaît.
     */
    public static List<String> lore(List<String> template, Object... kv) {
        List<String> out = new ArrayList<String>();
        for (String line : template) {
            boolean expanded = false;
            for (int i = 0; i + 1 < kv.length; i += 2) {
                String key = "{" + kv[i] + "}";
                if (!line.contains(key)) {
                    continue;
                }
                Object value = kv[i + 1];
                if (value instanceof List) {
                    for (Object element : (List<?>) value) {
                        out.add(Text.replace(line.replace(key, String.valueOf(element)), kv));
                    }
                    expanded = true;
                    break;
                }
                if (line.trim().equals(key) && (value == null || String.valueOf(value).isEmpty())) {
                    expanded = true;
                    break;
                }
            }
            if (!expanded) {
                out.add(Text.replace(line, kv));
            }
        }
        return out;
    }

    /** Première lettre en majuscule. */
    public static String capitalize(String s) {
        return s == null || s.isEmpty() ? "" : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public static void setSkullOwner(ItemStack item, String owner) {
        if (item.getItemMeta() instanceof SkullMeta && owner != null && !owner.isEmpty()) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwner(owner);
            item.setItemMeta(meta);
        }
    }

    /** Lit une valeur de configuration qui peut être une chaîne ou une liste. */
    public static List<String> stringList(ConfigurationSection section, String path) {
        if (section == null || !section.contains(path)) {
            return Collections.emptyList();
        }
        if (section.isList(path)) {
            return section.getStringList(path);
        }
        String value = section.getString(path);
        return value == null ? Collections.<String>emptyList() : Collections.singletonList(value);
    }

    public static String toBase64(ItemStack[] contents) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes);
            out.writeInt(contents.length);
            for (ItemStack item : contents) {
                out.writeObject(item);
            }
            out.close();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de sérialiser l'inventaire", e);
        }
    }

    public static ItemStack[] fromBase64(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        try {
            BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)));
            int size = in.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) in.readObject();
            }
            in.close();
            return items;
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de lire l'inventaire", e);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Impossible de lire l'inventaire", e);
        }
    }
}
