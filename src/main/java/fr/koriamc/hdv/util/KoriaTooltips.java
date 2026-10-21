package fr.koriamc.hdv.util;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Infobulle KoriaMC d'un objet (tag NBT "KoriaTooltip" lu par le client) : titre et lignes en texte riche
 * (&amp;couleurs, &lt;img:...&gt;, &lt;item:...&gt;, &lt;head:...&gt;, calculs {=...} comme un compte à rebours).
 * Sans le serveur KoriaMC (NMS introuvable), l'objet est rendu tel quel.
 */
public final class KoriaTooltips {

    private static boolean ready;
    private static Method asNmsCopy;
    private static Method asBukkitCopy;
    private static Method hasTag;
    private static Method getTag;
    private static Method setTag;
    private static Method compoundSet;
    private static Method compoundSetString;
    private static Method compoundSetByte;
    private static Method listAdd;
    private static Constructor<?> newCompound;
    private static Constructor<?> newList;
    private static Constructor<?> newString;

    static {
        try {
            String version = Bukkit.getServer().getClass().getPackage().getName().split("\\.")[3];
            String nms = "net.minecraft.server." + version + ".";
            Class<?> craftStack = Class.forName("org.bukkit.craftbukkit." + version + ".inventory.CraftItemStack");
            Class<?> nmsStack = Class.forName(nms + "ItemStack");
            Class<?> compound = Class.forName(nms + "NBTTagCompound");
            Class<?> base = Class.forName(nms + "NBTBase");
            Class<?> list = Class.forName(nms + "NBTTagList");
            Class<?> string = Class.forName(nms + "NBTTagString");
            asNmsCopy = craftStack.getMethod("asNMSCopy", ItemStack.class);
            asBukkitCopy = craftStack.getMethod("asBukkitCopy", nmsStack);
            hasTag = nmsStack.getMethod("hasTag");
            getTag = nmsStack.getMethod("getTag");
            setTag = nmsStack.getMethod("setTag", compound);
            compoundSet = compound.getMethod("set", String.class, base);
            compoundSetString = compound.getMethod("setString", String.class, String.class);
            compoundSetByte = compound.getMethod("setByte", String.class, byte.class);
            listAdd = list.getMethod("add", base);
            newCompound = compound.getConstructor();
            newList = list.getConstructor();
            newString = string.getConstructor(String.class);
            ready = true;
        } catch (Throwable t) {
            ready = false;
        }
    }

    private KoriaTooltips() {
    }

    /**
     * Copie de l'objet avec une infobulle KoriaMC.
     *
     * @param title   titre (null : nom de l'objet)
     * @param lines   lignes sous le titre
     * @param replace true : seulement le titre et ces lignes (sans enchantements, dégâts, durabilité ni description)
     */
    public static ItemStack with(ItemStack item, String title, List<String> lines, boolean replace) {
        if (!ready || item == null) {
            return item;
        }
        try {
            Object stack = asNmsCopy.invoke(null, item);
            if (stack == null) {
                return item;
            }
            Object tag = Boolean.TRUE.equals(hasTag.invoke(stack)) ? getTag.invoke(stack) : newCompound.newInstance();
            Object custom = newCompound.newInstance();
            if (title != null) {
                compoundSetString.invoke(custom, "Title", title);
            }
            Object list = newList.newInstance();
            for (String line : lines) {
                listAdd.invoke(list, newString.newInstance(line));
            }
            compoundSet.invoke(custom, "Lines", list);
            compoundSetByte.invoke(custom, "Replace", (byte) (replace ? 1 : 0));
            compoundSet.invoke(tag, "KoriaTooltip", custom);
            setTag.invoke(stack, tag);
            return (ItemStack) asBukkitCopy.invoke(null, stack);
        } catch (Throwable t) {
            return item;
        }
    }
}
