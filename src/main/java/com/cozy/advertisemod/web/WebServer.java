package com.cozy.advertisemod.web;

import com.cozy.advertisemod.AdvertisePlugin;
import com.cozy.advertisemod.model.RegisteredBarrel;
import com.cozy.advertisemod.model.ShopFeedItem;
import com.cozy.advertisemod.model.ShopListing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Embedded HTTP microservice serving the /api/shops JSON endpoint
 * and the single-page web application.
 */
public class WebServer {

    private final AdvertisePlugin plugin;
    private final Gson gson;
    private HttpServer server;

    public WebServer(AdvertisePlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Starts the embedded HTTP server.
     */
    public synchronized void start() {
        if (!plugin.getConfigManager().isWebServerEnabled()) {
            plugin.getLogger().info("Web server is disabled in config.yml.");
            return;
        }

        int port = plugin.getConfigManager().getWebServerPort();
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newFixedThreadPool(4));

            server.createContext("/api/shops", new ShopsApiHandler());
            server.createContext("/", new StaticFileHandler());

            server.start();
            plugin.getLogger().info("AdvertiseMod WebServer running on http://localhost:" + port);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start embedded web server on port " + port, e);
        }
    }

    /**
     * Stops the embedded HTTP server.
     */
    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
            plugin.getLogger().info("AdvertiseMod WebServer stopped.");
        }
    }

    private class ShopsApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain");
                return;
            }

            try {
                List<RegisteredBarrel> barrels = plugin.getDatabaseManager().getAllBarrels();
                List<ShopFeedItem> feedItems = new ArrayList<>(barrels.size());

                for (RegisteredBarrel barrel : barrels) {
                    List<ShopListing> listings = plugin.getCachedListings(barrel.getId());
                    if (listings == null) {
                        listings = Collections.emptyList();
                    }
                    feedItems.add(ShopFeedItem.fromBarrel(barrel, listings));
                }

                String json = gson.toJson(feedItems);
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Error generating /api/shops response", t);
                sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}", "application/json");
            }
        }
    }

    private class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.equals("/index.html")) {
                byte[] content = loadIndexHtml();
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content);
                }
            } else {
                sendResponse(exchange, 404, "404 Not Found", "text/plain");
            }
        }
    }

    private byte[] loadIndexHtml() {
        // First, check if custom index.html exists in plugin data folder
        File customFile = new File(plugin.getDataFolder(), "web/index.html");
        if (customFile.exists()) {
            try {
                return Files.readAllBytes(customFile.toPath());
            } catch (IOException ignored) {
            }
        }

        // Fallback to bundled resource in jar
        try (InputStream in = plugin.getResource("web/index.html")) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException ignored) {
        }

        return "<html><body><h1>AdvertiseMod Web UI not found</h1></body></html>".getBytes(StandardCharsets.UTF_8);
    }

    private void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

