# Requirements

<!-- Paste your AI-generated requirements here -->

Please build the complete implementation of the AdvertiseMod Minecraft plugin by strictly following the requirements below.

================================================================================
REQUIREMENTS SPECIFICATION: AdvertiseMod
================================================================================

1. Executive Summary & Objective
AdvertiseMod is a custom Paper/Folia-compatible Minecraft server plugin paired with an embedded HTTP microservice. It allows players on the Cozy Minecraft server to register Barrels as sales points within the server's Shopping District.

The plugin continuously monitors stock levels (including deep-scanning the contents of Shulker Boxes inside barrels) and exposes a REST API and searchable web frontend, enabling players to find items, check stock levels, compare prices, and locate shops via the server's website.

--------------------------------------------------------------------------------

2. Technical Stack & Environment Requirements (Canvas 26.2 & Velocity Proxy)
- Target Server Platform: CanvasMC 26.2 (Folia-based backend) behind a Velocity Proxy
- Client Version: Minecraft 1.26.2
- Language Runtime: Java 21 LTS
- Architecture & Network Topology:
  * Network Gateway: Velocity Proxy routes player connections from the client (1.26.2) to backend server instances.
  * Game Backend: CanvasMC 26.2 hosts the shopping district world, terrain chunks, block entities (Barrels), and inventories.
  * Placement of AdvertiseMod: Deployed directly on the CanvasMC backend server where world blocks, containers, and events live.
  * Velocity Forwarding: Player identification (`player.getUniqueId()` and `player.getName()`) leverages Velocity Modern Forwarding configured on the Canvas backend.
- Concurrency & Threading Model (Canvas / Folia Native):
  * plugin.yml explicitly sets 'folia-supported: true' (enforced by CanvasMC).
  * Strict Region Scheduling:
    - Block entity checks, barrel inventory reads, and tile validations execute strictly via Folia/Canvas RegionScheduler or on the native region thread inside Bukkit event handlers.
    - Off-region / asynchronous chunk accesses are forbidden.
  * AsyncScheduler for I/O:
    - Embedded SQLite queries (shops.db), embedded HTTP WebServer execution, and periodic stock audit timers execute asynchronously via Folia/Canvas AsyncScheduler.
- Database: Embedded SQLite (shops.db with WAL mode enabled).
- Build Tool: Maven (standard Maven directory layout, Java 21 release target).

--------------------------------------------------------------------------------

3. Configuration & Bounding Limits (config.yml)

Default Configuration File Structure:
------------------------------------
web_server:
  port: 8080
  enabled: true

limits:
  max_barrels_per_player: 64

shopping_district:
  enforce_area_limits: true
  allowed_dimensions:
    - "world_the_end"
    - "the_sift" # Reserved for future dimension release
  bounds:
    min_x: -1000
    max_x: 1000
    min_y: 0
    max_y: 256
    min_z: -1000
    max_z: 1000

scanning:
  audit_interval_seconds: 30

Boundary & Dimension Validation Logic:
-------------------------------------
- When a player executes /advertise start, the plugin validates the barrel's location:
  1. The barrel must reside within an allowed dimension specified in shopping_district.allowed_dimensions.
  2. The barrel's (x, y, z) coordinates must fall within the bounding box defined in shopping_district.bounds.
- If validation fails, abort registration and notify the player in chat.

--------------------------------------------------------------------------------

4. Commands, Syntax & Permissions

Player Commands (/advertise or alias /adv):
Permission: cozy.advertise.use (Default: true)

1. /advertise help
   - Displays an in-game formatted guide detailing command syntax, registration rules, currency rules, and allowed shopping district boundaries.

2. /advertise start <price_amount> <price_unit> <sell_amount> <sell_unit> [shop_name]
   - Prerequisites:
     * The player MUST be looking directly at a BARREL block within 5 blocks when the command is run. The barrel inventory does not need to be open.
     * If the targeted block is not a BARREL, or no block is targeted within 5 blocks, the plugin MUST show an error and not register anything.
     * The barrel MUST be within allowed dimensions and spatial boundaries.
     * The barrel MUST NOT already be registered by another player (Ownership Lock).
     * The registering player MUST NOT exceed limits.max_barrels_per_player (Default: 64).
   - Arguments & Tab Completion (Brigadier/Paper API):
     * <price_amount>: Positive Integer.
     * <price_unit>: Enum [diamond, diamond_block].
     * <sell_amount>: Positive Integer.
     * <sell_unit>: Enum [item, stack, shulker].
     * [shop_name]: Optional trailing String (e.g., "The Diamond Den").

3. /advertise stop
   - Universal Unregister: Any player can run this command while having an advertised barrel open to unregister it (e.g., cleaning up abandoned/empty shops).
   - Appends an unregistration record in SQLite audit_logs under action 'UNREGISTER_BY_PLAYER'.

4. /advertise status
   - Displays the advertisement status, shop name, price rule, remaining item count, and recent audit history for the currently open barrel.

Admin Commands (/advertise admin <subcommand>):
Permission: cozy.advertise.admin (Default: op)

- /advertise admin reload
  * Reloads config.yml (bounds, dimensions, limits, scan interval) without restarting the server.
- /advertise admin remove <world> <x> <y> <z>
  * Forcefully unregisters a barrel at the specified coordinates and logs 'ADMIN_REMOVE'.
- /advertise admin setlimit <player_name|default> <amount>
  * Dynamically updates the max barrel limit per player in memory and config.
- /advertise admin setbounds <minX> <maxX> <minY> <maxY> <minZ> <maxZ>
  * Updates the shopping district bounding box coordinates dynamically.

--------------------------------------------------------------------------------

5. Barrel & Inventory Scanning Engine

Scanning Rules:
1. Target Container: Strictly limited to BARREL block entities.
2. Currency Exclusion: Exclude all DIAMOND and DIAMOND_BLOCK items from sales inventory counting.
3. Display / Private Item Exclusion: Ignore any item whose DisplayName or Material name begins with an asterisk (*).
4. Multi-Item Barrels:
   - Barrels can contain multiple distinct item types.
   - All non-excluded items share the same pricing rule configured during /advertise start.
   - On the web API and UI, distinct items in a mixed barrel MUST be exposed as separate listings associated with the same barrel location and pricing.
5. Shulker Box Deep Inspection:
   - Inspect and count all Shulker Boxes inside the barrel.
   - Recursively inspect internal NBT/block entity tags of each Shulker Box to index all internal contents (material, item count, display name, lore).
   - Expose both top-level Shulker count and nested item inventories to the web service.

--------------------------------------------------------------------------------

6. Audit Logging & Event Tracking

Database Schema (SQLite shops.db):
----------------------------------
CREATE TABLE IF NOT EXISTS registered_barrels (
    id TEXT PRIMARY KEY, -- Formatted as "world_x_y_z"
    world TEXT NOT NULL,
    x INT NOT NULL,
    y INT NOT NULL,
    z INT NOT NULL,
    owner_uuid TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    shop_name TEXT,
    price_amount INT NOT NULL,
    price_unit TEXT NOT NULL,
    sell_amount INT NOT NULL,
    sell_unit TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    barrel_id TEXT NOT NULL,
    player_uuid TEXT NOT NULL,
    player_name TEXT NOT NULL,
    action TEXT NOT NULL, -- REGISTER, UNREGISTER_BY_PLAYER, BREAK_UNREGISTER, PRICE_CHANGE, ADMIN_REMOVE
    details TEXT,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

Stock Syncing & Lifecycle Events:
---------------------------------
1. Async Background Audit Task: Runs every scanning.audit_interval_seconds (default: 30s) via AsyncScheduler to re-scan stock levels across all registered barrels in loaded regions. Prevents server lag from hopper/redstone events.
2. InventoryCloseEvent: Triggers a non-blocking background stock recount for the specific barrel closed by a player.
3. BlockBreakEvent: If an advertised barrel is broken by any player:
   - Remove the barrel from active database listings.
   - Insert record into audit_logs: action = "BREAK_UNREGISTER".

--------------------------------------------------------------------------------

7. Embedded Web Microservice & API

- Server: Embedded com.sun.net.httpserver.HttpServer listening on web_server.port (default: 8080).
- CORS Support: Headers must include 'Access-Control-Allow-Origin: *'.

Endpoints:
1. GET /api/shops: Returns JSON array of all active shop barrels, individual listings, and deep-scanned Shulker contents.
   JSON Response Format:
   [
     {
       "barrel_id": "world_the_end_150_64_-200",
       "shop_name": "The Diamond Den",
       "owner_name": "Steve",
       "owner_uuid": "069a79f4-44e9-4726-a5be-fef90e38aaf5",
       "location": { "world": "world_the_end", "x": 150, "y": 64, "z": -200 },
       "pricing": {
         "price_amount": 1,
         "price_unit": "diamond",
         "sell_amount": 1,
         "sell_unit": "shulker"
       },
       "listings": [
         {
           "item_type": "SHULKER_BOX",
           "quantity_available": 2,
           "is_shulker": true,
           "shulker_contents": [
             { "item": "DIAMOND_HELMET", "count": 1 },
             { "item": "ELYTRA", "count": 1 },
             { "item": "FIREWORK_ROCKET", "count": 128 }
           ]
         },
         {
           "item_type": "OAK_LOG",
           "quantity_available": 128,
           "is_shulker": false,
           "shulker_contents": []
         }
       ]
     }
   ]

2. GET /: Serves static HTML/JS web interface (src/main/resources/web/index.html).

Web Frontend (index.html):
- Single-page application built with HTML, modern CSS, and vanilla JavaScript.
- Real-time search filter searching by item name, shop name, or seller.
- Deep Search: Searching for a specific item (e.g., "Elytra") highlights Shulker Boxes containing that item and displays expandable item cards detailing full Shulker contents.

================================================================================
DELIVERABLES REQUIRED
================================================================================

Generate all required Java classes and resource files following the standard Maven project structure:
1. pom.xml (Paper 1.20 API + SQLite dependency)
2. src/main/resources/plugin.yml (folia-supported: true)
3. src/main/resources/config.yml
4. AdvertisePlugin.java (Plugin entry point)
5. ConfigManager.java & DatabaseManager.java
6. AdvertiseCommand.java & AdminCommand.java (Brigadier completion & Folia-safe execution)
7. InventoryListener.java & BlockListener.java
8. BarrelScanner.java (Deep Shulker inventory recursion & currency/* exclusions)
9. StockAuditTask.java (AsyncScheduler periodic scanning)
10. WebServer.java (Embedded HttpServer with CORS)
11. src/main/resources/web/index.html (Searchable web portal with expandable Shulker contents)
