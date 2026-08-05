package ru.spliterash.musicbox.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import ru.spliterash.musicbox.MusicBox;

/**
 * Transparent scheduler bridge between the classic Bukkit scheduler (Paper/Spigot)
 * and Folia's regionized schedulers.
 *
 * <p>On Folia {@link org.bukkit.scheduler.BukkitScheduler} is a stub that throws
 * {@link UnsupportedOperationException}. All scheduling has to go through the
 * global / region / async schedulers, and all block &amp; entity access must happen
 * on the thread that owns that region. This helper hides the platform branching.</p>
 */
public final class FoliaUtils {

	private static final boolean FOLIA;

	static {
		boolean folia = false;
		try {
			Class.forName("io.papermc.paper.threadedregions.ThreadedRegionizer");
			folia = true;
		} catch (ClassNotFoundException ignored) {
			// not Folia
		}
		FOLIA = folia;
	}

	private FoliaUtils() {
	}

	public static boolean isFolia() {
		return FOLIA;
	}

	/** Runs a task on a thread independent of the server tick loop. */
	public static void runAsync(Runnable runnable) {
		if (FOLIA) {
			Bukkit.getAsyncScheduler().runNow(MusicBox.getInstance(), task -> runnable.run());
		} else {
			Bukkit.getScheduler().runTaskAsynchronously(MusicBox.getInstance(), runnable);
		}
	}

	/**
	 * Runs a task on the global region thread (Folia) or the main thread (Paper).
	 * Only safe for server-global state, never for block/entity access in a loaded
	 * region. Prefer {@link #runAtLocation(Location, Runnable)} when a location is
	 * available.
	 */
	public static void runSyncGlobal(Runnable runnable) {
		if (FOLIA) {
			Bukkit.getGlobalRegionScheduler().run(MusicBox.getInstance(), task -> runnable.run());
		} else {
			Bukkit.getScheduler().runTask(MusicBox.getInstance(), runnable);
		}
	}

	/**
	 * Runs a task on the region that owns the given location. Safe for block access
	 * on Folia; on Paper this is just the main thread.
	 */
	public static void runAtLocation(Location location, Runnable runnable) {
		if (FOLIA) {
			Bukkit.getRegionScheduler().run(MusicBox.getInstance(), location, task -> runnable.run());
		} else {
			Bukkit.getScheduler().runTask(MusicBox.getInstance(), runnable);
		}
	}

	/** Runs a task on the player's own region thread (Folia) or the main thread (Paper). */
	public static void runAtPlayer(Player player, Runnable runnable) {
		player.getScheduler().execute(MusicBox.getInstance(), runnable, () -> {}, 0L);
	}

	/**
	 * Runs a task after {@code delayTicks} on the region that owns the location.
	 */
	public static void runDelayedAtLocation(Location location, Runnable runnable, long delayTicks) {
		if (FOLIA) {
			Bukkit.getRegionScheduler().runDelayed(MusicBox.getInstance(), location, task -> runnable.run(), delayTicks);
		} else {
			Bukkit.getScheduler().runTaskLater(MusicBox.getInstance(), runnable, delayTicks);
		}
	}

	/**
	 * Runs a repeating task on the region that owns the location. Returns an opaque
	 * handle for {@link #cancel(Object)}.
	 */
	public static Object runAtFixedRateAtLocation(Location location, Runnable runnable, long initialDelayTicks, long periodTicks) {
		if (FOLIA) {
			return Bukkit.getRegionScheduler().runAtFixedRate(
					MusicBox.getInstance(), location, task -> runnable.run(), initialDelayTicks, periodTicks);
		}
		return Bukkit.getScheduler().runTaskTimer(MusicBox.getInstance(), runnable, initialDelayTicks, periodTicks);
	}

	public static void cancel(Object handle) {
		if (handle instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask) {
			((io.papermc.paper.threadedregions.scheduler.ScheduledTask) handle).cancel();
		} else if (handle instanceof org.bukkit.scheduler.BukkitTask) {
			((org.bukkit.scheduler.BukkitTask) handle).cancel();
		}
	}
}