package ru.spliterash.musicbox.customDiscs.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ru.spliterash.musicbox.MusicBox;
import ru.spliterash.musicbox.MusicBoxConfig;
import ru.spliterash.musicbox.customDiscs.CustomDiscManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Minimal embedded web server that serves the upload page and accepts raw .nbs
 * file uploads (POST /upload?token=...). Uses the JDK's built-in HttpServer, so
 * no extra dependencies are shaded into the plugin.
 *
 * <p>Handlers run on a dedicated daemon thread pool and never touch Bukkit
 * directly; the heavy lifting happens in {@link CustomDiscManager} which
 * dispatches player-facing work back onto the player's region thread.
 */
public class UploadServer {
    private final MusicBoxConfig.UploadSetting setting;
    private final String page;
    private HttpServer server;
    private ExecutorService executor;

    public UploadServer(MusicBoxConfig.UploadSetting setting) {
        this.setting = setting;
        this.page = loadPage();
    }

    private static String loadPage() {
        try (InputStream in = MusicBox.class.getResourceAsStream("/web/index.html")) {
            if (in == null)
                return "<html><body><h1>upload page missing</h1></body></html>";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1)
                out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "<html><body><h1>upload page error</h1></body></html>";
        }
    }

    public void start() throws IOException {
        InetSocketAddress address;
        String host = setting.getHost();
        if (host == null || host.trim().isEmpty() || host.equals("0.0.0.0"))
            address = new InetSocketAddress(setting.getPort());
        else
            address = new InetSocketAddress(host, setting.getPort());
        server = HttpServer.create(address, 0);
        executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "MusicBox-Upload");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if (path.equals("/") || path.equals("/index.html")) {
                if (method.equals("GET")) {
                    respond(exchange, 200, "text/html; charset=utf-8", page.getBytes(StandardCharsets.UTF_8));
                } else {
                    respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                }
                return;
            }
            if (path.equals("/upload")) {
                if (!method.equals("POST")) {
                    respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed".getBytes(StandardCharsets.UTF_8));
                    return;
                }
                String token = queryParam(exchange, "token");
                if (token == null || token.isEmpty()) {
                    respond(exchange, 400, "application/json; charset=utf-8",
                            UploadResult.fail("missing token").toJson().getBytes(StandardCharsets.UTF_8));
                    return;
                }
                byte[] body = readLimited(exchange.getRequestBody(), setting.getMaxFileSize());
                if (body == null) {
                    double mb = Math.floor(setting.getMaxFileSize() / 1024D / 1024D * 10) / 10;
                    respond(exchange, 413, "application/json; charset=utf-8",
                            UploadResult.fail("file too large (max " + mb + " MB)").toJson().getBytes(StandardCharsets.UTF_8));
                    return;
                }
                UploadResult result = CustomDiscManager.getInstance().handleUpload(token, body);
                respond(exchange, 200, "application/json; charset=utf-8",
                        result.toJson().getBytes(StandardCharsets.UTF_8));
                return;
            }
            if (path.equals("/status")) {
                String token = queryParam(exchange, "token");
                if (token == null || token.isEmpty()) {
                    respond(exchange, 400, "application/json; charset=utf-8",
                            UploadResult.fail("missing token").toJson().getBytes(StandardCharsets.UTF_8));
                    return;
                }
                respond(exchange, 200, "application/json; charset=utf-8",
                        CustomDiscManager.getInstance().statusJson(token).getBytes(StandardCharsets.UTF_8));
                return;
            }
            respond(exchange, 404, "text/plain; charset=utf-8", "Not Found".getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            try {
                respond(exchange, 500, "text/plain; charset=utf-8",
                        ("Server error: " + ex).getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        } finally {
            exchange.close();
        }
    }

    private static String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isEmpty())
            return null;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0)
                continue;
            String k = decode(pair.substring(0, eq));
            if (k.equals(key))
                return decode(pair.substring(eq + 1));
        }
        return null;
    }

    private static String decode(String s) {
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8.name());
        } catch (Exception ex) {
            return s;
        }
    }

    /**
     * Reads up to {@code max+1} bytes. Returns {@code null} when the body exceeds
     * the limit, otherwise the full body.
     */
    private static byte[] readLimited(InputStream in, long max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) != -1) {
            total += n;
            if (total > max)
                return null;
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void respond(HttpExchange exchange, int code, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
