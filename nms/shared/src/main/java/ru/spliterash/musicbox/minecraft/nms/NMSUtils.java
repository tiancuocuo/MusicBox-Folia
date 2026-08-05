package ru.spliterash.musicbox.minecraft.nms;

import org.bukkit.Bukkit;

public class NMSUtils {

    public static int parseMajorVersion(String raw) {
        // Old-style versions look like "1.21.3": the feature version is the second
        // number. Calendar-versioned releases (26.x and up) look like "26.2": the
        // first number IS the major version.
        if (raw.startsWith("1.")) {
            int firstDotIndex = raw.indexOf(".");
            raw = raw.substring(firstDotIndex + 1);
            int secondDotIndex = raw.indexOf(".");
            if (secondDotIndex != -1)
                raw = raw.substring(0, secondDotIndex);
            return Integer.parseInt(raw);
        }
        int dotIndex = raw.indexOf(".");
        if (dotIndex != -1)
            raw = raw.substring(0, dotIndex);
        return Integer.parseInt(raw);
    }

    public static String getRawVersion() {
        String strVersion = Bukkit.getVersion();

        int start = strVersion.indexOf("(MC: ") + 5;

        strVersion = strVersion.substring(start);

        int end = strVersion.indexOf(")");

        strVersion = strVersion.substring(0, end);

        return strVersion;
    }

    public static int getVersion() {
        return parseMajorVersion(getRawVersion());
    }
}
