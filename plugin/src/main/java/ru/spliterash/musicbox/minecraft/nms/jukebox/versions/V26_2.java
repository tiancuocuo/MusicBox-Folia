package ru.spliterash.musicbox.minecraft.nms.jukebox.versions;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import ru.spliterash.musicbox.MusicBox;
import ru.spliterash.musicbox.minecraft.nms.jukebox.IJukebox;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Jukebox implementation for calendar-versioned servers (26.x and up).
 *
 * <p>No paperweight dev bundle is used here, so the block entity is reached
 * through a small reflective bridge. The record is placed with the server's
 * {@code JukeboxBlockEntity#setSongItemWithoutPlaying(ItemStack, long)}, which
 * stores the record without starting the vanilla jukebox music, and the block
 * state's {@code has_record} property is set so the disc renders on top.</p>
 *
 * <p>Notes discovered against a 26.1.2 server:
 * <ul>
 *   <li>{@code Jukebox#setRecord()} and the container inventory write both end up
 *       starting (or trying to start) vanilla playback, so they are unusable.</li>
 *   <li>{@code Jukebox#hasRecord()} reads the block-state property, not the stored
 *       record, so {@link #getJukebox()} must read the record directly.</li>
 * </ul></p>
 */
public class V26_2 implements IJukebox {
    private final Block block;

    public V26_2(Jukebox jukebox) {
        this.block = jukebox.getBlock();
    }

    @Override
    public void setJukebox(ItemStack item) {
        if (trySetWithoutPlaying(item))
            return;
        // fallback: plain setRecord + stopPlaying. The record is placed (never
        // lost) at the cost of a possible vanilla disc sound.
        try {
            Jukebox jukebox = fresh();
            if (jukebox != null) {
                jukebox.setRecord(item);
                jukebox.stopPlaying();
                jukebox.update(true, false);
            }
        } catch (Throwable t) {
            MusicBox.getInstance().getLogger().log(Level.WARNING,
                    "[MusicBox] Failed to place jukebox record", t);
        }
    }

    /**
     * Places the record without vanilla playback: updates the {@code has_record}
     * block-state property first (rendering), then calls
     * {@code JukeboxBlockEntity#setSongItemWithoutPlaying} on the live entity.
     * Returns false when the bridge is unavailable.
     */
    private boolean trySetWithoutPlaying(ItemStack item) {
        try {
            Object serverLevel = serverLevel();
            Object pos = blockPos();
            if (serverLevel == null || pos == null)
                return false;
            Object nmsItem = toNmsItem(item);
            if (nmsItem == null)
                return false;

            // rendering: mark the block state so the client shows the disc on top
            try {
                markHasRecord(serverLevel, pos, true);
            } catch (Throwable t) {
                MusicBox.getInstance().getLogger().log(Level.WARNING,
                        "[MusicBox] Failed to update jukebox has_record property", t);
            }

            Object tileEntity = tileEntityAt(serverLevel, pos);
            if (tileEntity == null)
                return false;
            Method setter = exactMethod(tileEntity.getClass(), "setSongItemWithoutPlaying",
                    nmsItem.getClass(), long.class);
            if (setter == null)
                return false;
            setter.setAccessible(true);
            setter.invoke(tileEntity, nmsItem, 0L);
            return true;
        } catch (Throwable t) {
            MusicBox.getInstance().getLogger().log(Level.WARNING,
                    "[MusicBox] No-sound jukebox placement unavailable, falling back", t);
            return false;
        }
    }

    @Override
    public ItemStack getJukebox() {
        // hasRecord() reads the block-state property (not the stored record), so the
        // record must be read directly from the jukebox's item instead.
        Jukebox jukebox = fresh();
        if (jukebox == null)
            return null;
        ItemStack record = jukebox.getRecord();
        return (record == null || record.getType().isAir()) ? null : record;
    }

    // ================= reflective NMS bridge =================

    private Object serverLevel() throws Exception {
        Object world = block.getWorld();
        Method getHandle = exactMethod(world.getClass(), "getHandle");
        return getHandle == null ? null : getHandle.invoke(world);
    }

    private Object blockPos() throws Exception {
        Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
        return blockPosClass.getConstructor(int.class, int.class, int.class)
                .newInstance(block.getX(), block.getY(), block.getZ());
    }

    private Object tileEntityAt(Object serverLevel, Object pos) throws Exception {
        Method getBlockEntity = exactMethod(serverLevel.getClass(), "getBlockEntity", pos.getClass());
        return getBlockEntity == null ? null : getBlockEntity.invoke(serverLevel, pos);
    }

    /**
     * Sets the jukebox block state's {@code has_record} property, so the disc
     * renders on top of the block. Uses the same setBlock flag as
     * {@code CraftJukebox#update} (3 = update neighbors + clients).
     */
    private void markHasRecord(Object serverLevel, Object pos, boolean value) throws Exception {
        Class<?> blockStateClass = Class.forName("net.minecraft.world.level.block.state.BlockState");
        Method getBlockState = exactMethod(serverLevel.getClass(), "getBlockState", pos.getClass());
        Object blockState = getBlockState.invoke(serverLevel, pos);
        Class<?> jukeboxBlockClass = Class.forName("net.minecraft.world.level.block.JukeboxBlock");
        Field hasRecord = jukeboxBlockClass.getField("HAS_RECORD");
        Object property = hasRecord.get(null);
        Class<?> propertyClass = Class.forName("net.minecraft.world.level.block.state.properties.Property");
        Method setValue = exactMethod(blockStateClass, "setValue", propertyClass, Comparable.class);
        Object newState = setValue.invoke(blockState, property, value);
        Method setBlock = exactMethod(serverLevel.getClass(), "setBlock",
                pos.getClass(), blockStateClass, int.class);
        setBlock.invoke(serverLevel, pos, newState, 3);
    }

    /**
     * Converts a Bukkit stack to an NMS stack. Note that {@code asNMSCopy} has an
     * overload taking a {@code List}, so matching must be done on the exact
     * parameter type, not just the parameter count.
     */
    private static Object toNmsItem(ItemStack item) throws Exception {
        Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
        Method asNMSCopy = exactMethod(craftItemStack, "asNMSCopy", ItemStack.class);
        if (asNMSCopy == null)
            throw new IllegalStateException("CraftItemStack.asNMSCopy(ItemStack) not found");
        return asNMSCopy.invoke(null, item);
    }

    /** Finds a public method by name and exact parameter types. */
    private static Method exactMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * Returns a fresh snapshot of the live block, or {@code null} when it is no
     * longer a jukebox.
     */
    private Jukebox fresh() {
        BlockState state = block.getState();
        return state instanceof Jukebox ? (Jukebox) state : null;
    }
}
