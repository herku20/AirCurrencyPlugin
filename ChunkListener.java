/// ChunkListener.java - Legutolsó verzió
package me.herku.aircurrency;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

public class ChunkListener implements Listener {

    private final AirCurrencyPlugin plugin;

    public ChunkListener(AirCurrencyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Delegate chunk load handling to FloatingItemManager
        plugin.getFloatingItemManager().handleChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Delegate chunk unload handling to FloatingItemManager
        plugin.getFloatingItemManager().handleChunkUnload(event.getChunk());
    }
}
// --- KÓD VÉGE ---