// AirCurrencyPlugin.java - Legutolsó, Végső Verzió, JAVÍTVA: Help Prefix, Help Sorközök, Magyar Üzenetek
package me.herku.aircurrency;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class AirCurrencyPlugin extends JavaPlugin implements TabCompleter {

    private SpawnPointManager spawnPointManager;
    private FloatingItemManager floatingItemManager;
    private ChunkListener chunkListener;
    private PickupListener pickupListener;

    private static Economy econ = null;

    private FileConfiguration config;
    private File configFile;

    private String pluginPrefix = "§8[§eAirCurrency§8] §r"; // Default prefix

    // Map to track players who need to confirm /removeall
    private final Map<UUID, BukkitTask> removeallConfirmations = new HashMap<>();


    // --- Plugin Enable/Disable ---

    @Override
    public void onEnable() {
        setupConfig();
        loadConfig();

        spawnPointManager = new SpawnPointManager(this);
        floatingItemManager = new FloatingItemManager(this);

        chunkListener = new ChunkListener(this);
        pickupListener = new PickupListener(this);
        getServer().getPluginManager().registerEvents(chunkListener, this);
        getServer().getPluginManager().registerEvents(pickupListener, this);

        // Register command executor and tab completer (this class)
        getCommand("aircurrency").setExecutor(this);
        getCommand("aircurrency").setTabCompleter(this);


        if (!setupEconomy()) {
            getLogger().severe(ChatColor.stripColor(pluginPrefix) + " Vault not found or no economy plugin hooked! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    //DisabledLog    getLogger().info(ChatColor.stripColor(pluginPrefix) + " Vault found and hooked successfully.");


        floatingItemManager.spawnItemsInLoadedChunks();

    //DisabledLog    getLogger().info(ChatColor.stripColor(pluginPrefix) + " has been enabled!");
    }

    @Override
    public void onDisable() {
        spawnPointManager.onPluginDisable();
        floatingItemManager.onPluginDisable();

        // Cancel any pending removeall confirmation tasks
        removeallConfirmations.values().forEach(BukkitTask::cancel);
        removeallConfirmations.clear();
    //DisabledLog    getLogger().info(ChatColor.stripColor(pluginPrefix) + " All removeall confirmation tasks cancelled.");


        getLogger().info(ChatColor.stripColor(pluginPrefix) + " has been disabled!");
    }


    // --- Config Handling ---

    public void setupConfig() {
        configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getLogger().info("config.yml not found, creating default...");
            saveResource("config.yml", false);
        }
        config = getConfig();
    }

    public void loadConfig() {
        config = getConfig();
        pluginPrefix = config.getString("plugin-prefix", "§8[§aAirCurrency§8] §r");
        // Item values are now stored per spawn point, not in config.
    }

    public void saveConfig() {
        try {
            config.save(configFile);
            getLogger().fine("config.yml saved.");
        } catch (IOException e) {
            getLogger().severe("Could not save config.yml!");
            e.printStackTrace();
        }
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }


    // --- Command Handling ---

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("aircurrency")) {
            return false;
        }

        // ** Single Permission Check for all commands **
        if (!sender.hasPermission("aircurrency.commands.admin")) {
            sendMessage(sender, "§cNincs jogosultságod az AirCurrency parancsok használatához.", true); // <-- LEFORDÍTVA
            return true;
        }


        // --- /aircurrency (no subcommand) - Show Help ---
        if (args.length < 1) {
            sendFormattedHelpMessage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        // --- /aircurrency set command ---
        if (subCommand.equals("set")) {
            if (!(sender instanceof Player)) {
                sendMessage(sender, "§cCsak játékosok használhatják ezt a parancsot.", true); // <-- LEFORDÍTVA
                return true;
            }
            Player player = (Player) sender;

            // Check argument count: set <item> <value> <RespawnTime> [message]
            if (args.length < 4) {
                sendFormattedUsageMessageSet(sender);
                return true;
            }

            String itemTypeId = args[1].toUpperCase();
            double amount; // Amount from command args
            int respawnTime;
            String message = "";

            // Validate amount
            try {
                amount = Double.parseDouble(args[2]);
                if (amount < 0) {
                    getLogger().warning("Spawn point set with negative amount: " + amount + " by player " + player.getName());
                }
            } catch (NumberFormatException e) {
                sendMessage(sender, "§cÉrvénytelen összeg érték formátum.", true); // <-- LEFORDÍTVA
                sendFormattedUsageMessageSet(sender);
                return true;
            }

            // Validate respawn time
            try {
                respawnTime = Integer.parseInt(args[3]);
                if (respawnTime < 0) {
                    sendMessage(sender, "§cAz újraspawn idő értéke nem lehet negatív.", true); // <-- LEFORDÍTVA
                    return true;
                }
            } catch (NumberFormatException e) {
                sendMessage(sender, "§cÉrvénytelen újraspawn idő érték formátum.", true); // <-- LEFORDÍTVA
                return true;
            }

            // Collect message arguments if present
            if (args.length > 4) {
                StringBuilder messageBuilder = new StringBuilder();
                for (int i = 4; i < args.length; i++) {
                    messageBuilder.append(args[i]).append(" ");
                }
                message = messageBuilder.toString().trim();
            }

            // Check if the item type is valid
            Material itemMaterial = Material.matchMaterial(itemTypeId);
            if (itemMaterial == null || !itemMaterial.isItem()) {
                sendMessage(sender, "§cÉrvénytelen item típus: " + itemTypeId, true); // <-- LEFORDÍTVA
                return true;
            }

            Location blockLocation = spawnPointManager.getBlockUnderPlayerLocation(player.getLocation());

            if (blockLocation == null) {
                sendMessage(sender, "§cNem állsz egy blokkon sem, amire létre lehetne hozni AirCurrency pontot.", true); // <-- LEFORDÍTVA
                return true;
            }

            // Create a new SpawnPoint instance with the amount from the command
            UUID newId = UUID.randomUUID();
            SpawnPoint newSpawnPoint = new SpawnPoint(newId, blockLocation, itemTypeId, respawnTime, message, amount);

            spawnPointManager.addSpawnPoint(newSpawnPoint);

            floatingItemManager.spawnItem(newSpawnPoint);

            // Sikeres set üzenet (most magyarul és formázhatóan)
            sendMessage(sender, "§aSikeresen létrehoztál egy AirCurrency pontot a(z) " + itemTypeId + " itemhez, " + amount + " értékkel és " + respawnTime + "s újraspawnolási idővel.", true); // <-- LEFORDÍTVA
            if (!message.isEmpty()) {
                sendMessage(sender, "§aEgyedi üzenet: '" + message + "'", true); // <-- LEFORDÍTVA
            }

            return true;
        }

        // --- /aircurrency remove command ---
        if (subCommand.equals("remove")) {
            if (args.length != 1) {
                sendFormattedUsageMessageRemove(sender);
                return true;
            }

            if (!(sender instanceof Player)) {
                sendMessage(sender, "§cCsak játékosok használhatják ezt a parancsot.", true); // <-- LEFORDÍTVA
                return true;
            }
            Player player = (Player) sender;

            double searchRadius = 1.5;

            SpawnPoint nearestPoint = spawnPointManager.findNearestSpawnPoint(player.getLocation(), searchRadius);

            if (nearestPoint != null) {
                UUID pointIdToRemove = nearestPoint.getId();
                spawnPointManager.removeSpawnPoint(pointIdToRemove);

                // Sikeres remove üzenet (most magyarul)
                sendMessage(sender, "§aSikeresen eltávolítottad a legközelebbi spawnpontot.", true); // <-- LEFORDÍTVA
            } else {
                sendFormattedUsageMessageRemove(sender); // Show usage/help message when no point found
            }

            return true;
        }


        // --- /aircurrency removeall command ---
        if (subCommand.equals("removeall")) {
            if (args.length != 1) {
                sendFormattedUsageMessageRemoveAll(sender); // Shows confirmation message as usage
                return true;
            }

            // Handle console execution vs Player confirmation
            if (!(sender instanceof Player)) {
                performRemoveAll(sender); // Console removes immediately
                return true;
            }

            // Player execution - require confirmation
            Player player = (Player) sender;

            // Check if player already has a pending confirmation
            if (removeallConfirmations.containsKey(player.getUniqueId())) {
                sendMessage(player, "§cMár van függőben egy /aircurrency confirm kérésed.", true); // <-- LEFORDÍTVA
                return true;
            }

            // Add player to confirmation map and schedule timeout task (20 seconds)
            BukkitTask confirmationTask = Bukkit.getScheduler().runTaskLater(this, () -> {
                removeallConfirmations.remove(player.getUniqueId()); // Remove from map after timeout
                if (player.isOnline()) { // Check if player is still online
                    sendMessage(player, "§eA /aircurrency confirm kérésed lejárt.", true); // <-- LEFORDÍTVA
                }
                getLogger().fine("Removeall confirmation for player " + player.getName() + " expired."); // Ez egy konzol log, maradhat angolul
            }, 20 * 20L);

            removeallConfirmations.put(player.getUniqueId(), confirmationTask); // Add to map

            // Send confirmation message (which is the usage message in this case)
            sendFormattedUsageMessageRemoveAll(sender);

            return true;
        }

        // --- /aircurrency confirm command (for removeall) ---
        if (subCommand.equals("confirm")) {
            // Only players can confirm.
            if (!(sender instanceof Player)) {
                sendMessage(sender, "§cCsak játékosok használhatják ezt a parancsot.", true); // <-- LEFORDÍTVA (ugyanaz, mint fent)
                return true;
            }
            Player player = (Player) sender;

            if (args.length != 1) {
                sendMessage(player, "§cHelyes használat: /aircurrency confirm", true); // <-- LEFORDÍTVA
                return true;
            }

            // Check if the player has a pending removeall confirmation
            if (removeallConfirmations.containsKey(player.getUniqueId())) {
                // Cancel the timeout task and remove from map
                BukkitTask task = removeallConfirmations.remove(player.getUniqueId());
                if (task != null) {
                    task.cancel();
                }

                // Perform the removeall action
                performRemoveAll(sender);

            } else {
                sendMessage(sender, "§cNincs függőben lévő /aircurrency confirm kérésed.", true); // <-- LEFORDÍTVA
            }

            return true;
        }


        // --- /aircurrency prefix command ---
        if (subCommand.equals("prefix")) {
            if (args.length < 2) {
                sendFormattedUsageMessagePrefix(sender);
                return true;
            }

            StringBuilder prefixBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) {
                prefixBuilder.append(args[i]).append(" ");
            }
            String newPrefix = ChatColor.translateAlternateColorCodes('&', prefixBuilder.toString().trim());

            pluginPrefix = newPrefix;
            getPluginConfig().set("plugin-prefix", newPrefix);
            saveConfig();

            // Sikeres prefix beállítás üzenet (magyarul, test message nélkül)
            sendMessage(sender, "§aA plugin előtagja beállítva: " + pluginPrefix, true); // <-- LEFORDÍTVA és EGYSZERŰSÍTVE
            return true;
        }


        // --- Handle unknown subcommand ---
        sendMessage(sender, "§cIsmeretlen alparancs. Használd a /aircurrency parancsot a súgóért.", true); // <-- LEFORDÍTVA
        return true;
    }

    // --- Helper method to display formatted help (on /aircurrency) ---
    private void sendFormattedHelpMessage(CommandSender sender) {
        // Use sendMessage with addPrefix = false for lines and title, false for command list items

        // Line 1 (Header): &f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 2 (Title): &e&o&l&n/AirCurrency:
        sendMessage(sender, "&e&o&l&n/AirCurrency:", false);

        sendMessage(sender, "&r", false); // <-- Üres sor a fejléc és a lista közé

        // Command List Items (use false for prefix, separated by blank lines)
        // Line 4: &e▶ &e&l&oSet ...
        sendMessage(sender, "&e▶ &e&l&oSet &f&l&o- &7&l&oLétrehoz egy drop pontot.", false); // Set command line
        sendMessage(sender, "&r", false); // <-- Üres sor beszúrása

        // Line 5: &e▶ &e&l&oRemove ...
        sendMessage(sender, "&e▶ &e&l&oRemove &f&l&o- &7&l&oEltávolít egy drop pontot.", false); // Remove command line
        sendMessage(sender, "&r", false); // <-- Üres sor beszúrása

        // Line 6: &e▶ &e&l&oRemoveall ...
        sendMessage(sender, "&e▶ &e&l&oRemoveall &f&l&o- &7&l&oEltávolítja az összes drop pontot", false); // Removeall command line
        sendMessage(sender, "&r", false); // <-- Üres sor beszúrása

        // Confirm: &e▶ &e&l&oConfirm ...
        sendMessage(sender, "&e▶ &e&l&oConfirm &f&l&o- &7&l&oMegerősíti az összes AirCurrency pont törlését.", false); // Confirm command line
        sendMessage(sender, "&r", false); // <-- Üres sor beszúrása

        // Prefix: &e▶ &e&l&oPrefix ...
        sendMessage(sender, "&e▶ &e&l&oPrefix &f&l&o- &7&l&oNévelőtag beállítása", false); // Prefix command line
        // Nem kell üres sor az utolsó után, hacsak nem akarsz egy üres sort a footer előtt.
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Final Line (Footer Symmetry):
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false); // <-- Footer Line (ugyanaz, mint a header)
    }

    // --- Helper method to display formatted usage for set ---
    private void sendFormattedUsageMessageSet(CommandSender sender) {
        // Use sendMessage with addPrefix = false for lines, true for others

        // Line 1: &f&o&l&m────────────&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 2: &e&o&l&n/AirCurrency set:
        sendMessage(sender, "&e&o&l&n/AirCurrency set:", false);

        // Blank line for spacing
        sendMessage(sender, "&r", false); // <-- Üres sor

        // Line 3: &e▶ &e&l&oSet &f&l&o<itemID> &f&l&o<Value> &f&l&o<Time> &f&l&o<Message>
        sendMessage(sender, "&e▶ &e&l&oSet &f&l&o<itemID> &f&l&o<Value> &f&l&o<Time> &f&l&o<Message>", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 4: &e▶ &e&l&o<Value> &f&l&o- &7&l&oItem értéke pénzben
        sendMessage(sender, "&e▶ &e&l&o<Value> &f&l&o- &7&l&oItem értéke pénzben", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 5: &e▶ &e&l&o<Time> &f&l&o- &7&l&oFelvétel utáni újraspawnolási idő
        sendMessage(sender, "&e▶ &e&l&o<Time> &f&l&o- &7&l&oFelvétel utáni újraspawnolási idő", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 6: &e▶ &e&l&o<Message> &f&l&o- &7&l&o&nPlaceholder&7&o: &a&l&o[PLAYERBALANCE]
        sendMessage(sender, "&e▶ &e&l&o<Message> &f&l&o- &7&l&o&nPlaceholder&7&o: &a&l&o[PLAYERBALANCE]", false);

        // Final Line (Footer Symmetry):
        sendMessage(sender, "&r", false); // <-- Üres sor
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false); // <-- Footer Line
    }

    // --- Helper method to display formatted usage for remove ---
    private void sendFormattedUsageMessageRemove(CommandSender sender) {
        // Use sendMessage with addPrefix = false for lines, true for others

        // Line 1: &f&o&l&m────────────&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 2: &e&o&l&n/AirCurrency Remove:
        sendMessage(sender, "&e&o&l&n/AirCurrency Remove:", false);

        // Blank line for spacing
        sendMessage(sender, "&r", false); // <-- Üres sor

        // Line 3: &c&l&oNincs drop pont az aktuális helyzetednél!
        sendMessage(sender, "&c&l&oNincs drop pont az aktuális helyzetednél!", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 4: &e▶ &e&l&o Remove parancs helyes használata:
        sendMessage(sender, "&e▶ &e&l&o Remove parancs helyes használata:", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 5: &7&o Állj egy drop pontra, és használd a parancsot.
        sendMessage(sender, "&7&o Állj egy drop pontra, és használd a parancsot.", false);

        // Final Line (Footer Symmetry):
        sendMessage(sender, "&r", false); // <-- Üres sor
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false); // <-- Footer Line
    }

    // --- Helper method to display formatted usage for removeall (Confirmation Message) ---
    private void sendFormattedUsageMessageRemoveAll(CommandSender sender) {
        // Use sendMessage with addPrefix = false for lines, true for others

        // Line 1: &f&o&l&m────────────&7&o&l[&c&o&lAirCurrency&7&o&l]&f&o&l&m─────────── (Note: AirCurrency is Red here)
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&c&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 2: &e&o&l&n/AirCurrency&4&l&o Removeall:
        sendMessage(sender, "&e&o&l&n/AirCurrency&4&l&o Removeall:", false);

        // Blank line for spacing
        sendMessage(sender, "&r", false); // <-- Üres sor

        // Line 3: &4▶ &4&l&o&nFigyelem!&c&l&o Ezzel eltávolitassz &4&l&o&n minden&c&l&o pontot!
        sendMessage(sender, "&4▶ &4&l&o&nFigyelem!&c&l&o Ezzel eltávolitassz &4&l&o&n minden&c&l&o pontot!", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 4: &4▶ &c&l&oHa valóban így döntessz, használd a
        sendMessage(sender, "&4▶ &c&l&oHa valóban így döntessz, használd a", false);

        // Line 5: &4▶ &c&l&o \"&e&o&l/AirCurrency Removeall &c&l&o&nconfirm&c&l&o\" &7&l&oparancsot.
        sendMessage(sender, "&4▶ &c&l&o \"&e&o&l/AirCurrency Removeall &c&l&o&nconfirm&c&l&o\" &7&l&oparancsot.", false); // Used user's exact string including quotes
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 6: &4▶ &e&l&o A parancs hitelessége &c&l&o&n20&e&l&o másodperc múlva lejár.
        sendMessage(sender, "&4▶ &e&l&o A parancs hitelessége &c&l&o&n20&e&l&o másodperc múlva lejár.", false);

        // Final Line (Footer Symmetry):
        sendMessage(sender, "&r", false); // <-- Üres sor
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&c&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false); // <-- Footer Line (red title)
    }

    // --- Helper method to display formatted usage for prefix ---
    private void sendFormattedUsageMessagePrefix(CommandSender sender) {
        // Use sendMessage with addPrefix = false for lines, true for others

        // Line 1: &f&o&l&m────────────&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 2: &e&o&l&n/AirCurrency Prefix:
        sendMessage(sender, "&e&o&l&n/AirCurrency Prefix:", false);

        // Blank line for spacing
        sendMessage(sender, "&r", false); // <-- Üres sor

        // Line 3: &e▶ &e&l&oItt tudsz beállítani prefixet.
        sendMessage(sender, "&e▶ &e&l&oItt tudsz beállítani prefixet.", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 4: &e▶ &e&l&oHasználat:
        sendMessage(sender, "&e▶ &e&l&oHasználat:", false);

        // Line 5: &e▶ &e&l&o/AirCurrency Prefix &a&l&o<Prefix>
        sendMessage(sender, "&e▶ &e&l&o/AirCurrency Prefix &a&l&o<Prefix>", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 6: &e▶ &7&l&o Ez a prefix fog megjelenni mindenhol.
        sendMessage(sender, "&e▶ &7&l&o Ez a prefix fog megjelenni mindenhol:", false);
        sendMessage(sender, "&r", false); // <-- Üres sor
        // Line 7: &e▶ 7&l&oA jelenlegi &e&l&oPrefix&7&l&o:.
        sendMessage(sender, "&e▶ &7&l&oA jelenlegi &e&l&oPrefix&7&l&o:", false); // Escaped quotes
		sendMessage(sender, "&r", true); // <-- Jelenlegi prefix

        // Final Line (Footer Symmetry):
        sendMessage(sender, "&r", false); // <-- Üres sor
        sendMessage(sender, "&f&o&l&m────────────&r&7&o&l[&e&o&lAirCurrency&7&o&l]&f&o&l&m───────────", false); // <-- Footer Line
    }


    // --- Helper method to perform the actual removeall logic ---
    private void performRemoveAll(CommandSender sender) {
        floatingItemManager.despawnAllItems(); // Despawn entities and clear map
        spawnPointManager.getAllSpawnPoints().clear(); // Clear active spawn points in memory
        spawnPointManager.saveSpawnPoints(); // Save empty list to file
        spawnPointManager.loadSpawnPoints(); // Reloads 0 points into memory

        sendMessage(sender, "§aSikeresen eltávolítottad az összes AirCurrency spawnpontot.", true); // <-- LEFORDÍTVA // Confirmation of successful removal, pass true for prefix
    }


    // --- Config Handling ---
    // Methods are defined above


    // --- Vault Integration Setup ---
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    // --- Getters for Managers ---
    public SpawnPointManager getSpawnPointManager() {
        return spawnPointManager;
    }

    public FloatingItemManager getFloatingItemManager() {
        return floatingItemManager;
    }

    // --- Getter for Vault Economy API ---
    public static Economy getEconomy() {
        return econ;
    }


    // --- Economy Logic (Amount from SpawnPoint, giveMoney) ---
    // getMoneyAmountForItem is removed as amount is per SpawnPoint
    // giveMoneyToPlayer is called by PickupListener and only deposits, doesn't send message


    // Method to give money to a player (using Vault) - Called from PickupListener
    public void giveMoneyToPlayer(Player player, double amount) {
        if (econ != null) {
            econ.depositPlayer(player, amount);
            // Message is handled in PickupListener using custom message and placeholders
        } else {
            getLogger().warning("Attempted to give money using Vault, but economy is not hooked!"); // Console log
        }
    }

    // --- Helper message sender with prefix and color code support ---
    // Added boolean flag to control prefix
    public void sendMessage(CommandSender recipient, String message, boolean addPrefix) {
        // Apply color codes to the raw message first
        String formattedMessage = ChatColor.translateAlternateColorCodes('&', message);

        if (addPrefix) {
            recipient.sendMessage(pluginPrefix + formattedMessage); // Add prefix and send
        } else {
            recipient.sendMessage(formattedMessage); // Send without prefix
        }
    }


    // --- Tab Completion ---

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("aircurrency")) {
            return null;
        }

        // Check admin permission for tab completion suggestions
        if (!sender.hasPermission("aircurrency.commands.admin")) {
            return new ArrayList<>(); // No suggestions if no permission
        }

        List<String> completions = new ArrayList<>();
        // Include 'confirm' in the list of possible subcommands for tab completion
        List<String> commands = Arrays.asList("set", "remove", "removeall", "prefix", "confirm");


        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], commands, completions);
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("set")) {
                // Suggest item types for /aircurrency set (2nd arg)
                List<String> materialNames = new ArrayList<>();
                for (Material material : Material.values()) {
                    if (material.isItem()) {
                        materialNames.add(material.name());
                    }
                }
                StringUtil.copyPartialMatches(args[1], materialNames, completions);
            } else if (subCommand.equals("prefix")) {
                // No specific suggestions for prefix value
            }
            // No suggestions for remove, removeall, confirm args > 1
        } else if (args.length == 3) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("set")) {
                // Suggest <value> placeholder (3rd arg)
                // completions.add("<value>"); // Placeholder suggestion
            }
        } else if (args.length == 4) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("set")) {
                // Suggest <RespawnTime> placeholder (4th arg)
                // completions.add("<RespawnTime>"); // Placeholder suggestion
            }
        } else if (args.length > 4) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("set")) {
                // Suggest <Message> placeholder (5th+ args)
                // completions.add("<Message>"); // Placeholder suggestion
            }
        }


        Collections.sort(completions);
        return completions;
    }

}
// --- KÓD VÉGE ---