package fr.koriamc.hdv.util;

import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * Lecture de sons configurables au format "NOM", "NOM:volume" ou "NOM:volume:pitch".
 */
public final class Sounds {

    private Sounds() {
    }

    public static void play(Player player, String spec) {
        if (player == null || spec == null || spec.trim().isEmpty()) {
            return;
        }
        String[] parts = spec.trim().split(":");
        Sound sound;
        try {
            sound = Sound.valueOf(parts[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return;
        }
        float volume = 1f;
        float pitch = 1f;
        if (parts.length > 1) {
            Double v = Text.parseDouble(parts[1]);
            if (v != null) {
                volume = v.floatValue();
            }
        }
        if (parts.length > 2) {
            Double p = Text.parseDouble(parts[2]);
            if (p != null) {
                pitch = p.floatValue();
            }
        }
        player.playSound(player.getLocation(), sound, volume, pitch);
    }
}
