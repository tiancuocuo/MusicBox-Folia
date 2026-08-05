package ru.spliterash.musicbox.customDiscs;

import com.cryptomorin.xseries.XMaterial;
import com.xxmicloxx.NoteBlockAPI.model.Song;
import com.xxmicloxx.NoteBlockAPI.utils.NBSDecoder;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.spliterash.musicbox.Lang;
import ru.spliterash.musicbox.MusicBox;
import ru.spliterash.musicbox.MusicBoxConfig;
import ru.spliterash.musicbox.customDiscs.web.UploadResult;
import ru.spliterash.musicbox.customDiscs.web.UploadServer;
import ru.spliterash.musicbox.db.DatabaseLoader;
import ru.spliterash.musicbox.db.model.CustomDiscModel;
import ru.spliterash.musicbox.db.utils.ResultSetRow;
import ru.spliterash.musicbox.song.MusicBoxSong;
import ru.spliterash.musicbox.song.MusicBoxSongManager;
import ru.spliterash.musicbox.utils.BukkitUtils;
import ru.spliterash.musicbox.utils.EconomyUtils;
import ru.spliterash.musicbox.utils.FoliaUtils;
import ru.spliterash.musicbox.utils.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the player custom-disc feature: upload slots, the song registry entries
 * for uploaded .nbs files, and the give/delete flows behind the chat buttons.
 *
 * <p>Thread model: DB calls and file IO happen on async / web-server threads
 * (never on a region thread). Player-facing responses are dispatched back to the
 * player's own region via {@link FoliaUtils#runAtPlayer}. The song registry in
 * {@link MusicBoxSongManager} is updated through atomic volatile swaps.
 */
public final class CustomDiscManager {
    private static CustomDiscManager instance;
    private final ConcurrentHashMap<String, MusicBoxSong> songsByDiscId = new ConcurrentHashMap<>();
    private UploadServer uploadServer;

    public static CustomDiscManager getInstance() {
        if (instance == null)
            instance = new CustomDiscManager();
        return instance;
    }

    private MusicBoxConfig.UploadSetting getSetting() {
        MusicBoxConfig config = MusicBox.getInstance().getConfigObject();
        return config == null ? null : config.getUpload();
    }

    // ================= lifecycle =================

    /**
     * Reloads custom discs from the database, re-registers their songs and
     * (re)starts the upload web server. Must run on an async thread.
     */
    public void reload() {
        stopServer();
        songsByDiscId.clear();
        List<CustomDiscModel> discs = DatabaseLoader.getBase().getAllCustomDiscs();
        List<MusicBoxSong> songs = new ArrayList<>(discs.size());
        for (CustomDiscModel disc : discs) {
            File file = disc.toFile();
            if (!file.isFile()) {
                // keep the record so the player can still see and delete it in the list
                MusicBox.getInstance().getLogger().warning(
                        "Custom disc file missing: " + disc.getFilePath() + " (record kept for cleanup)");
                continue;
            }
            try {
                MusicBoxSong song = new MusicBoxSong(file, disc.getSongName());
                songs.add(song);
                songsByDiscId.put(disc.getDiscId(), song);
            } catch (Exception ex) {
                MusicBox.getInstance().getLogger().warning(
                        "Can't register custom disc " + disc.getDiscId() + ": " + ex.getMessage());
            }
        }
        MusicBoxSongManager.registerCustomSongs(songs);

        MusicBoxConfig.UploadSetting setting = getSetting();
        if (setting != null && setting.isEnabled()) {
            try {
                uploadServer = new UploadServer(setting);
                uploadServer.start();
                MusicBox.getInstance().getLogger().info("Upload server started on " + setting.getHost() + ":" + setting.getPort());
            } catch (IOException ex) {
                MusicBox.getInstance().getLogger().severe("Can't start upload server: " + ex.getMessage());
            }
        }
    }

    public void stopServer() {
        if (uploadServer != null) {
            uploadServer.stop();
            uploadServer = null;
        }
    }

    // ================= upload slot creation (in-game command) =================

    public void startUpload(Player player, String name) {
        MusicBoxConfig.UploadSetting setting = getSetting();
        if (setting == null || !setting.isEnabled()) {
            player.sendMessage(Lang.UPLOAD_SERVER_DISABLED.toString());
            return;
        }
        if (!EconomyUtils.isEnable()) {
            player.sendMessage(Lang.ECONOMY_DISABLED.toString());
            return;
        }
        if (!hasDiscItem(player)) {
            player.sendMessage(Lang.UPLOAD_NO_DISC.toString());
            return;
        }
        UUID owner = player.getUniqueId();
        FoliaUtils.runAsync(() -> {
            int count = DatabaseLoader.getBase().countCustomDiscs(owner);
            if (count >= setting.getMaxDiscs()) {
                runAtPlayerIfOnline(player, () -> player.sendMessage(
                        Lang.UPLOAD_LIMIT.toString("{max}", String.valueOf(setting.getMaxDiscs()))));
                return;
            }
            String token = UUID.randomUUID().toString();
            DatabaseLoader.getBase().createUploadSlot(token, owner, name);
            runAtPlayerIfOnline(player, () -> {
                if (!player.isOnline()) {
                    // slot created but player left: drop the slot
                    FoliaUtils.runAsync(() -> DatabaseLoader.getBase().consumeUploadSlot(token));
                    return;
                }
                if (!EconomyUtils.canBuy(player, setting.getUploadPrice())) {
                    FoliaUtils.runAsync(() -> DatabaseLoader.getBase().consumeUploadSlot(token));
                    return;
                }
                EconomyUtils.buyNoMessage(player, setting.getUploadPrice());
                removeOneDisc(player);
                sendUploadLink(player, token, name, setting);
            });
        });
    }

    private void sendUploadLink(Player player, String token, String name, MusicBoxConfig.UploadSetting setting) {
        String url = setting.getDisplayUrl();
        if (url == null || url.trim().isEmpty())
            url = "http://" + setting.getHost() + ":" + setting.getPort();
        String separator = url.contains("?") ? "&" : "?";
        String fullUrl = url + separator + "token=" + token;
        TextComponent link = new TextComponent(Lang.UPLOAD_LINK.toString());
        link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, fullUrl));
        link.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(Lang.UPLOAD_LINK_HOVER.toString("{custom_name}", name))));
        player.sendMessage(Lang.UPLOAD_SLOT_CREATED.toString(
                "{minutes}", String.valueOf(setting.getTokenExpireMinutes())));
        player.spigot().sendMessage(link);
    }

    // ================= web upload =================

    /**
     * Called from the web-server thread. Validates the token, the .nbs payload and
     * the per-player limit, then persists the file and registers the song.
     */
    public UploadResult handleUpload(String token, byte[] bytes) {
        MusicBoxConfig.UploadSetting setting = getSetting();
        if (setting == null || !setting.isEnabled())
            return UploadResult.fail(Lang.UPLOAD_SERVER_DISABLED.toPlainText());

        ResultSetRow slot = DatabaseLoader.getBase().getUploadSlot(token);
        if (slot == null)
            return UploadResult.fail(Lang.UPLOAD_INVALID_TOKEN.toPlainText());

        int expireMinutes = setting.getTokenExpireMinutes();
        long createdAt = Long.parseLong(slot.getString("created_at"));
        if (expireMinutes > 0 && System.currentTimeMillis() - createdAt > expireMinutes * 60_000L) {
            DatabaseLoader.getBase().consumeUploadSlot(token);
            return UploadResult.fail(Lang.UPLOAD_EXPIRED.toPlainText());
        }

        if (bytes.length > setting.getMaxFileSize()) {
            double mb = Math.floor(setting.getMaxFileSize() / 1024D / 1024D * 10) / 10;
            return UploadResult.fail(Lang.UPLOAD_TOO_BIG.toPlainText("{max}", String.valueOf(mb)));
        }

        UUID owner = UUID.fromString(slot.getString("owner"));
        String songName = slot.getString("song_name");

        Song song;
        try {
            song = NBSDecoder.parse(new ByteArrayInputStream(bytes));
        } catch (Exception ex) {
            song = null;
        }
        if (song == null)
            return UploadResult.fail(Lang.UPLOAD_NOT_NBS.toPlainText());

        if (DatabaseLoader.getBase().countCustomDiscs(owner) >= setting.getMaxDiscs()) {
            DatabaseLoader.getBase().consumeUploadSlot(token);
            return UploadResult.fail(Lang.UPLOAD_LIMIT.toPlainText("{max}", String.valueOf(setting.getMaxDiscs())));
        }

        String discId = UUID.randomUUID().toString();
        File dir = new File(new File(MusicBox.getInstance().getDataFolder(), "custom"), owner.toString());
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File file = new File(dir, discId + ".nbs");
        try {
            Files.write(file.toPath(), bytes);
        } catch (IOException ex) {
            MusicBox.getInstance().getLogger().severe("Can't save custom disc file " + file + ": " + ex);
            return UploadResult.fail(Lang.UPLOAD_NOT_NBS.toPlainText());
        }

        try {
            DatabaseLoader.getBase().saveCustomDisc(new CustomDiscModel(
                    owner, discId, songName, file.getAbsolutePath(), System.currentTimeMillis()));
            DatabaseLoader.getBase().consumeUploadSlot(token);

            MusicBoxSong mbSong = new MusicBoxSong(file, songName);
            songsByDiscId.put(discId, mbSong);
            MusicBoxSongManager.registerCustomSongs(Collections.singletonList(mbSong));

            // the first copy comes with the upload: auto-give one disc for free
            giveUploadedDisc(owner, mbSong, songName);
        } catch (Exception ex) {
            // roll back the saved file so no orphan accumulates on a DB/registration failure
            try {
                Files.deleteIfExists(file.toPath());
            } catch (IOException ignored) {
            }
            MusicBox.getInstance().getLogger().severe("Can't persist uploaded song " + file + ": " + ex);
            return UploadResult.fail(Lang.UPLOAD_DB_ERROR.toPlainText());
        }

        return UploadResult.ok(Lang.UPLOAD_SUCCESS.toPlainText("{disc}", songName));
    }

    /**
     * Gives the freshly uploaded disc to its owner once, for free (the first copy
     * is included with the upload). Drops it at the player's feet when the
     * inventory is full. Player interaction happens on the player's own region.
     */
    private void giveUploadedDisc(UUID owner, MusicBoxSong song, String songName) {
        try {
            Player player = Bukkit.getPlayer(owner);
            if (player == null || !player.isOnline())
                return;
            FoliaUtils.runAtPlayer(player, () -> {
                if (!player.isOnline())
                    return;
                ItemStack stack = song.getSongStack();
                HashMap<Integer, ItemStack> left = player.getInventory().addItem(stack);
                if (!left.isEmpty()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), stack);
                }
                player.sendMessage(Lang.UPLOAD_SUCCESS.toString("{disc}", songName));
            });
        } catch (Exception ignored) {
            // player went offline mid-flight
        }
    }

    // ================= list / give / delete =================

    /**
     * Returns the JSON payload for the upload page's /status endpoint.
     */
    public String statusJson(String token) {
        MusicBoxConfig.UploadSetting setting = getSetting();
        if (setting == null || !setting.isEnabled())
            return "{\"ok\":false,\"message\":\"upload service disabled\"}";
        ResultSetRow slot = DatabaseLoader.getBase().getUploadSlot(token);
        if (slot == null)
            return "{\"ok\":false,\"message\":\"" + escapeJson(Lang.UPLOAD_INVALID_TOKEN.toPlainText()) + "\"}";
        int expireMinutes = setting.getTokenExpireMinutes();
        long createdAt = Long.parseLong(slot.getString("created_at"));
        if (expireMinutes > 0 && System.currentTimeMillis() - createdAt > expireMinutes * 60_000L) {
            DatabaseLoader.getBase().consumeUploadSlot(token);
            return "{\"ok\":false,\"message\":\"" + escapeJson(Lang.UPLOAD_EXPIRED.toPlainText()) + "\"}";
        }
        UUID owner = UUID.fromString(slot.getString("owner"));
        int count = DatabaseLoader.getBase().countCustomDiscs(owner);
        return "{\"ok\":true,\"count\":" + count + ",\"max\":" + setting.getMaxDiscs()
                + ",\"expireMinutes\":" + expireMinutes + "}";
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void sendDiscList(Player player) {
        MusicBoxConfig.UploadSetting setting = getSetting();
        int max = setting == null ? 5 : setting.getMaxDiscs();
        FoliaUtils.runAsync(() -> {
            List<CustomDiscModel> discs = DatabaseLoader.getBase().getCustomDiscs(player.getUniqueId());
            runAtPlayerIfOnline(player, () -> {
                player.sendMessage(Lang.MYDISCS_TITLE.toString(
                        "{count}", String.valueOf(discs.size()),
                        "{max}", String.valueOf(max)));
                if (discs.isEmpty()) {
                    player.sendMessage(Lang.MYDISCS_EMPTY.toString());
                    return;
                }
                for (int i = 0; i < discs.size(); i++) {
                    player.spigot().sendMessage(buildDiscLine(i + 1, discs.get(i), setting));
                }
            });
        });
    }

    private BaseComponent[] buildDiscLine(int num, CustomDiscModel disc, MusicBoxConfig.UploadSetting setting) {
        List<BaseComponent> parts = new ArrayList<>();
        // fromLegacyText only understands § codes, so translate & colours first
        Collections.addAll(parts, TextComponent.fromLegacyText(
                StringUtils.t("&7" + num + ". &b" + disc.getSongName() + "  ")));

        TextComponent give = new TextComponent(Lang.MYDISCS_GIVE.toString());
        give.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/musicbox givecd " + disc.getDiscId()));
        give.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(Lang.MYDISCS_GIVE_HOVER.toString("{price}", String.valueOf(setting.getGivePrice())))));
        parts.add(give);

        parts.add(new TextComponent("  "));

        TextComponent del = new TextComponent(Lang.MYDISCS_DELETE.toString());
        del.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/musicbox delcd " + disc.getDiscId()));
        del.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(Lang.MYDISCS_DELETE_HOVER.toString())));
        parts.add(del);

        return parts.toArray(new BaseComponent[0]);
    }

    public void giveDisc(Player player, String discId) {
        MusicBoxConfig.UploadSetting setting = getSetting();
        if (setting == null || !EconomyUtils.isEnable()) {
            player.sendMessage(Lang.ECONOMY_DISABLED.toString());
            return;
        }
        FoliaUtils.runAsync(() -> {
            try {
                CustomDiscModel disc = DatabaseLoader.getBase().getCustomDisc(discId);
                if (disc == null || !disc.getOwner().equals(player.getUniqueId())) {
                    runAtPlayerIfOnline(player, () -> player.sendMessage(Lang.DISC_NOT_FOUND.toString()));
                    return;
                }
                MusicBoxSong song = songsByDiscId.get(discId);
                if (song == null) {
                    // self-heal: the registry may have lost the entry (e.g. a reload raced)
                    song = reRegister(disc);
                }
                if (song == null) {
                    runAtPlayerIfOnline(player, () -> player.sendMessage(Lang.DISC_FILE_MISSING.toString()));
                    return;
                }
                final MusicBoxSong finalSong = song;
                runAtPlayerIfOnline(player, () -> {
                    if (!player.isOnline())
                        return;
                    if (!EconomyUtils.canBuy(player, setting.getGivePrice()))
                        return;
                    ItemStack stack = finalSong.getSongStack();
                    HashMap<Integer, ItemStack> left = player.getInventory().addItem(stack);
                    if (!left.isEmpty()) {
                        player.sendMessage(Lang.NO_INVENTORY_SPACE.toString());
                        return;
                    }
                    EconomyUtils.buyNoMessage(player, setting.getGivePrice());
                    player.sendMessage(Lang.DISC_GIVEN.toString("{disc}", finalSong.getName()));
                });
            } catch (Exception ex) {
                MusicBox.getInstance().getLogger().severe("givecd failed for " + discId + ": " + ex);
                runAtPlayerIfOnline(player, () -> player.sendMessage(Lang.DISC_ERROR.toString()));
            }
        });
    }

    /**
     * Re-creates and re-registers the {@link MusicBoxSong} for a disc from its stored
     * file, so a lost registry entry self-heals instead of failing the give.
     */
    private MusicBoxSong reRegister(CustomDiscModel disc) {
        File file = disc.toFile();
        if (!file.isFile())
            return null;
        try {
            MusicBoxSong song = new MusicBoxSong(file, disc.getSongName());
            songsByDiscId.put(disc.getDiscId(), song);
            MusicBoxSongManager.registerCustomSongs(Collections.singletonList(song));
            return song;
        } catch (Exception ex) {
            MusicBox.getInstance().getLogger().warning(
                    "Can't re-register custom disc " + disc.getDiscId() + ": " + ex);
            return null;
        }
    }

    /**
     * Shows a click-to-confirm prompt before actually deleting a disc.
     */
    public void confirmDelete(Player player, String discId) {
        FoliaUtils.runAsync(() -> {
            CustomDiscModel disc = DatabaseLoader.getBase().getCustomDisc(discId);
            if (disc == null || !disc.getOwner().equals(player.getUniqueId())) {
                runAtPlayerIfOnline(player, () -> player.sendMessage(Lang.DISC_NOT_FOUND.toString()));
                return;
            }
            runAtPlayerIfOnline(player, () -> {
                TextComponent confirm = new TextComponent(StringUtils.t(
                        Lang.MYDISCS_DELETE.toString() + " &7(再次点击确认)"));
                confirm.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        "/musicbox delcd " + discId + " confirm"));
                player.spigot().sendMessage(confirm);
            });
        });
    }

    public void deleteDisc(Player player, String discId) {
        FoliaUtils.runAsync(() -> {
            CustomDiscModel disc = DatabaseLoader.getBase().getCustomDisc(discId);
            if (disc == null || !disc.getOwner().equals(player.getUniqueId())) {
                FoliaUtils.runAtPlayer(player, () -> player.sendMessage(Lang.DISC_NOT_FOUND.toString()));
                return;
            }
            DatabaseLoader.getBase().deleteCustomDisc(discId);
            File file = disc.toFile();
            if (file.isFile() && !deleteFileWithRetry(file)) {
                MusicBox.getInstance().getLogger().warning(
                        "Can't delete custom disc file (may be locked), left as orphan: " + file);
            }
            MusicBoxSong song = songsByDiscId.remove(discId);
            if (song != null)
                MusicBoxSongManager.unregisterCustomSong(song);
            runAtPlayerIfOnline(player, () ->
                    player.sendMessage(Lang.DISC_DELETED.toString("{disc}", disc.getSongName())));
        });
    }

    /**
     * Deletes a file, retrying once after a short delay: on Windows a freshly
     * written file can be briefly locked (antivirus / indexer).
     */
    private static boolean deleteFileWithRetry(File file) {
        try {
            Files.deleteIfExists(file.toPath());
            return true;
        } catch (IOException | SecurityException ignored) {
        }
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            Files.deleteIfExists(file.toPath());
            return true;
        } catch (IOException | SecurityException ignored) {
            return false;
        }
    }

    private static void runAtPlayerIfOnline(Player player, Runnable runnable) {
        try {
            if (player.isOnline())
                FoliaUtils.runAtPlayer(player, runnable);
        } catch (Exception ignored) {
            // player went offline mid-flight
        }
    }

    // ================= helpers =================

    private static boolean isDisc(ItemStack item) {
        if (item == null)
            return false;
        return BukkitUtils.DISCS.contains(XMaterial.matchXMaterial(item.getType()));
    }

    private boolean hasDiscItem(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDisc(item))
                return true;
        }
        return false;
    }

    private void removeOneDisc(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isDisc(item)) {
                ItemStack one = item.clone();
                one.setAmount(1);
                player.getInventory().removeItem(one);
                return;
            }
        }
    }
}
