package fr.koriamc.hdv.util;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Titres (avec durées) et barre d'action pour la 1.8.8, par réflexion afin de
 * rester compatible avec les forks de Spigot. En cas d'échec, on se rabat sur
 * l'API Bukkit.
 */
public final class Nms {

    private static boolean initialized;
    private static boolean available;

    private static Method getHandle;
    private static Field playerConnection;
    private static Method sendPacket;
    private static Method serialize;
    private static Constructor<?> chatPacket;
    private static Constructor<?> titlePacket;
    private static Constructor<?> timesPacket;
    private static Object actionTitle;
    private static Object actionSubtitle;

    private Nms() {
    }

    private static void init() {
        initialized = true;
        try {
            String pkg = Bukkit.getServer().getClass().getPackage().getName();
            String version = pkg.substring(pkg.lastIndexOf('.') + 1);
            String nms = "net.minecraft.server." + version + ".";
            Class<?> craftPlayer = Class.forName("org.bukkit.craftbukkit." + version + ".entity.CraftPlayer");
            Class<?> entityPlayer = Class.forName(nms + "EntityPlayer");
            Class<?> connection = Class.forName(nms + "PlayerConnection");
            Class<?> packet = Class.forName(nms + "Packet");
            Class<?> component = Class.forName(nms + "IChatBaseComponent");
            Class<?> serializer = Class.forName(nms + "IChatBaseComponent$ChatSerializer");
            Class<?> chat = Class.forName(nms + "PacketPlayOutChat");
            Class<?> title = Class.forName(nms + "PacketPlayOutTitle");
            Class<?> action = Class.forName(nms + "PacketPlayOutTitle$EnumTitleAction");

            getHandle = craftPlayer.getMethod("getHandle");
            playerConnection = entityPlayer.getField("playerConnection");
            sendPacket = connection.getMethod("sendPacket", packet);
            serialize = serializer.getMethod("a", String.class);
            chatPacket = chat.getConstructor(component, byte.class);
            titlePacket = title.getConstructor(action, component);
            timesPacket = title.getConstructor(int.class, int.class, int.class);
            actionTitle = action.getField("TITLE").get(null);
            actionSubtitle = action.getField("SUBTITLE").get(null);
            available = true;
        } catch (Throwable t) {
            available = false;
            Bukkit.getLogger().log(Level.WARNING, "[KoriaHdv] Titres/actionbar NMS indisponibles, utilisation de l'API Bukkit : " + t);
        }
    }

    private static Object component(String text) throws Exception {
        JsonObject json = new JsonObject();
        json.addProperty("text", text == null ? "" : text);
        return serialize.invoke(null, json.toString());
    }

    private static void send(Player player, Object packet) throws Exception {
        Object handle = getHandle.invoke(player);
        Object conn = playerConnection.get(handle);
        sendPacket.invoke(conn, packet);
    }

    public static void actionBar(Player player, String text) {
        if (!initialized) {
            init();
        }
        if (available) {
            try {
                send(player, chatPacket.newInstance(component(text), (byte) 2));
                return;
            } catch (Throwable ignored) {
                // repli ci-dessous
            }
        }
        player.sendMessage(text);
    }

    @SuppressWarnings("deprecation")
    public static void title(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (!initialized) {
            init();
        }
        if (available) {
            try {
                send(player, timesPacket.newInstance(fadeIn, stay, fadeOut));
                send(player, titlePacket.newInstance(actionSubtitle, component(subtitle == null ? "" : subtitle)));
                send(player, titlePacket.newInstance(actionTitle, component(title == null ? "" : title)));
                return;
            } catch (Throwable ignored) {
                // repli ci-dessous
            }
        }
        player.sendTitle(title == null ? "" : title, subtitle == null ? "" : subtitle);
    }
}
