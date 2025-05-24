// PickupListener.java - Legutolsó verzió, JAVÍTVA: sendMessage hívások
package me.herku.aircurrency;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.UUID;

public class PickupListener implements Listener {

    private final AirCurrencyPlugin plugin;

    public PickupListener(AirCurrencyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler // Listens for when an entity picks up an item
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        // Check if the entity picking up is a player
        if (!(event.getEntity() instanceof Player)) {
            return; // Only interested in players picking up items
        }
        Player player = (Player) event.getEntity();
        Item item = event.getItem(); // The item entity that was picked up

        // Check if the picked up item is one of our floating items
        UUID itemEntityUUID = item.getUniqueId();
        // Use the isAirCurrencyItem check from FloatingItemManager
        if (plugin.getFloatingItemManager().isAirCurrencyItem(itemEntityUUID)) {

            // Get the associated SpawnPoint for this item
            SpawnPoint spawnPoint = plugin.getFloatingItemManager().getSpawnPointForEntity(itemEntityUUID);

            if (spawnPoint != null) {
                // It's one of our tracked items for an active spawn point.

                // 1. Get the money amount directly from the SpawnPoint
                double amount = spawnPoint.getAmount(); // Get amount directly from SpawnPoint

                // 2. Give money to the player using Vault (implemented in plugin class)
                Economy econ = AirCurrencyPlugin.getEconomy(); // Get the static Economy instance
                if (econ != null && amount >= 0) { // Allow 0 amount drops? Yes, based on user command format.
                    // Use the giveMoneyToPlayer helper in AirCurrencyPlugin
                    plugin.giveMoneyToPlayer(player, amount); // Use plugin method to give money

                    // 3. Handle respawning the item after the respawn time
                    plugin.getFloatingItemManager().scheduleRespawn(spawnPoint); // Schedule respawn task

                    // 4. Despawn the item entity immediately from our tracking and the world
                    plugin.getFloatingItemManager().despawnItem(itemEntityUUID); // Explicitly despawn after pickup handling

                    // 5. Send pickup message to the player using the custom message and placeholdrs
                    String customMessage = spawnPoint.getMessage(); // Get the custom message from SpawnPoint
                    if (customMessage != null && !customMessage.isEmpty()) {
                        // Replace placeholders
                        // Get player's NEW balance AFTER depositing
                        double playerBalance = econ.getBalance(player); // Get current balance after deposit

                        String formattedMessage = customMessage
                                .replace("[PLAYERBALANCE]", econ.format(playerBalance)); // Remove [AMOUNT] placeholder replacement

                        plugin.sendMessage(player, formattedMessage, true); // <-- JAVÍTVA: Hozzáadva 'true' paraméter (prefix kell)
                    } else {
                        // If no custom message, send a default message if amount > 0
                        if (amount > 0) {
                            // Use the item's material name for the default message
                            Material itemType = item.getItemStack().getType(); // Get itemType here where it's needed
                            plugin.sendMessage(player, "§aYou picked up a " + itemType.name() + " worth " + econ.format(amount) + "!", true); // <-- JAVÍTVA: Hozzáadva 'true' paraméter (prefix kell)
                        } else {
                            // If amount is 0 and no custom message, no message needed.
                        }
                    }

                    // Prevent the item from going into the player's inventory
                    event.setCancelled(true); // Cancel the pickup event

                } else {
                    // Vault not hooked or amount is negative.
                    if (econ != null && amount < 0) { // Vault is hooked but amount is negative - Problem!
                        Material itemType = item.getItemStack().getType();
                        plugin.getLogger().warning("SpawnPoint " + spawnPoint.getId() + " has negative amount (" + amount + ")! Not giving money for item " + itemType.name() + " and despawning.");
                        plugin.getFloatingItemManager().despawnItem(itemEntityUUID);
                        event.setCancelled(true);
                    } else if (econ == null) { // Vault not hooked
                        plugin.getLogger().warning("Vault economy not hooked. AirCurrency item picked up as normal item.");
                        // Let it be picked up normally. Don't despawn, don't cancel, don't schedule respawn.
                    }
                }

            } else {
                // Found our marker, but no associated SpawnPoint. Data inconsistency.
                plugin.getLogger().warning("AirCurrency item picked up, but no associated SpawnPoint found! Entity UUID: " + itemEntityUUID + ". This might indicate a data inconsistency");
                plugin.getFloatingItemManager().despawnItem(itemEntityUUID);
                event.setCancelled(true);
            }

        }
        // If not our item, let the event proceed normally.
    }

}
// --- KÓD VÉGE ---