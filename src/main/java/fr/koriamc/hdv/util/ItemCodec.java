package fr.koriamc.hdv.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Base64;
import java.util.Locale;

/**
 * Objets enregistrés en NBT (comme le jeu) plutôt qu'avec la sérialisation Bukkit : les
 * objets ajoutés par KoriaMC (koriamc:jade...) n'ont pas de nom Bukkit et ne survivraient
 * pas à celle-ci. Accès au serveur par réflexion.
 */
public final class ItemCodec {

    private static boolean initialized;
    private static boolean available;
    private static Method asNmsCopy;
    private static Method asBukkitCopy;
    private static Method save;
    private static Method createStack;
    private static Method getItem;
    private static Method writeCompressed;
    private static Method readCompressed;
    private static Class<?> compoundClass;
    private static Object registry;
    private static Method keyOf;

    private ItemCodec() {
    }

    private static void init() {
        initialized = true;
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            String nms = "net.minecraft.server." + version + ".";
            Class<?> craftStack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nmsStack = Class.forName(nms + "ItemStack");
            Class<?> item = Class.forName(nms + "Item");
            compoundClass = Class.forName(nms + "NBTTagCompound");
            Class<?> tools = Class.forName(nms + "NBTCompressedStreamTools");
            asNmsCopy = craftStack.getMethod("asNMSCopy", ItemStack.class);
            asBukkitCopy = craftStack.getMethod("asBukkitCopy", nmsStack);
            save = nmsStack.getMethod("save", compoundClass);
            createStack = nmsStack.getMethod("createStack", compoundClass);
            getItem = nmsStack.getMethod("getItem");
            writeCompressed = tools.getMethod("a", compoundClass, OutputStream.class);
            readCompressed = tools.getMethod("a", InputStream.class);
            Field field = item.getField("REGISTRY");
            registry = field.get(null);
            keyOf = registry.getClass().getMethod("c", Object.class);
            available = true;
        } catch (Throwable t) {
            available = false;
            Bukkit.getLogger().warning("[KoriaHdv] Accès NBT indisponible (" + t + ") : sérialisation Bukkit utilisée.");
        }
    }

    /** Objet encodé en texte (NBT compressé en base64, ou "bukkit:" + sérialisation Bukkit). */
    public static String encode(ItemStack stack) {
        if (!initialized) {
            init();
        }
        if (available) {
            try {
                Object nms = asNmsCopy.invoke(null, stack);
                Object compound = compoundClass.newInstance();
                save.invoke(nms, compound);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                writeCompressed.invoke(null, compound, out);
                return Base64.getEncoder().encodeToString(out.toByteArray());
            } catch (Throwable t) {
                throw new IllegalStateException("Objet impossible à enregistrer", t);
            }
        }
        return "bukkit:" + Items.toBase64(new ItemStack[]{stack});
    }

    /** @return l'objet, ou null si le texte est illisible */
    public static ItemStack decode(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        if (!initialized) {
            init();
        }
        try {
            if (data.startsWith("bukkit:")) {
                ItemStack[] items = Items.fromBase64(data.substring(7));
                return items.length == 0 ? null : items[0];
            }
            if (!available) {
                return null;
            }
            Object compound = readCompressed.invoke(null, new ByteArrayInputStream(Base64.getDecoder().decode(data)));
            Object nms = createStack.invoke(null, compound);
            return nms == null ? null : (ItemStack) asBukkitCopy.invoke(null, nms);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Identifiant du jeu (minecraft:diamond_sword, koriamc:jade), ou null. */
    public static String id(ItemStack stack) {
        if (stack == null || stack.getTypeId() == 0) {
            return null;
        }
        if (!initialized) {
            init();
        }
        if (available) {
            try {
                Object nms = asNmsCopy.invoke(null, stack);
                Object item = nms == null ? null : getItem.invoke(nms);
                Object key = item == null ? null : keyOf.invoke(registry, item);
                if (key != null) {
                    String id = key.toString();
                    return id.contains(":") ? id : "minecraft:" + id;
                }
            } catch (Throwable ignored) {
                // repli ci-dessous
            }
        }
        return stack.getType() == null ? "minecraft:stone" : "minecraft:" + stack.getType().name().toLowerCase(Locale.ROOT);
    }

    /** Nom affiché : nom personnalisé, sinon tiré de l'identifiant (koriamc:korium_ingot -> Korium ingot). */
    public static String name(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        ItemMeta meta = stack.hasItemMeta() ? stack.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        String id = id(stack);
        if (id == null) {
            return "";
        }
        String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
        return Items.capitalize(path);
    }
}
