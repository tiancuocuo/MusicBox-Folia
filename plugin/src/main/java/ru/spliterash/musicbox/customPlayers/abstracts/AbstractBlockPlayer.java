package ru.spliterash.musicbox.customPlayers.abstracts;

import com.xxmicloxx.NoteBlockAPI.songplayer.PositionSongPlayer;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.IllegalPluginAccessException;
import ru.spliterash.musicbox.MusicBox;
import ru.spliterash.musicbox.customPlayers.interfaces.IPlayList;
import ru.spliterash.musicbox.customPlayers.interfaces.MusicBoxSongPlayer;
import ru.spliterash.musicbox.customPlayers.interfaces.PositionPlayer;
import ru.spliterash.musicbox.customPlayers.models.MusicBoxSongPlayerModel;
import ru.spliterash.musicbox.customPlayers.models.RangePlayerModel;
import ru.spliterash.musicbox.utils.BukkitUtils;
import ru.spliterash.musicbox.utils.FoliaUtils;
import ru.spliterash.musicbox.utils.SignUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Getter
public abstract class AbstractBlockPlayer extends PositionSongPlayer implements PositionPlayer {
    // ConcurrentHashMap: players are created/destroyed/looked up from many Folia
    // region threads concurrently.
    private final static Map<Location, AbstractBlockPlayer> players = new ConcurrentHashMap<>();
    @Getter
    private final static Collection<AbstractBlockPlayer> all = Collections.unmodifiableCollection(players.values());
    private final MusicBoxSongPlayerModel musicBoxModel;
    private final RangePlayerModel rangePlayerModel;
    private final Location location;
    private Object tickerHandle;

    public AbstractBlockPlayer(IPlayList list, Location location, int range) {
        super(list.getCurrent().getSong());
        this.location = BukkitUtils.centerBlock(location);
        setRange(range);
        setTargetLocation(BukkitUtils.centerBlock(location));
        AbstractBlockPlayer oldBlock = players.put(getTargetLocation(), this);
        if (oldBlock != null)
            oldBlock.destroy();
        this.musicBoxModel = new MusicBoxSongPlayerModel(this, list, this::runNextSong);
        this.rangePlayerModel = new RangePlayerModel(musicBoxModel);
        musicBoxModel.runPlayer();
        // Periodic integrity check on the block's own region: a SignPlayer verifies the
        // block is still a powered sign, a JukeboxPlayer that it is still a jukebox.
        tickerHandle = FoliaUtils.runAtFixedRateAtLocation(this.location, () -> {
            if (isDestroyed()) {
                FoliaUtils.cancel(tickerHandle);
                return;
            }
            every100MillisAsync();
        }, 2, 2);
    }

    public static <T extends AbstractBlockPlayer> T findByLocation(Location location) {
        //noinspection unchecked
        return (T) players.get(BukkitUtils.centerBlock(location));
    }

    public static Set<? extends AbstractBlockPlayer> findByChunk(World world, int x, int z) {
        return getAll()
                .stream()
                .filter(e -> BukkitUtils.inChunk(e.getLocation(), world, x, z))
                .collect(Collectors.toSet());

    }

    protected abstract Location getInfoSign();


    protected abstract void every100MillisAsync();

    protected abstract MusicBoxSongPlayer runNextSong(IPlayList list);

    public static <T extends AbstractBlockPlayer> Optional<T> findByInfoSign(Location location) {
        //noinspection unchecked
        return players
                .values()
                .stream()
                .filter(i -> i.getInfoSign() != null && i.getInfoSign().equals(location))
                .findFirst()
                .map(a -> (T) a);
    }

    @Override
    public Location getLocation() {
        return getTargetLocation();
    }

    @Override
    public int getRange() {
        return getDistance();
    }

    @Override
    public void setRange(int range) {
        setDistance(range);
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public void destroy() {
        if (!isDestroyed()) {
            try {
                super.destroy();
            } catch (IllegalPluginAccessException ex) {
                // ПОФИК
            }
            players.values().remove(this);
            boolean normalEnd = musicBoxModel.isSongEndNormal();
            if (normalEnd)
                songEnd();
            Location infoSign = getInfoSign();
            if (infoSign != null) {
                BukkitUtils.runSyncTask(infoSign, () -> {
                    if (!musicBoxModel.isNextCreated())
                        SignUtils.setPlayerOff(infoSign);
                });
            }
            rangePlayerModel.destroy();
            musicBoxModel.destroy();
            if (tickerHandle != null)
                FoliaUtils.cancel(tickerHandle);
        }
    }

    /**
     * Вызывается в случае нормального завершения музыки
     */
    protected abstract void songEnd();
}
