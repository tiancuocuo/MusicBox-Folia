package ru.spliterash.musicbox.minecraft.nms.jukebox.versions;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import ru.spliterash.musicbox.minecraft.nms.jukebox.IJukebox;

/**
 * Jukebox implementation for calendar-versioned servers (26.x and up).
 *
 * <p>Unlike the NMS-based implementations this one is pure Bukkit API, so it needs
 * no paperweight dev bundle and keeps working across 26.x releases. Inserting a
 * record without the vanilla jukebox starting to play is done by setting the record
 * and immediately clearing the playing flag inside the same block-state snapshot,
 * then committing it once: the world (and clients) only ever see the final
 * "record present, not playing" state, so no vanilla disc sound ever starts.</p>
 */
public class V26_2 implements IJukebox {
    private final Block block;

    public V26_2(Jukebox jukebox) {
        this.block = jukebox.getBlock();
    }

    /**
     * Returns a fresh snapshot of the live block, or {@code null} when it is no
     * longer a jukebox. Fetching fresh avoids acting on a stale state (the caller
     * may already have ejected the previous record on an older snapshot).
     */
    private Jukebox fresh() {
        BlockState state = block.getState();
        return state instanceof Jukebox ? (Jukebox) state : null;
    }

    @Override
    public void setJukebox(ItemStack item) {
        Jukebox jukebox = fresh();
        if (jukebox == null)
            return;
        // setRecord auto-starts playing inside the snapshot; stopPlaying clears that
        // flag while keeping the record, so the single update() commits a silent
        // "record inserted" state.
        jukebox.setRecord(item);
        jukebox.stopPlaying();
        jukebox.update(true, false);
    }

    @Override
    public ItemStack getJukebox() {
        Jukebox jukebox = fresh();
        if (jukebox == null || !jukebox.hasRecord())
            return null;
        ItemStack record = jukebox.getRecord();
        return (record == null || record.getType().isAir()) ? null : record;
    }
}
