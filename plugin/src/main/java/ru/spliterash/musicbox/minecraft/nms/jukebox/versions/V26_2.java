package ru.spliterash.musicbox.minecraft.nms.jukebox.versions;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Jukebox;
import org.bukkit.inventory.ItemStack;
import ru.spliterash.musicbox.MusicBox;
import ru.spliterash.musicbox.minecraft.nms.jukebox.IJukebox;

import java.lang.reflect.Method;
import java.util.logging.Level;

/**
 * Jukebox implementation for calendar-versioned servers (26.x and up).
 *
 * <p>No paperweight dev bundle is used here, so the block entity is reached
 * through a small reflective bridge. The record is placed with the server's
 * {@code JukeboxBlockEntity#setSongItemWithoutPlaying(ItemStack, long)}, which
 * stores the record without starting the vanilla jukebox music. The plain Bukkit
 * paths are unusable for this: {@code setRecord()} starts the vanilla music on
 * the live tile entity, and writing through the container inventory calls
 * {@code setTheItem() -> JukeboxSongPlayer.play()}, which NPEs on a snapshot
 * entity (null level) and loses the disc.</p>
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
     * Reflectively calls {@code JukeboxBlockEntity#setSongItemWithoutPlaying}
     * on the live tile entity. Returns false when the bridge is unavailable.
     */
    private boolean trySetWithoutPlaying(ItemStack item) {
        try {
            Object tileEntity = liveTileEntity();
            if (tileEntity == null)
                return false;
            Object nmsItem = toNmsItem(item);
            Method setter = findMethod(tileEntity.getClass(), "setSongItemWithoutPlaying", 2);
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

    private Object liveTileEntity() throws Exception {
        Object world = block.getWorld();
        Method getHandle = findMethod(world.getClass(), "getHandle", 0);
        if (getHandle == null)
            return null;
        Object serverLevel = getHandle.invoke(world);
        Class<?> blockPosClass = Class.forName("net.minecraft.core.BlockPos");
        Object pos = blockPosClass.getConstructor(int.class, int.class, int.class)
                .newInstance(block.getX(), block.getY(), block.getZ());
        Method getBlockEntity = findMethod(serverLevel.getClass(), "getBlockEntity", 1);
        if (getBlockEntity == null)
            return null;
        return getBlockEntity.invoke(serverLevel, pos);
    }

    private static Object toNmsItem(ItemStack item) throws Exception {
        Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
        Method asNMSCopy = findMethod(craftItemStack, "asNMSCopy", 1);
        if (asNMSCopy == null)
            throw new IllegalStateException("CraftItemStack.asNMSCopy not found");
        return asNMSCopy.invoke(null, item);
    }

    private static Method findMethod(Class<?> clazz, String name, int paramCount) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount)
                return m;
        }
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramCount)
                return m;
        }
        return null;
    }

    @Override
    public ItemStack getJukebox() {
        Jukebox jukebox = fresh();
        if (jukebox == null || !jukebox.hasRecord())
            return null;
        ItemStack record = jukebox.getRecord();
        return (record == null || record.getType().isAir()) ? null : record;
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
