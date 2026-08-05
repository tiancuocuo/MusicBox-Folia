package ru.spliterash.musicbox.customDiscs.web;

/**
 * JSON-friendly result of a web upload attempt.
 */
public final class UploadResult {
    public final boolean ok;
    public final String message;

    private UploadResult(boolean ok, String message) {
        this.ok = ok;
        this.message = message;
    }

    public static UploadResult ok(String message) {
        return new UploadResult(true, message);
    }

    public static UploadResult fail(String message) {
        return new UploadResult(false, message);
    }

    public String toJson() {
        String msg = message == null ? "" : message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        return "{\"ok\":" + ok + ",\"message\":\"" + msg + "\"}";
    }
}
