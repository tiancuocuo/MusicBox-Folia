package ru.spliterash.musicbox.minecraft.nms.jukebox;

import org.bukkit.Bukkit;
import org.bukkit.block.Jukebox;
import ru.spliterash.musicbox.minecraft.nms.NMSUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;

public class JukeboxFactory {
    private static final String START_PATH = "ru.spliterash.musicbox.minecraft.nms.jukebox.versions.";
    private static final Class<? extends IJukebox> clazz;

    static {
        String raw = NMSUtils.getRawVersion();
        int iV = NMSUtils.parseMajorVersion(raw);

        String className;
        if (iV >= 26) {
            // Calendar-versioned servers (26.x): pure-API implementation.
            className = START_PATH + "V26_2";
        } else if (iV == 21) {
            switch (raw) {
                case "1.21":
                case "1.21.1":
                    className = START_PATH + "V21";
                    break;
                case "1.21.2":
                case "1.21.3":
                    className = START_PATH + "V21_2";
                    break;
                default:
                    className = null;
                    break;
            }
        } else if (iV == 20) {
            switch (raw) {
                case "1.20":
                case "1.20.1":
                    className = START_PATH + "V20_1";
                    break;
                case "1.20.2":
                    className = START_PATH + "V20_2";
                    break;
                case "1.20.3":
                case "1.20.4":
                    className = START_PATH + "V20_3";
                    break;
                case "1.20.5":
                case "1.20.6":
                default:
                    className = START_PATH + "V20_5";
                    break;
            }
        } else if (iV == 19) {
            switch (raw) {
                case "1.19.2":
                    className = START_PATH + "V19_2";
                    break;
                case "1.19.3":
                    className = START_PATH + "V19_3";
                    break;
                case "1.19.4":
                    className = START_PATH + "V19_4";
                    break;
                default:
                    className = null;
                    break;
            }
        } else if (iV == 18)
            className = START_PATH + "V18";
        else if (iV == 17)
            className = START_PATH + "V17";
        else if (iV >= 13)
            className = START_PATH + "V13_16";
        else if (iV == 12)
            className = START_PATH + "V12";
        else
            className = null;

        Class<? extends IJukebox> tmpClass = null;
        if (className != null) {
            try {
                //noinspection unchecked
                tmpClass = (Class<? extends IJukebox>) Class.forName(className);
            } catch (Throwable t) {
                // Degrade gracefully: the jukebox feature becomes unavailable but the
                // rest of the plugin keeps working.
                Bukkit.getLogger().log(Level.WARNING, "[MusicBox] Jukebox support unavailable for " + raw, t);
            }
        } else {
            Bukkit.getLogger().warning("[MusicBox] Unsupported version for jukeboxes: " + raw);
        }
        clazz = tmpClass;
    }

    public static boolean jukeboxAvailable() {
        return clazz != null;
    }

    public static IJukebox getJukebox(Jukebox jukebox) {
        try {
            return clazz.getConstructor(Jukebox.class).newInstance(jukebox);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
