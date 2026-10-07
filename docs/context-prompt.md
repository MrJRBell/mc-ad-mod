# Context Prompt

<!-- Paste your context prompt here -->

Act as a senior software engineer specializing in Minecraft server software and Paper/Folia API plugin development. 

I need you to implement the complete, production-ready codebase for the `AdvertiseMod` plugin and embedded web service. All requirements, architecture rules, database schemas, command syntaxes, and API structures are fully detailed in the project's `requirements.md` file (or `prompt.txt`).

### Implementation Instructions:

1. **Strict Folia Threading Rules:**
   - Ensure `plugin.yml` sets `folia-supported: true`.
   - All world state inspections, block entity reads, and tile checks must use `RegionScheduler` or execute natively insideBukkit event handlers.
   - All I/O operations (SQLite database queries, embedded HTTP server execution, and background scheduled tasks) MUST run asynchronously using Folia's `AsyncScheduler`.

2. **Core Functionality to Implement:**
   - **Command Handlers (`/advertise` & `/advertise admin`):** Implement full Brigadier tab completion for subcommands, pricing units (`diamond`, `diamond_block`), and sell units (`item`, `stack`, `shulker`). Validate that the player has an open Barrel (`InventoryType.BARREL`), enforce the 64-barrel per player limit, and check spatial boundary limits defined in `config.yml`.
   - **Barrel Scanner (`BarrelScanner.java`):** Recursively scan barrels while ignoring currency (`DIAMOND`/`DIAMOND_BLOCK`) and private display items starting with `*`. Deep-scan Shulker Box NBT/item meta to index internal contents.
   - **Audit Logging & Database (`DatabaseManager.java`):** Set up the SQLite connection (`shops.db`) to manage `registered_barrels` and immutable `audit_logs` for creation, unregistration, block breaks, and admin removals.
   - **Embedded Web Microservice (`WebServer.java`):** Configure `com.sun.net.httpserver.HttpServer` on port 8080. Include `Access-Control-Allow-Origin: *` CORS headers. Serve the JSON feed at `/api/shops` and the static single-page app at `/`.
   - **Web Frontend (`index.html`):** Build a modern, dark-themed responsive single-page Web UI with a real-time search bar that supports deep searching inside Shulker Boxes (with expandable inventory preview cards).

3. **Deliverables:**
   Please generate the complete, un-truncated source code for all project files in the standard Maven structure:
   - `pom.xml`
   - `src/main/resources/plugin.yml`
   - `src/main/resources/config.yml`
   - `AdvertisePlugin.java`
   - `ConfigManager.java` & `DatabaseManager.java`
   - `AdvertiseCommand.java` & `AdminCommand.java`
   - `InventoryListener.java` & `BlockListener.java`
   - `BarrelScanner.java`
   - `StockAuditTask.java`
   - `WebServer.java`
   - `src/main/resources/web/index.html`

Read `requirements.md` and start building the application step-by-step.