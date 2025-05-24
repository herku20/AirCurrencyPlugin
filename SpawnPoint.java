// SpawnPoint.java - Legutolsó verzió, HELYESÍTVE: amount mező
package me.herku.aircurrency;

import org.bukkit.Location;

import java.util.UUID;

public class SpawnPoint {

    private final UUID id;
    private Location location;
    private String itemTypeId; // Store as string like "GOLD_INGOT"
    private int respawnTime; // in seconds
    private String message; // Custom pickup message
    private double amount; // <-- ÚJ MEZŐ: Pénzösszeg, amit ad

    // Constructor including message and amount
    public SpawnPoint(UUID id, Location location, String itemTypeId, int respawnTime, String message, double amount) { // <-- MÓDOSÍTVA (6 paraméter)
        this.id = id;
        this.location = location;
        this.itemTypeId = itemTypeId;
        this.respawnTime = respawnTime;
        this.message = message;
        this.amount = amount; // <-- ÚJ MEZŐ ÉRTÉKADÁS
    }

    // Constructor without message (for compatibility, though less needed long term)
    // Let's update this constructor to include a default amount.
    public SpawnPoint(UUID id, Location location, String itemTypeId, int respawnTime, double amount) { // <-- HOZZÁADVA (5 paraméter)
        this(id, location, itemTypeId, respawnTime, "", amount); // Default message empty
    }

    // Constructor without message and amount (for compatibility, maybe remove later)
    public SpawnPoint(UUID id, Location location, String itemTypeId, int respawnTime) { // <-- Eredeti (4 paraméter)
        this(id, location, itemTypeId, respawnTime, "", 0.0); // Default message empty, default amount 0.0
    }


    // Getters
    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public String getItemTypeId() {
        return itemTypeId;
    }

    public int getRespawnTime() {
        return respawnTime;
    }

    public String getMessage() {
        return message;
    }

    public double getAmount() { // <-- ÚJ GETTER
        return amount;
    }


    // Setters (if needed, though for Location, ItemType, RespawnTime, Amount it's less common after creation)
    public void setMessage(String message) {
        this.message = message;
    }

    // public void setAmount(double amount) { this.amount = amount; } // Setter for amount if needed

}
// --- KÓD VÉGE ---