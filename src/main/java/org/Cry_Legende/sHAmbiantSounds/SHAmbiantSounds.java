package org.Cry_Legende.sHAmbiantSounds;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class SHAmbiantSounds extends JavaPlugin {

    // Le monde est résolu à chaque lecture : il peut être chargé après onEnable (PhantomWorlds)
    private record AmbiantSound(Sound sound, float pitch, float volume, String worldName, double x, double y, double z, String replayEvery) {
        public void play() {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.playSound(new Location(world, x, y, z), sound, volume, pitch);
            }
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadAndScheduleSounds();
        getLogger().info("SHAmbiantSounds a été activé ! Les sons vont commencer.");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        getLogger().info("SHAmbiantSounds a été désactivé !");
    }

    private void loadAndScheduleSounds() {
        ConfigurationSection soundsSection = getConfig().getConfigurationSection("sounds");
        if (soundsSection == null) {
            getLogger().warning("La section 'sounds' est introuvable dans config.yml !");
            return;
        }

        for (String key : soundsSection.getKeys(false)) {
            ConfigurationSection soundConfig = soundsSection.getConfigurationSection(key);
            if (soundConfig == null) continue;

            String soundName = soundConfig.getString("sound");
            String worldName = soundConfig.getString("world");
            String positionStr = soundConfig.getString("position");
            String replayEvery = soundConfig.getString("replayEvery");

            if (soundName == null || worldName == null || positionStr == null || replayEvery == null) {
                getLogger().warning("Configuration invalide pour '" + key + "'. Paramètres manquants.");
                continue;
            }

            Sound sound = sound(soundName);
            if (sound == null) {
                getLogger().warning("Son '" + soundName + "' invalide pour '" + key + "'.");
                continue;
            }

            try {
                String[] posParts = positionStr.split("/");
                double x = Double.parseDouble(posParts[0]);
                double y = Double.parseDouble(posParts[1]);
                double z = Double.parseDouble(posParts[2]);
                float pitch = (float) soundConfig.getDouble("pitch", 1.0);
                float volume = (float) soundConfig.getDouble("volume", 1.0);

                AmbiantSound ambiantSound = new AmbiantSound(sound, pitch, volume, worldName, x, y, z, replayEvery);
                playAndScheduleNext(ambiantSound);

            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                getLogger().warning("Coordonnées de position invalides pour le son '" + key + "'.");
            }
        }
    }

    private void playAndScheduleNext(final AmbiantSound soundData) {
        soundData.play();
        long delay = getNextDelayInTicks(soundData.replayEvery());
        new BukkitRunnable() {
            @Override
            public void run() {
                playAndScheduleNext(soundData);
            }
        }.runTaskLater(this, delay);
    }

    /** Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup). */
    static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUND_EVENT.get(key);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    /**
     * Lit la configuration pour 'replayEvery' et la retourne directement en ticks.
     */
    private long getNextDelayInTicks(String replayEvery) {
        try {
            if (replayEvery.contains("-")) {
                String[] parts = replayEvery.split("-");
                int min = Integer.parseInt(parts[0]);
                int max = Integer.parseInt(parts[1]);
                if (min > max) {
                    int temp = min;
                    min = max;
                    max = temp;
                }
                // CHANGEMENT : La multiplication par 20L est supprimée
                return ThreadLocalRandom.current().nextInt(min, max + 1);
            } else {
                // CHANGEMENT : La multiplication par 20L est supprimée
                return Long.parseLong(replayEvery);
            }
        } catch (Exception e) {
            // Le message d'erreur est mis à jour pour mentionner les ticks.
            getLogger().log(Level.WARNING, "Format 'replayEvery' invalide : " + replayEvery + ". Utilisation d'un délai par défaut de 200 ticks.", e);
            return 200L; // Délai par défaut de 200 ticks (10 secondes)
        }
    }
}