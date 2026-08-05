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
 * record without the vanilla jukebox starting to play is done by writing the
 * record into the jukebox's inventory slot (jukeboxes are containers since
 * 1.21.3): the plain container write keeps the record but never starts vanilla
 * playback, and stopPlaying() is kept as a guard on the playing flag.</p>
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
        // setRecord() starts the vanilla jukebox music on the live tile entity, so
        // placing the record through the jukebox's inventory slot is used instead:
        // the plain container write keeps the record without ever starting vanilla
        // playback, and stopPlaying() guards the playing flag for good measure.
        jukebox.getSnapshotInventory().setItem(0, item);
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
