// FloatingItemManager.java - Legutolsó verzió, Csökkentett Logolással
package me.herku.aircurrency;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class FloatingItemManager {

    private final AirCurrencyPlugin plugin;
    // Map SpawnPoint UUID -> Entity UUID to track which spawn points currently HAVE a spawned entity
    private final Map<UUID, UUID> spawnedItemsMap = new HashMap<>();

    // Map SpawnPoint UUID -> BukkitTask for respawn timers
    private final Map<UUID, BukkitTask> respawnTasks = new HashMap<>();

    // NamespacedKey to mark our items with PersistentData for identification
    private final org.bukkit.NamespacedKey airCurrencyKey;

    public FloatingItemManager(AirCurrencyPlugin plugin) {
        this.plugin = plugin;
        this.airCurrencyKey = new org.bukkit.NamespacedKey(plugin, "aircurrency_item");
    }

    /**
     * Spawns an item entity for a given SpawnPoint if its chunk is loaded and no item is already tracked for it.
     * Applies static properties with a slight delay.
     *
     * @param spawnPoint The SpawnPoint to spawn the item for.
     * @return The spawned Item entity, or null if spawn failed (e.g., chunk not loaded, invalid item, already tracked).
     */
    public Item spawnItem(SpawnPoint spawnPoint) {
        if (spawnPoint == null) return null;
        UUID spawnPointId = spawnPoint.getId();

        // --- LOGOLÁS (Csökkentett) ---
        plugin.getLogger().fine("spawnItem: Attempting spawn for SpawnPoint " + spawnPointId + ". spawnedItemsMap contains key? " + spawnedItemsMap.containsKey(spawnPointId)); // Changed from INFO to FINE
        // -------------

        if (spawnedItemsMap.containsKey(spawnPointId)) {
            plugin.getLogger().fine("Attempted to spawn item for SpawnPoint " + spawnPointId + " but one is already tracked in spawnedItemsMap. Skipping spawn.");
            return null;
        }


        Location location = spawnPoint.getLocation();
        if (location == null) {
            plugin.getLogger().warning("Attempted to spawn item for SpawnPoint " + spawnPointId + " with null location.");
            return null;
        }

        World world = location.getWorld();
        if (world == null) {
            plugin.getLogger().warning("Attempted to spawn item for SpawnPoint " + spawnPointId + " in a world that is somehow not loaded: " + location.getWorld().getName());
            return null;
        }

        Chunk chunk = location.getChunk();
        if (!chunk.isLoaded()) {
            plugin.getLogger().fine("Attempted to spawn item for SpawnPoint " + spawnPointId + ", but chunk is not loaded. Skipping spawn for now.");
            return null;
        }

        final double HOVER_HEIGHT_ABOVE_TOP = 0.2;
        final Location spawnLocation = location.clone().add(0.5, 1.0 + HOVER_HEIGHT_ABOVE_TOP, 0.5);

        Material itemMaterial = Material.matchMaterial(spawnPoint.getItemTypeId());
        if (itemMaterial == null || !itemMaterial.isItem()) {
            plugin.getLogger().warning("Invalid item ID '" + spawnPoint.getItemTypeId() + "' for SpawnPoint " + spawnPointId + ". Skipping spawn.");
            return null;
        }
        ItemStack itemStack = new ItemStack(itemMaterial);


        Item floatingItem = world.dropItemNaturally(spawnLocation, itemStack);


        PersistentDataContainer container = floatingItem.getPersistentDataContainer();
        container.set(airCurrencyKey, PersistentDataType.STRING, spawnPointId.toString());
        spawnedItemsMap.put(spawnPointId, floatingItem.getUniqueId());
        // --- LOGOLÁS (Csökkentett) ---
        plugin.getLogger().fine("spawnItem: Successfully added SpawnPoint " + spawnPointId + " with Entity " + floatingItem.getUniqueId() + " to spawnedItemsMap."); // Changed from INFO to FINE
        // -------------
        plugin.getLogger().fine("Spawned item for SpawnPoint " + spawnPointId + " at " + formatLocation(spawnLocation) + " Entity UUID: " + floatingItem.getUniqueId());


        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Entity entityCheck = plugin.getServer().getEntity(floatingItem.getUniqueId());
            if (entityCheck instanceof Item) {
                Item validItem = (Item) entityCheck;
                validItem.setGravity(false);
                validItem.setVelocity(new Vector(0, 0, 0));
                validItem.setUnlimitedLifetime(true);
                validItem.setInvulnerable(true);
                validItem.setPickupDelay(0);
                validItem.setPersistent(true);

                plugin.getLogger().fine("Applied scheduled static properties to item entity: " + validItem.getUniqueId());

                validItem.teleport(spawnLocation);
            } else {
                plugin.getLogger().warning("Scheduled task failed to apply static properties to item entity " + floatingItem.getUniqueId() + " for SpawnPoint " + spawnPointId + ". Entity not found or invalid.");
            }
        }, 1L);


        return floatingItem;
    }

    /**
     * Despawns a specific floating item entity by its Entity UUID.
     * Removes from spawnedItemsMap and the world. Called on pickup or by despawnAllItems.
     *
     * @param entityUUID The UUID of the entity to despawn.
     * @return true if the entity was found (in map values) and removed, false otherwise.
     */
    public boolean despawnItem(UUID entityUUID) {
        if (entityUUID == null) return false;

        UUID spawnPointId = null;
        Iterator<Map.Entry<UUID, UUID>> iterator = spawnedItemsMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            if (entry.getValue().equals(entityUUID)) {
                spawnPointId = entry.getKey();
                iterator.remove();
                plugin.getLogger().fine("Removed entity " + entityUUID + " for SpawnPoint " + spawnPointId + " from spawnedItemsMap.");
                break;
            }
        }

        Entity entity = plugin.getServer().getEntity(entityUUID);
        boolean entityRemoved = false;
        if (entity != null && entity instanceof Item) {
            if (entity.getPersistentDataContainer().has(airCurrencyKey, PersistentDataType.STRING)) {
                entity.remove();
                entityRemoved = true;
                plugin.getLogger().fine("Despawned AirCurrency item entity from world: " + entityUUID);
            } else if (spawnPointId != null) {
                plugin.getLogger().warning("Tracked entity UUID " + entityUUID + " found in spawnedItemsMap for SpawnPoint " + spawnPointId + " but missing AirCurrency PersistentData in world! Removing entity anyway.");
                entity.remove();
                entityRemoved = true;
            }
        } else if (spawnPointId != null) {
            plugin.getLogger().warning("Tracked entity UUID " + entityUUID + " found in spawnedItemsMap for SpawnPoint " + spawnPointId + " but entity not found in world or not an Item!");
        }

        return spawnPointId != null || entityRemoved;
    }

    /**
     * Despawns the active floating item entity associated with a given SpawnPoint UUID.
     * Removes from spawnedItemsMap and the world. Called by /remove.
     *
     * @param spawnPointId The UUID of the SpawnPoint.
     * @return true if an item for this SpawnPoint was found (in map key) and despawned, false otherwise.
     */
    public boolean despawnItemForSpawnPoint(UUID spawnPointId) {
        if (spawnPointId == null) return false;

        UUID entityUUID = spawnedItemsMap.remove(spawnPointId);
        boolean wasTracked = entityUUID != null;
        if (wasTracked) {
            plugin.getLogger().fine("Removed SpawnPoint " + spawnPointId + " from spawnedItemsMap.");
        } else {
            plugin.getLogger().fine("Attempted to remove SpawnPoint " + spawnPointId + " from spawnedItemsMap but it was not found.");
        }


        boolean entityRemoved = false;
        if (entityUUID != null) {
            Entity entity = plugin.getServer().getEntity(entityUUID);
            if (entity != null && entity instanceof Item && entity.getPersistentDataContainer().has(airCurrencyKey, PersistentDataType.STRING)) {
                entity.remove();
                entityRemoved = true;
                plugin.getLogger().fine("Despawned AirCurrency item entity from world for SpawnPoint " + spawnPointId + ": " + entityUUID);
            } else {
    //DisabledLog            plugin.getLogger().warning("Tracked entity UUID " + entityUUID + " found in spawnedItemsMap for SpawnPoint " + spawnPointId + " but entity not found in world or not ours!");
            }
        }

        return wasTracked || entityRemoved;
    }

    /**
     * Despawns all currently active floating item entities managed by the plugin.
     * Removes from spawnedItemsMap and the world. Called on plugin disable or by /aircurrency removeall.
     */
    public void despawnAllItems() {
    //DisabledLog    plugin.getLogger().info("Despawning all active AirCurrency items...");
        new java.util.ArrayList<>(spawnedItemsMap.keySet()).forEach(this::despawnItemForSpawnPoint);

        spawnedItemsMap.clear();
    //DisabledLog    plugin.getLogger().info("Despawned all active AirCurrency items.");
    }

    /**
     * Checks if a given entity UUID is one of the plugin's managed floating items
     * based on the entity's PersistentDataContainer. (Primary check)
     * Also cross-references with the internal spawnedItemsMap for robustness.
     *
     * @param entityUUID The UUID of the entity to check.
     * @return true if it's a managed item, false otherwise.
     */
    public boolean isAirCurrencyItem(UUID entityUUID) {
        if (entityUUID == null) return false;

        Entity entity = plugin.getServer().getEntity(entityUUID);
        if (entity != null && entity instanceof Item) {
            PersistentDataContainer container = entity.getPersistentDataContainer();
            if (container.has(airCurrencyKey, PersistentDataType.STRING)) {
                try {
                    UUID spawnPointIdFromEntity = UUID.fromString(container.get(airCurrencyKey, PersistentDataType.STRING));
                    if (!spawnedItemsMap.containsKey(spawnPointIdFromEntity) || !spawnedItemsMap.get(spawnPointIdFromEntity).equals(entityUUID)) {
                        plugin.getLogger().fine("Found AirCurrency item entity " + entityUUID + " with marker, but not correctly tracked in spawnedItemsMap! Map state needs review. SpawnPoint ID: " + spawnPointIdFromEntity);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID format stored in PersistentData for entity " + entity.getUniqueId() + ". Data corrupted?");
                }
                return true;
            }
        }
        return false;
    }


    /**
     * Gets the SpawnPoint associated with a given active entity UUID.
     * Used by the PickupListener. Uses PersistentData for robustness.
     *
     * @param entityUUID The UUID of the entity.
     * @return The associated SpawnPoint, or null if not found or spawn point doesn't exist in SpawnPointManager.
     */
    public SpawnPoint getSpawnPointForEntity(UUID entityUUID) {
        if (entityUUID == null) return null;

        Entity entity = plugin.getServer().getEntity(entityUUID);
        if (entity != null && entity instanceof Item && entity.getPersistentDataContainer().has(airCurrencyKey, PersistentDataType.STRING)) {
            try {
                UUID spawnPointId = UUID.fromString(entity.getPersistentDataContainer().get(airCurrencyKey, PersistentDataType.STRING));
                SpawnPoint point = plugin.getSpawnPointManager().getSpawnPoint(spawnPointId);
                if (point == null) {
                    plugin.getLogger().warning("Found AirCurrency item entity " + entityUUID + " with marker for SpawnPoint " + spawnPointId + ", but SpawnPoint is not active in manager! Map state may be inconsistent.");
                }
                return point;

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID format stored in PersistentData for entity " + entityUUID + ". Data corrupted?");
                return null;
            }
        }
        return null;
    }


    // --- Respawn Scheduling ---

    /**
     * Schedules the respawning of an item for a SpawnPoint after its respawn time.
     * Cancels any existing task for this point.
     *
     * @param spawnPoint The SpawnPoint.
     */
    public void scheduleRespawn(SpawnPoint spawnPoint) {
        if (spawnPoint == null) return;
        UUID spawnPointId = spawnPoint.getId();

        cancelRespawnTask(spawnPointId);

        int respawnSeconds = spawnPoint.getRespawnTime();
        int delayTicks = respawnSeconds * 20;

        if (delayTicks <= 0) {
            plugin.getLogger().fine("Respawn time is non-positive (" + respawnSeconds + "s) for " + spawnPointId + ". Attempting immediate spawn.");
            spawnItem(spawnPoint);
            return;
        }

        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            plugin.getLogger().fine("Respawn task running for SpawnPoint " + spawnPointId);

            SpawnPoint pointToSpawn = plugin.getSpawnPointManager().getSpawnPoint(spawnPointId);

            if (pointToSpawn != null) {
                Item spawnedEntity = spawnItem(pointToSpawn);
                if (spawnedEntity != null) {
                    plugin.getLogger().fine("Successfully respawned item for " + pointToSpawn.getId());
                } else {
                    plugin.getLogger().fine("Respawn task failed to spawn item for " + pointToSpawn.getId() + ". Reason: spawnItem returned null (e.g. chunk not loaded or item already tracked?). ChunkListener may pick it up.");
                }
            } else {
                plugin.getLogger().warning("Respawn task ran for non-existent SpawnPoint: " + spawnPointId + ". Task cancelled.");
            }

            respawnTasks.remove(spawnPointId);

        }, delayTicks);

        respawnTasks.put(spawnPointId, task);
        plugin.getLogger().fine("Scheduled respawn for SpawnPoint " + spawnPointId + " in " + respawnSeconds + " seconds (Ticks: " + delayTicks + ").");
    }

    /**
     * Cancels the scheduled respawn task for a SpawnPoint.
     * Called when a spawn point is removed or plugin disables.
     *
     * @param spawnPointId The UUID of the SpawnPoint.
     */
    public void cancelRespawnTask(UUID spawnPointId) {
        if (spawnPointId == null) return;
        BukkitTask task = respawnTasks.remove(spawnPointId);
        if (task != null && !task.isCancelled()) {
            task.cancel();
            plugin.getLogger().fine("Cancelled respawn task for SpawnPoint " + spawnPointId);
        } else if (task != null) {
            plugin.getLogger().fine("Respawn task for SpawnPoint " + spawnPointId + " was already cancelled or finished.");
        }
    }

    /**
     * Cancels all currently scheduled respawn tasks.
     * Called on plugin disable. The removeall command does NOT call this.
     */
    public void cancelAllRespawnTasks() {
    //DisabledLog    plugin.getLogger().info("Cancelling all scheduled respawn tasks...");
        new java.util.ArrayList<>(respawnTasks.keySet()).forEach(this::cancelRespawnTask);
        respawnTasks.clear();
    //DisabledLog    plugin.getLogger().info("Cancelled all scheduled respawn tasks.");
    }


    // --- Chunk Loading / Unloading Handling ---

    /**
     * Handles chunk loading - attempts spawning items for points in this chunk.
     * Called by ChunkListener when a chunk loads.
     * Attempts to spawn items for any *active* spawn points in this chunk if they
     * are not already tracked as spawned. Also attempts to load points from file
     * that are in this chunk if they are not already active/tracked.
     *
     * @param chunk The loaded chunk.
     */
    public void handleChunkLoad(Chunk chunk) {
        if (chunk == null) return;
        // --- LOGOLÁS (Csökkentett) ---
        plugin.getLogger().fine("handleChunkLoad: Chunk " + chunk.getX() + "," + chunk.getZ() + " in world " + chunk.getWorld().getName() + " loaded. Spawned items map size: " + spawnedItemsMap.size());
        // -------------

        // 1. Check for SpawnPoints in this chunk that are currently ACTIVE in memory (SpawnPointManager)
        Collection<SpawnPoint> activePointsInChunk = plugin.getSpawnPointManager().getSpawnPointsInChunk(chunk);

        for (SpawnPoint point : activePointsInChunk) {
            UUID pointId = point.getId();

            // --- LOGOLÁS (Csökkentett) ---
            plugin.getLogger().fine("handleChunkLoad: Checking active SpawnPoint " + pointId + " in chunk " + chunk.getX() + "," + chunk.getZ() + ". spawnedItemsMap contains key? " + spawnedItemsMap.containsKey(pointId));
            // -------------

            if (spawnedItemsMap.containsKey(pointId)) {
                UUID entityId = spawnedItemsMap.get(pointId);
                Entity entity = plugin.getServer().getEntity(entityId);

                if (entity != null && entity instanceof Item && entity.getPersistentDataContainer().has(airCurrencyKey, PersistentDataType.STRING)) {
                    plugin.getLogger().fine("Found tracked and existing item entity " + entityId + " for active SpawnPoint " + pointId + " in loaded chunk.");
                } else {
                    plugin.getLogger().warning("SpawnedItemsMap thinks item entity " + entityId + " exists for active SpawnPoint " + pointId + " in loaded chunk, but entity is missing or invalid! Removing from map and attempting to spawn new item.");
                    spawnedItemsMap.remove(pointId);
                    spawnItem(point);
                }

            } else {
                Item existingItem = findAirCurrencyItemBySpawnPointId(pointId);

                if (existingItem == null) {
                    plugin.getLogger().fine("SpawnedItemsMap does not track item for active SpawnPoint " + pointId + " in loaded chunk, AND no existing entity found in world. Attempting to spawn item.");
                    spawnItem(point);
                } else {
    //DisabledLog                plugin.getLogger().warning("SpawnedItemsMap does not track item for active SpawnPoint " + pointId + " in loaded chunk, BUT found an existing entity in world: " + existingItem.getUniqueId() + ". Adding to tracking map instead of spawning new.");
                    spawnedItemsMap.put(pointId, existingItem.getUniqueId());
                }
            }
        }

        // 2. Check for SpawnPoints in the file...
        Collection<UUID> allFilePointIds = plugin.getSpawnPointManager().getAllSpawnPointIdsFromFile();
        for (UUID pointIdFromFile : allFilePointIds) {
            if (plugin.getSpawnPointManager().getSpawnPoint(pointIdFromFile) == null && !spawnedItemsMap.containsKey(pointIdFromFile)) {
                SpawnPoint newlyLoadedPoint = plugin.getSpawnPointManager().attemptLoadSingleSpawnPoint(pointIdFromFile);

                if (newlyLoadedPoint != null) {
                    Item existingItem = findAirCurrencyItemBySpawnPointId(newlyLoadedPoint.getId());

                    if (newlyLoadedPoint.getLocation() != null && newlyLoadedPoint.getLocation().getWorld() != null &&
                            newlyLoadedPoint.getLocation().getWorld().getUID().equals(chunk.getWorld().getUID()) &&
                            (newlyLoadedPoint.getLocation().getBlockX() >> 4) == chunk.getX() &&
                            (newlyLoadedPoint.getLocation().getBlockZ() >> 4) == chunk.getZ()) {

                        if (existingItem == null) {
                            plugin.getLogger().fine("Newly loaded spawn point " + newlyLoadedPoint.getId() + " is in the loaded chunk, not tracked, AND no existing entity found. Attempting initial spawn.");
                            spawnItem(newlyLoadedPoint);
                        } else {
    //DisabledLog                        plugin.getLogger().warning("Newly loaded spawn point " + newlyLoadedPoint.getId() + " is in the loaded chunk, not tracked, BUT found an existing entity: " + existingItem.getUniqueId() + ". Adding to tracking map instead of spawning new.");
                            spawnedItemsMap.put(newlyLoadedPoint.getId(), existingItem.getUniqueId());
                        }

                    } else {
                        plugin.getLogger().fine("Newly loaded spawn point " + newlyLoadedPoint.getId() + " is not in chunk " + chunk.getX() + "," + chunk.getZ() + " but loaded successfully and not tracked. Will spawn when its chunk loads.");
                    }
                } else {
                    plugin.getLogger().fine("Spawn point " + pointIdFromFile + " still not loaded after chunk " + chunk.getX() + "," + chunk.getZ() + " loaded (world still unloaded or data bad?).");
                }
            } else if (plugin.getSpawnPointManager().getSpawnPoint(pointIdFromFile) == null && spawnedItemsMap.containsKey(pointIdFromFile)) {
                plugin.getLogger().warning("SpawnedItemsMap tracks item for non-active SpawnPoint " + pointIdFromFile + ". This entry may be stale. Cleanup handled by handleChunkUnload/despawnItem.");
            }
        }
    }

    /**
     * Handles chunk unloading - despawns any *tracked* items within this chunk.
     * **DOES NOT REMOVE FROM spawnedItemsMap IF ENTITY NOT FOUND.** Map entry is only removed
     * on pickup, spawn point removal, or if handleChunkLoad detects missing entity in loaded chunk.
     *
     * @param chunk The unloaded chunk.
     */
    public void handleChunkUnload(Chunk chunk) {
        if (chunk == null) return;
        // plugin.getLogger().fine("Chunk unloading: " + chunk.getX() + "," + chunk.getZ() + " in " + chunk.getWorld().getName() + ". Checking for tracked AirCurrency items to despawn.");


        Iterator<Map.Entry<UUID, UUID>> iterator = spawnedItemsMap.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, UUID> entry = iterator.next();
            UUID spawnPointId = entry.getKey();
            UUID entityUUID = entry.getValue();

            SpawnPoint point = plugin.getSpawnPointManager().getSpawnPoint(spawnPointId);

            if (point != null && point.getLocation() != null && point.getLocation().getWorld() != null &&
                    point.getLocation().getWorld().getUID().equals(chunk.getWorld().getUID()) &&
                    (point.getLocation().getBlockX() >> 4) == chunk.getX() &&
                    (point.getLocation().getBlockZ() >> 4) == chunk.getZ()) {
                Entity entity = plugin.getServer().getEntity(entityUUID);

                if (entity != null && entity instanceof Item && entity.getPersistentDataContainer().has(airCurrencyKey, PersistentDataType.STRING)) {
                    plugin.getLogger().fine("Found tracked item entity " + entityUUID + " for SpawnPoint " + spawnPointId + " in unloading chunk. Despawning.");
                    entity.remove();
                } else {
                    plugin.getLogger().fine("Tracked entity " + entityUUID + " for SpawnPoint " + spawnPointId + " expected in unloading chunk but not found in valid state. Not removing from map yet.");
                }
            } else if (point == null && spawnedItemsMap.containsKey(spawnPointId)) {
                plugin.getLogger().fine("SpawnedItemsMap tracks item entity " + entityUUID + " for non-active SpawnPoint " + spawnPointId + ". Checking if entity still exists for cleanup.");
                Entity entity = plugin.getServer().getEntity(entityUUID);
                if (entity != null) {
                    plugin.getLogger().warning("SpawnedItemsMap tracks item entity " + entityUUID + " for non-active SpawnPoint " + spawnPointId + ", and entity still exists! Removing from map and despawning entity.");
                    entity.remove();
                    iterator.remove();
                } else {
                    plugin.getLogger().fine("SpawnedItemsMap tracks item entity " + entityUUID + " for non-active SpawnPoint " + spawnPointId + ", entity not found. Removing from map.");
                    iterator.remove();
                }
            }
        }
    }

    // --- Helper method to get SpawnPoint ID from entity PersistentData ---
    private UUID getSpawnPointIdFromEntity(Entity entity) {
        if (entity == null || !(entity instanceof Item)) return null;
        PersistentDataContainer container = entity.getPersistentDataContainer();
        if (container.has(airCurrencyKey, PersistentDataType.STRING)) {
            try {
                return UUID.fromString(container.get(airCurrencyKey, PersistentDataType.STRING));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid UUID format stored in PersistentData for entity " + entity.getUniqueId() + ". Data corrupted?");
                return null;
            }
        }
        return null;
    }

    // --- Helper method to find AirCurrency item entity by SpawnPoint ID ---
    private Item findAirCurrencyItemBySpawnPointId(UUID spawnPointId) {
        if (spawnPointId == null) return null;
        SpawnPoint point = plugin.getSpawnPointManager().getSpawnPoint(spawnPointId);
        if (point != null && point.getLocation() != null && point.getLocation().getWorld() != null) {
            if (point.getLocation().getChunk().isLoaded()) {
                for (Entity entity : point.getLocation().getChunk().getEntities()) {
                    if (entity instanceof Item) {
                        UUID idFromEntity = getSpawnPointIdFromEntity(entity);
                        if (spawnPointId.equals(idFromEntity)) {
                            return (Item) entity;
                        }
                    }
                }
            }
        } else if (spawnPointId != null) {
            // This case is primarily for cleanup in handleChunkUnload when point is null but id is in map.
            // If point is null, we need to scan all loaded worlds, not just a specific chunk.
            // However, the current handleChunkUnload structure calls this helper only when point is null
            // as part of a check *after* iterating the map, not for finding an entity to link to a null point.
            // The primary use is in handleChunkLoad/spawnItemsInLoadedChunks where the point is active.
            // The existing logic for handleChunkUnload cleanup relies on the spawnedItemsMap entry to get the UUID
            // and then entity.remove(). It doesn't use findAirCurrencyItemBySpawnPointId for cleanup.
            // So, the current implementation of this helper is sufficient for its intended uses (handleChunkLoad, spawnItemsInLoadedChunks).
        }
        return null;
    }


    // --- KEZDETI ITEM SPAWNOLÁS BETÖLTÖTT CHUNKOKBAN PLUGIN INDULÁSKOR ---

    /**
     * Called on plugin enable to attempt spawning items for points in already loaded chunks.
     * On startup, it first identifies existing tracked items in loaded worlds before spawning missing ones.
     */
    public void spawnItemsInLoadedChunks() {
    //DisabledLog    plugin.getLogger().info("Attempting to spawn items in loaded chunks on startup..."); // Keep as INFO

        // Fázis 1: Feltöltjük a spawnedItemsMap-et a világban MÁR létező AirCurrency item entitásokkal
        int existingFoundCount = 0;
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(Item.class)) {
                UUID spawnPointId = getSpawnPointIdFromEntity(entity);

                if (spawnPointId != null) {
                    SpawnPoint point = plugin.getSpawnPointManager().getSpawnPoint(spawnPointId);

                    if (point != null) {
                        UUID entityUUID = entity.getUniqueId();
                        spawnedItemsMap.put(spawnPointId, entityUUID);
                        existingFoundCount++;
                        plugin.getLogger().fine("Startup scan found existing AirCurrency item entity " + entityUUID + " for active SpawnPoint " + spawnPointId + " at " + formatLocation(entity.getLocation()) + ". Added to tracking map."); // Keep as FINE
                    } else {
                        plugin.getLogger().warning("Startup scan found orphaned AirCurrency item entity " + entity.getUniqueId() + " with marker for non-active SpawnPoint " + spawnPointId + " at " + formatLocation(entity.getLocation()) + ". Removing entity from world."); // Keep as WARNING
                        entity.remove();
                    }
                }
            }
        }
    //DisabledLog    plugin.getLogger().info("Startup scan completed. Found " + existingFoundCount + " existing tracked items in loaded worlds."); // Keep as INFO


        // Fázis 2: Spawnolunk itemet azokra az aktív pontokra, amelyekhez Még Nem Találtunk itemet az 1. fázisban.
        int newlySpawnedCount = 0;
        for (SpawnPoint point : plugin.getSpawnPointManager().getAllSpawnPoints()) {
            Location loc = point.getLocation();
            if (loc != null && loc.getWorld() != null && loc.getChunk().isLoaded() && !spawnedItemsMap.containsKey(point.getId())) {
                Item existingItem = findAirCurrencyItemBySpawnPointId(point.getId());

                if (existingItem == null) {
                    plugin.getLogger().fine("Active spawn point " + point.getId() + " in loaded chunk is not tracked yet, AND no existing entity found in world. Attempting to spawn new item."); // Keep as FINE
                    spawnItem(point);
                    newlySpawnedCount++;
                } else {
    //DisabledLog                plugin.getLogger().warning("Active spawn point " + point.getId() + " in loaded chunk is not tracked yet, BUT found an existing entity in world: " + existingItem.getUniqueId() + ". Adding to tracking map instead of spawning new."); // Keep as WARNING
                    spawnedItemsMap.put(point.getId(), existingItem.getUniqueId());
                }
            }
        }
    //DisabledLog    plugin.getLogger().info("Finished initial item spawning on startup. Spawned " + newlySpawnedCount + " new items for untracked points in loaded chunks."); // Keep as INFO
    //DisabledLog    plugin.getLogger().info("Initial startup item handling complete. Total tracked items after startup: " + spawnedItemsMap.size()); // Keep as INFO
    }


    // Helper method for logging location
    private String formatLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return "null";
        return String.format("%s(%.2f, %.2f, %.2f)", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
    }

    // --- Cleanup on Plugin Disable ---
    public void onPluginDisable() {
    //DisabledLog    plugin.getLogger().info("Cleaning up AirCurrency items and tasks on plugin disable..."); // Keep as INFO
        despawnAllItems();
        cancelAllRespawnTasks();
        spawnedItemsMap.clear();
        respawnTasks.clear();
    //DisabledLog    plugin.getLogger().info("AirCurrency cleanup complete."); // Keep as INFO
    }
}
// --- KÓD VÉGE ---