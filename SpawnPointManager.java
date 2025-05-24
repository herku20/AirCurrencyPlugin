// SpawnPointManager.java - HOZZÁADVA: amount mező mentése/betöltése a fájlba
package me.herku.aircurrency;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpawnPointManager {

    private final AirCurrencyPlugin plugin;
    // Map to store active spawn points (UUID -> SpawnPoint object)
    private final Map<UUID, SpawnPoint> activeSpawnPoints = new ConcurrentHashMap<>();

    private File dataFile;
    private YamlConfiguration yamlConfig;

    public SpawnPointManager(AirCurrencyPlugin plugin) {
        this.plugin = plugin;
        setupFile();
        loadSpawnPoints(); // Load existing points on plugin enable
    }

    // --- File Handling ---
    private void setupFile() {
        dataFile = new File(plugin.getDataFolder(), "spawnpoints.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
                plugin.getLogger().info("Created spawnpoints.yml");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create spawnpoints.yml!");
                e.printStackTrace();
            }
        }
        yamlConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void loadSpawnPoints() {
        activeSpawnPoints.clear(); // Clear current active points before loading

        yamlConfig = YamlConfiguration.loadConfiguration(dataFile); // Reload from disk

        if (yamlConfig.contains("spawnpoints")) {
            org.bukkit.configuration.ConfigurationSection spawnPointsSection = yamlConfig.getConfigurationSection("spawnpoints");

            if (spawnPointsSection != null) {
                for (String idString : spawnPointsSection.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(idString);
                        org.bukkit.configuration.ConfigurationSection pointSection = spawnPointsSection.getConfigurationSection(idString);

                        if (pointSection != null) {
                            Location location = (Location) pointSection.get("location");
                            String itemTypeId = pointSection.getString("itemType", "GOLD_INGOT");
                            int respawnTime = pointSection.getInt("respawnTime", 300);
                            String message = pointSection.getString("message", ""); // Load message
                            double amount = pointSection.getDouble("amount", 0.0); // <-- ÚJ: amount betöltése

                            if (location != null) {
                                // NEW: Pass amount to SpawnPoint constructor
                                SpawnPoint spawnPoint = new SpawnPoint(id, location, itemTypeId, respawnTime, message, amount); // <-- MÓDOSÍTVA
                                activeSpawnPoints.put(id, spawnPoint);
                                // Log message updated to include amount
                                plugin.getLogger().fine("Loaded SpawnPoint: " + id + " at " + formatLocation(location) + " Type: " + itemTypeId + " Respawn: " + respawnTime + "s Amount: " + amount + " Message: '" + message + "'");
                            } else {
                                plugin.getLogger().warning("Skipped loading SpawnPoint " + id + ": Null location in file.");
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Skipped loading spawn point with invalid UUID format: " + idString);
                    }
                }
            }
        }
        plugin.getLogger().info("Loaded " + activeSpawnPoints.size() + " spawn points from spawnpoints.yml");
    }

    public void saveSpawnPoints() {
        yamlConfig.set("spawnpoints", null);

        org.bukkit.configuration.ConfigurationSection spawnPointsSection = yamlConfig.createSection("spawnpoints");

        for (SpawnPoint spawnPoint : activeSpawnPoints.values()) {
            org.bukkit.configuration.ConfigurationSection pointSection = spawnPointsSection.createSection(spawnPoint.getId().toString());

            pointSection.set("location", spawnPoint.getLocation());
            pointSection.set("itemType", spawnPoint.getItemTypeId());
            pointSection.set("respawnTime", spawnPoint.getRespawnTime());
            pointSection.set("message", spawnPoint.getMessage());
            pointSection.set("amount", spawnPoint.getAmount()); // <-- ÚJ: amount mentése
        }

        try {
            yamlConfig.save(dataFile);
            plugin.getLogger().fine("Saved " + activeSpawnPoints.size() + " spawn points to spawnpoints.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save spawnpoints.yml!");
            e.printStackTrace();
        }
    }

    // --- Spawn Point Management ---
    public void addSpawnPoint(SpawnPoint spawnPoint) {
        if (spawnPoint == null || activeSpawnPoints.containsKey(spawnPoint.getId())) {
            return;
        }
        activeSpawnPoints.put(spawnPoint.getId(), spawnPoint);
        saveSpawnPoints();
    }

    public void removeSpawnPoint(UUID id) {
        SpawnPoint removedPoint = activeSpawnPoints.remove(id);

        if (removedPoint != null) {
            plugin.getFloatingItemManager().despawnItemForSpawnPoint(id);
            plugin.getFloatingItemManager().cancelRespawnTask(id);
            saveSpawnPoints();
            plugin.getLogger().info("Removed SpawnPoint: " + id + " at " + formatLocation(removedPoint.getLocation()));
        }
    }

    public SpawnPoint getSpawnPoint(UUID id) {
        return activeSpawnPoints.get(id);
    }

    public Collection<SpawnPoint> getAllSpawnPoints() {
        return activeSpawnPoints.values();
    }

    /**
     * Gets all active spawn points located within a specific chunk.
     *
     * @param chunk The chunk to check.
     * @return A collection of SpawnPoint objects in the specified chunk.
     */
    public Collection<SpawnPoint> getSpawnPointsInChunk(Chunk chunk) {
        Collection<SpawnPoint> pointsInChunk = new java.util.ArrayList<>();
        if (chunk == null) return pointsInChunk;

        for (SpawnPoint point : activeSpawnPoints.values()) {
            Location loc = point.getLocation();
            if (loc != null && loc.getWorld() != null && loc.getWorld().equals(chunk.getWorld()) && loc.getChunk().equals(chunk)) {
                pointsInChunk.add(point);
            }
        }
        return pointsInChunk;
    }

    /**
     * Gets all SpawnPoint UUIDs stored in the spawnpoints.yml file.
     * Does NOT load the full SpawnPoint objects into memory if not already loaded.
     * Useful for checking points that are in the file but not currently active/loaded.
     *
     * @return A collection of UUIDs from the file, or empty collection if none.
     */
    public Collection<UUID> getAllSpawnPointIdsFromFile() {
        Collection<UUID> idsFromFile = new java.util.ArrayList<>();
        yamlConfig = YamlConfiguration.loadConfiguration(dataFile); // Reload from disk
        if (yamlConfig.contains("spawnpoints")) {
            org.bukkit.configuration.ConfigurationSection spawnPointsSection = yamlConfig.getConfigurationSection("spawnpoints");
            if (spawnPointsSection != null) {
                for (String idString : spawnPointsSection.getKeys(false)) {
                    try {
                        idsFromFile.add(UUID.fromString(idString));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID format in spawnpoints.yml: " + idString);
                    }
                }
            }
        }
        return idsFromFile;
    }

    /**
     * Attempts to load a single SpawnPoint from the file by its UUID.
     * If successful, it adds it to the activeSpawnPoints map IF its world is loaded.
     *
     * @param id The UUID of the SpawnPoint to load.
     * @return The loaded and activated SpawnPoint, or null if not found, data invalid, or world not loaded.
     */
    public SpawnPoint attemptLoadSingleSpawnPoint(UUID id) {
        if (id == null) return null;
        if (activeSpawnPoints.containsKey(id)) {
            return activeSpawnPoints.get(id);
        }

        yamlConfig = YamlConfiguration.loadConfiguration(dataFile); // Reload from disk

        if (yamlConfig.contains("spawnpoints." + id.toString())) {
            org.bukkit.configuration.ConfigurationSection pointSection = yamlConfig.getConfigurationSection("spawnpoints." + id.toString());
            if (pointSection != null) {
                Location location = (Location) pointSection.get("location");
                String itemTypeId = pointSection.getString("itemType", "GOLD_INGOT");
                int respawnTime = pointSection.getInt("respawnTime", 300);
                String message = pointSection.getString("message", "");
                double amount = pointSection.getDouble("amount", 0.0); // <-- ÚJ: amount betöltése

                if (location != null && location.getWorld() != null) { // Check if location and its world are valid/loaded
                    // Pass amount to constructor
                    SpawnPoint spawnPoint = new SpawnPoint(id, location, itemTypeId, respawnTime, message, amount); // <-- MÓDOSÍTVA
                    activeSpawnPoints.put(id, spawnPoint); // Add to active map
                    plugin.getLogger().fine("Successfully loaded and activated SpawnPoint " + id + " from file.");
                    return spawnPoint;
                } else {
                    plugin.getLogger().fine("Cannot activate SpawnPoint " + id + " from file: Location null or world not loaded.");
                    return null; // Cannot activate if world not loaded
                }
            }
        }
        plugin.getLogger().fine("SpawnPoint " + id + " not found in spawnpoints.yml.");
        return null; // Not found or invalid data
    }

    /**
     * Finds the nearest active SpawnPoint to a given location within a specified radius.
     * Used for the /aircurrency remove command.
     *
     * @param location The location to search around (typically player location).
     * @param radius   The maximum distance to search (in blocks).
     * @return The nearest SpawnPoint found within the radius, or null if none found.
     */
    public SpawnPoint findNearestSpawnPoint(Location location, double radius) {
        if (location == null || location.getWorld() == null) return null;

        SpawnPoint nearestPoint = null;
        double minDistanceSq = radius * radius;

        for (SpawnPoint point : activeSpawnPoints.values()) {
            Location pointLocation = point.getLocation();
            if (pointLocation != null && pointLocation.getWorld() != null && pointLocation.getWorld().equals(location.getWorld())) {
                double distanceSq = location.distanceSquared(pointLocation);
                if (distanceSq <= minDistanceSq) {
                    minDistanceSq = distanceSq;
                    nearestPoint = point;
                }
            }
        }
        return nearestPoint;
    }


    // Helper method for logging location (could be moved to a utility class later)
    private String formatLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return String.format("%s(%.2f, %.2f, %.2f)", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    /**
     * Helper to get block under player location for /aircurrency set.
     *
     * @param playerLocation The player's current location.
     * @return The Location of the block directly below the player.
     */
    public Location getBlockUnderPlayerLocation(Location playerLocation) {
        if (playerLocation == null || playerLocation.getWorld() == null) return null;
        return playerLocation.getBlock().getRelative(BlockFace.DOWN).getLocation();
    }


    // --- Cleanup on Plugin Disable ---
    public void onPluginDisable() {
        saveSpawnPoints();
        plugin.getLogger().info("SpawnPointManager cleanup complete.");
    }
}
// --- KÓD VÉGE ---