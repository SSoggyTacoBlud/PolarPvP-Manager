package com.pvptoggle.manager;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import com.pvptoggle.PvPTogglePlugin;
import com.pvptoggle.model.PvPZone;

public class ZoneManager {

    private final PvPTogglePlugin plugin;
    private final Map<String, PvPZone> zones = new LinkedHashMap<>();      // key = lowercase name
    private final Map<UUID, Location[]> selections = new HashMap<>();      // [0]=pos1, [1]=pos2

    public ZoneManager(PvPTogglePlugin plugin) {
        this.plugin = plugin;
    }

    // set wand selection
    public void setPosition(UUID playerId, int positionIndex, Location loc) {
        selections.computeIfAbsent(playerId, k -> new Location[2])[positionIndex] = loc.clone();
    }

    // get wand selection
    public Location[] getSelection(UUID playerId) {
        return selections.get(playerId);
    }

    public boolean createZone(String name, UUID playerUUID) {
        Location[] selection = selections.get(playerUUID);
        if (selection == null || selection[0] == null || selection[1] == null) return false;
        World worldA = selection[0].getWorld();
        World worldB = selection[1].getWorld();
        if (worldA == null || worldB == null) return false;
        if (!worldA.getName().equals(worldB.getName())) return false;

        PvPZone zone = new PvPZone(name, worldA.getName(), new PvPZone.Corners(
                selection[0].getBlockX(), selection[0].getBlockY(), selection[0].getBlockZ(),
                selection[1].getBlockX(), selection[1].getBlockY(), selection[1].getBlockZ()));
        zones.put(name.toLowerCase(), zone);
        saveZones();
        return true;
    }

    public boolean deleteZone(String name) {
        if (zones.remove(name.toLowerCase()) != null) {
            saveZones();
            return true;
        }
        return false;
    }

    public PvPZone getZone(String name) {
        return zones.get(name.toLowerCase());
    }

    public Collection<PvPZone> getZones() {
        return Collections.unmodifiableCollection(zones.values());
    }

    public Set<String> getZoneNames() {
        return Collections.unmodifiableSet(zones.keySet());
    }

    public boolean isInForcedPvPZone(Location location) {
        if (location == null) return false;
        for (PvPZone zone : zones.values()) {
            if (zone.contains(location)) return true;
        }
        return false;
    }

    // zones.yml i/o

    public void loadZones() {
        File file = new File(plugin.getDataFolder(), "zones.yml");
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("zones");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection zoneSection = section.getConfigurationSection(key);
            if (zoneSection == null) continue;
            zones.put(key.toLowerCase(), new PvPZone(
                    zoneSection.getString("name", key),
                    zoneSection.getString("world", "world"),
                    new PvPZone.Corners(
                            zoneSection.getInt("x1"), zoneSection.getInt("y1"), zoneSection.getInt("z1"),
                            zoneSection.getInt("x2"), zoneSection.getInt("y2"), zoneSection.getInt("z2"))
            ));
        }
        plugin.getLogger().log(Level.INFO, "Loaded {0} PvP zone(s).", zones.size());
    }

    public void saveZones() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, PvPZone> entry : zones.entrySet()) {
            PvPZone zone = entry.getValue();
            String path = "zones." + entry.getKey();
            config.set(path + ".name",  zone.getName());
            config.set(path + ".world", zone.getWorldName());
            config.set(path + ".x1", zone.getX1());
            config.set(path + ".y1", zone.getY1());
            config.set(path + ".z1", zone.getZ1());
            config.set(path + ".x2", zone.getX2());
            config.set(path + ".y2", zone.getY2());
            config.set(path + ".z2", zone.getZ2());
        }
        try {
            config.save(new File(plugin.getDataFolder(), "zones.yml"));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save zones", e);
        }
    }
}
