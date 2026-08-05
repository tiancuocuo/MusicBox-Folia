package ru.spliterash.musicbox.customPlayers.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import ru.spliterash.musicbox.MusicBox;
import ru.spliterash.musicbox.customPlayers.interfaces.MusicBoxSongPlayer;
import ru.spliterash.musicbox.customPlayers.interfaces.PositionPlayer;
import ru.spliterash.musicbox.customPlayers.objects.SpeakerPlayer;
import ru.spliterash.musicbox.players.PlayerWrapper;
import ru.spliterash.musicbox.utils.FoliaUtils;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are able to hear a {@link PositionPlayer}.
 *
 * <p>On Folia no thread may read a player's location unless it owns that player's
 * region, so the old 100ms async polling loop is replaced by an event-driven model:
 * membership is (re)evaluated from {@link org.bukkit.event.player.PlayerEvent}s,
 * which always fire on the affected player's own region thread. The playback source
 * position is cached ({@link #sourceLocation}) so it can be compared safely from any
 * region thread. Auto-destroy is handled by a lightweight periodic task running on
 * the source's region.</p>
 */
public class RangePlayerModel implements Listener {

	private final MusicBoxSongPlayerModel musicBoxModel;
	private final Set<UUID> players = ConcurrentHashMap.newKeySet();
	private int destroyMillis;
	private volatile long emptyStart = 0;
	private volatile boolean destroyed = false;

	/**
	 * Cached playback source position. For block players this is the fixed block
	 * location; for {@link SpeakerPlayer} it is refreshed on the owner's region.
	 * Readable from any thread (plain data object).
	 */
	private volatile Location sourceLocation;

	/** Owner player for {@link SpeakerPlayer}, else {@code null}. */
	private final Player owner;

	private Object tickerHandle;

	public RangePlayerModel(MusicBoxSongPlayerModel musicBoxModel) {
		this.musicBoxModel = musicBoxModel;
		destroyMillis = MusicBox.getInstance().getConfigObject().getAutoDestroy() * 1000;

		MusicBoxSongPlayer sp = musicBoxModel.getMusicBoxSongPlayer();
		if (sp instanceof SpeakerPlayer) {
			this.owner = ((SpeakerPlayer) sp).getOwner().getPlayer();
			// Securely seed + keep the source position fresh on the owner's region.
			owner.getScheduler().run(MusicBox.getInstance(), task -> sourceLocation = owner.getLocation(), () -> {});
			tickerHandle = owner.getScheduler().runAtFixedRate(MusicBox.getInstance(), task -> {
				if (destroyed) {
					task.cancel();
					return;
				}
				sourceLocation = owner.getLocation();
				autoDestroyTick();
			}, () -> {}, 1L, 10L);
		} else {
			this.owner = null;
			// Fixed block location; safe to read from any thread.
			this.sourceLocation = getSongPlayer().getLocation();
			tickerHandle = FoliaUtils.runAtFixedRateAtLocation(sourceLocation, this::autoDestroyTick, 20, 20);
		}

		players.addAll(getSongPlayer().getPlayers());
		if (owner != null)
			players.add(owner.getUniqueId());

		Bukkit.getPluginManager().registerEvents(this, MusicBox.getInstance());

		// Catch-up: evaluate already-online players so stationary listeners are added
		// without needing to move. Each check runs on that player's own region thread.
		for (Player p : Bukkit.getOnlinePlayers()) {
			if (destroyed) break;
			FoliaUtils.runAtPlayer(p, () -> updateMembership(p));
		}
	}

	/**
	 * Sets the empty duration (ms) after which the SongPlayer is destroyed.
	 * {@code 0} disables auto-destroy.
	 */
	public void setAutoDestroyMillis(int millis) {
		this.destroyMillis = millis;
		emptyStart = 0;
	}

	public PositionPlayer getSongPlayer() {
		return (PositionPlayer) musicBoxModel.getMusicBoxSongPlayer();
	}

	public void destroy() {
		if (destroyed)
			return;
		destroyed = true;
		HandlerList.unregisterAll(this);
		if (tickerHandle != null)
			FoliaUtils.cancel(tickerHandle);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		updateMembership(e.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		updateMembership(e.getPlayer());
	}

	@EventHandler
	public void onWorldChange(PlayerChangedWorldEvent e) {
		updateMembership(e.getPlayer());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onMove(PlayerMoveEvent e) {
		// PlayerTeleportEvent is a subclass of PlayerMoveEvent, so teleports are
		// already covered here.
		if (e.isCancelled())
			return;
		updateMembership(e.getPlayer());
	}

	/**
	 * Re-evaluates whether the given player can hear this SongPlayer. Safe to call
	 * from any player's own region thread.
	 */
	private void updateMembership(Player player) {
		if (destroyed)
			return;
		Location source = sourceLocation;
		if (source == null)
			return;

		boolean hear;
		if (owner != null && owner.equals(player)) {
			hear = true;
		} else {
			hear = PlayerWrapper.getInstance(player).canHearMusic()
					&& player.getWorld().equals(source.getWorld())
					&& player.getLocation().distanceSquared(source) < Math.pow(getSongPlayer().getRange() + 10, 2);
		}

		boolean changed;
		if (hear)
			changed = players.add(player.getUniqueId());
		else
			changed = players.remove(player.getUniqueId());

		if (changed) {
			musicBoxModel.setPlayers(players);
			if (hear)
				emptyStart = 0;
		}
	}

	/**
	 * Runs periodically on the source's region. Destroys the SongPlayer once it has
	 * been empty for {@link #destroyMillis}.
	 */
	private void autoDestroyTick() {
		if (destroyed || destroyMillis <= 0)
			return;
		if (players.isEmpty()) {
			if (emptyStart == 0)
				emptyStart = System.currentTimeMillis();
			else if (System.currentTimeMillis() - emptyStart >= destroyMillis)
				getSongPlayer().destroy();
		} else {
			emptyStart = 0;
		}
	}
}