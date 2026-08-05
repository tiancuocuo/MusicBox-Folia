package ru.spliterash.musicbox.db.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.io.File;
import java.util.UUID;

/**
 * A player-uploaded custom disc. The {@link #discId} is a stable unique identifier
 * used by the chat list actions; the actual .nbs file lives at {@link #filePath}.
 */
@Getter
@RequiredArgsConstructor
public class CustomDiscModel {
    private final UUID owner;
    private final String discId;
    private final String songName;
    private final String filePath;
    private final long createdAt;

    public File toFile() {
        return new File(filePath);
    }
}
