# To-do — UnsentPlugin

Tasks for future Claude agents working on this plugin. Read this whole file before
starting — the **Build environment** section has a gotcha that will waste your time
if you miss it.

> **Status (2026-06-01):** Tasks 1, 2, and 3 are all ✅ **DONE**. JDK 25 was found at
> `C:\Users\celes\.jdks\openjdk-25.0.2` and the jar builds cleanly with it. `pom.xml` and
> `README.md` were updated from "Java 21" to the real requirement (Java 25).

---

## ⚠️ Build environment (read first)

- **Paper API 26.1.2 requires JDK 25**, *not* JDK 21. The `pom.xml` and `README.md`
  both say "Java 21", but the `paper-api` jar in the local Maven cache has class-file
  version **69.0** (= Java 25). Compiling with JDK 21 fails with
  `class file has wrong version 69.0, should be 65.0`.
- This machine (as of last check) only has **JDK 21** installed (Eclipse Adoptium,
  Oracle Java, and IntelliJ's bundled JBR are all 21). You must use a **JDK 25** to build.
- Maven is **not** on PATH. IntelliJ bundles it at:
  `C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd`
- **Recommended fix:** either install/point Maven at a JDK 25, OR update `pom.xml` +
  `README.md` to the real requirement (Java 25). Don't "fix" it by downgrading
  `<java.version>` to 21 alone — the dependency itself is Java-25 bytecode, so that
  won't compile.

### Build command (once a JDK 25 is available)
```powershell
$env:JAVA_HOME = "<path-to-jdk-25>"
$mvn = "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"
& $mvn -f "<project-root>\pom.xml" clean package
# Output: target\UnsentPlugin-1.0.0.jar
```

---

## Task 1 — Show the date/time the note was made on the map `[mm/dd/yy]`

**Goal:** Render the creation date (format `mm/dd/yy`, e.g. `06/01/26`) on the map image
so the recipient can see when each note was written.

**Good news:** the timestamp is **already stored**. `MessageStore.save()`
([src/main/java/com/unsentplugin/MessageStore.java](src/main/java/com/unsentplugin/MessageStore.java#L31-L33))
prefixes each entry with `Instant.now().toEpochMilli() + "|"`, and `load()` parses it back
into `UnsentMessage.timestamp`. So this task is **wiring the timestamp into the renderer**,
not adding new storage.

**Steps:**
1. In [UnsentCommand.java](src/main/java/com/unsentplugin/UnsentCommand.java#L68-L74), the
   map is built *after* saving with `MapFactory.createMap(player.getWorld(), name, message)`.
   The save doesn't return the timestamp, so capture one timestamp value
   (`long now = System.currentTimeMillis();`) and pass the **same** value to both
   `MessageStore.save(...)` and `MapFactory.createMap(...)`. (Refactor `save()` to accept the
   timestamp instead of generating its own, so the stored value and the rendered value match.)
2. Add a `long timestamp` parameter to
   [MapFactory.createMap()](src/main/java/com/unsentplugin/MapFactory.java#L17) and pass it
   through to `new UnsentMapRenderer(recipientName, message, timestamp)`.
3. In [UnsentMapRenderer.java](src/main/java/com/unsentplugin/UnsentMapRenderer.java), store the
   timestamp and draw it in `render()`. Format with:
   ```java
   DateTimeFormatter.ofPattern("MM/dd/yy").withZone(ZoneId.systemDefault())
       .format(Instant.ofEpochMilli(timestamp))
   ```
   Suggested placement: small gray text in a corner (e.g. bottom-right or under the
   "to [Name]" header). The canvas is 128×128 — see the existing layout (`y` cursor stops
   at `MAP_SIZE - 10`), so leave room or draw it at a fixed `y` near the bottom before the
   body loop can overrun it.

**Note:** `ReadCommand` already prints a date as `MMM d, yyyy`
([ReadCommand.java](src/main/java/com/unsentplugin/ReadCommand.java#L19-L20)). This task is
specifically about the **map image**, and the requested format is `mm/dd/yy`. Consider whether
to keep the two formats distinct or unify them — confirm with the user.

---

## Task 2 — Note cannot be popped out of item frames (cancel the event)

**Goal:** When an unsent map is placed in an item frame, players should **not** be able to
pop the item back out (the first left-click normally removes the item from a frame).

**Current state:** the plugin registers **no event listeners at all** —
[UnsentPlugin.onEnable()](src/main/java/com/unsentplugin/UnsentPlugin.java#L12-L31) only wires
up commands. This task needs a brand-new `Listener` class and registration.

**Steps:**
1. **Tag the map so we can identify it.** In
   [MapFactory.createMap()](src/main/java/com/unsentplugin/MapFactory.java#L36-L51), inside the
   `editMeta` block, write a marker into the item's `PersistentDataContainer`, e.g.:
   ```java
   NamespacedKey key = new NamespacedKey(plugin, "unsent_map"); // pass plugin in, or use a static key
   meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
   ```
   (`createMap` is currently `static` and has no `plugin` reference — either pass the plugin/key
   in, or build the `NamespacedKey` from the main class. Pick one and keep it consistent.)
2. **Create `ItemFrameListener implements Listener`.** Handle the pop-out, which in Bukkit is
   an `EntityDamageByEntityEvent` where the damaged entity is an `ItemFrame` (the first hit
   removes the item rather than breaking the frame). Cancel it when the frame's item is one of
   our tagged maps:
   ```java
   @EventHandler
   public void onFrameDamage(EntityDamageByEntityEvent e) {
       if (e.getEntity() instanceof ItemFrame frame) {
           ItemStack item = frame.getItem();
           if (isUnsentMap(item)) e.setCancelled(true);
       }
   }
   ```
   Also consider `HangingBreakByEntityEvent` / `PlayerInteractEntityEvent` for edge cases
   (e.g. breaking the frame entirely, or projectiles). At minimum cancel the pop-out; decide
   with the user whether the *frame itself* should also be protected.
3. **Register it** in `onEnable()`:
   ```java
   getServer().getPluginManager().registerEvents(new ItemFrameListener(this), this);
   ```
4. Add a helper `isUnsentMap(ItemStack)` that checks the PDC key from step 1.

**Edge cases to think about:** map vs. filled-map material, items placed *before* this feature
existed (no PDC tag — they won't be protected), and admin bypass (`unsent.admin`) if desired.

---

## Task 3 — Build the jar for quick testing

Produce `target/UnsentPlugin-1.0.0.jar` so it can be dropped into a Paper server's
`plugins/` folder. See **Build environment** above — **you need a JDK 25**, not 21.

After building, the testing loop is:
1. Copy `target\UnsentPlugin-1.0.0.jar` → your test server's `plugins\` folder.
2. Restart the server (or `/reload confirm`, though a full restart is cleaner for renderer changes).
3. In-game: `/unsent SomeName your message here`, then place the map in an item frame to
   verify Task 2, and read the date on the map to verify Task 1.

---

## Task 4 [Update 1.4.0] - AI moderation — ✅ DONE (shipped in v1.5.0)

Create a moderation system built on top of the existing one that just catches bad words, and then this AI moderation will be used to catch any possible bypasses! I will be using OpenAI with a API key, so in the config mke a new input for the key and also ensure tht the prompt to give the agent returns back true or false on if the message is safe and not threatning or sexual in any way! this is a 13+ server!!!

**Done:** `AiModerator` (OpenAI chat-completions, true/false safe-for-13+ prompt) runs async on top of
`WordFilter`. Config under `ai-moderation` (`enabled`, `api-key`, `model`, `timeout-seconds`,
`fail-closed`). See README → *AI moderation (optional)*.

---

## Task 5 [planned] — Real-username check, placement whitelist, polished chat, per-user moderation log

Four related features. Each can reasonably ship as its own version (suggest one per version, 5A → 5D).
**⚠️ Some of the requirements conflict or need a decision — see "Decisions to confirm" at the bottom
before writing any code.**

### 5A — Verify the recipient is a real Minecraft account (via an API)

**Goal:** When a player runs `/unsent <name> <message>`, confirm `<name>` is an actual Minecraft
username before creating the note; reject unknown names.

**Why an API:** Bukkit's `getOfflinePlayer(name)` never confirms a *global* account — it returns an
object regardless, and `hasPlayedBefore()` only knows about the local server. Existence has to be
checked against Mojang (directly or via a wrapper).

**Researched options to evaluate:**

| Option | Endpoint | Notes |
|---|---|---|
| Mojang (official) | `GET api.mojang.com/users/profiles/minecraft/<name>` | 200 + `{id,name}` if real; 404/empty if not. No auth. Rate-limited (~hundreds / 10 min per IP). |
| Mojang (services host) | `api.minecraftservices.com/minecraft/profile/lookup/name/<name>` | Same idea on the current service host. |
| PlayerDB | `playerdb.co/api/player/minecraft/<name>` | Friendly JSON wrapper with a `success` flag; caches upstream. |
| Ashcon | `api.ashcon.app/mojang/v2/user/<name>` | Wrapper; 404 when missing; also returns UUID/skin. |

**✅ Decided:** support a **configurable, ordered list of APIs** and **fail over** — if the first
provider is down/errors, try the next, and so on.

**Approach:**
- Async HTTP — reuse the `AiModerator` pattern (`HttpClient` off-thread → hop back to main thread).
- New `UsernameValidator` class that walks the configured provider list **in order** until one gives
  a definitive answer (exists / doesn't exist). A provider that errors or times out is skipped and
  the next is tried. Only if **all** providers fail is it treated as "couldn't verify".
- Built-in providers identified by name (each with its own known request + response parsing, since
  every API returns a different shape): `mojang`, `mojang-services`, `playerdb`, `ashcon`. Consider
  also allowing a custom entry with a URL template (`{name}`) — but note generic existence-detection
  is unreliable, so built-in named providers are the safe core.
- **Cache** positive/negative results (TTL) to respect rate limits and reduce calls.
- Cheap pre-check first with the existing `isValidName` regex (consider tightening to Mojang's
  3–16 `[A-Za-z0-9_]`).
- Config block, e.g.:
  ```yaml
  username-validation:
    enabled: false            # off by default so offline-mode / creative servers aren't broken
    fail-closed: true         # after ALL providers fail: reject (true) or allow (false)
    cache-ttl-minutes: 1440
    providers:                # tried in order, first definitive answer wins
      - mojang
      - playerdb
      - ashcon
  ```
- Edge cases: offline-mode servers, name changes, every provider down at once, recipient who never
  joined here.

### 5B — Restrict where maps can be placed (whitelist of blocks; no floor/ceiling) — ✅ DONE (v1.7.0)

**Goal:** A new `whitelist.yml` in the plugin folder lists the block materials that ARE allowed to
hold an unsent map. Placing a map into an item frame is only permitted when (a) the block the frame
hangs on is whitelisted, **and** (b) the frame faces a wall — **floor/ceiling (UP/DOWN) frames are
rejected**.

**Approach:**
- New `whitelist.yml` (a set of `Material` names) + loader (consider the same auto-update treatment
  as `config.yml`/`ConfigUpdater`). Ship sensible defaults.
- Intercept the map entering a frame in `ItemFrameListener.onFrameInteract` (the empty-frame +
  placing-our-map branch that already exists):
  - reject if `frame.getFacing()` is `UP`/`DOWN` (floor/ceiling),
  - reject if the attached block (frame location relative to the opposite of its facing) isn't
    whitelisted,
  - on reject: cancel the interaction so the map stays in hand, and send a message.
- Edge cases: maps placed before this existed, creative mode, possible `unsent.admin` bypass.

**✅ Decided:** **whitelist (allow-list) model** — only blocks listed in `whitelist.yml` may hold a
map; everything else (and all floor/ceiling frames) is rejected.

### 5C — Polish chat output with MiniMessage

**Goal:** Make command feedback look nicer — bold accents, colour, maybe a gradient prefix — using
"mini text" (Adventure **MiniMessage**, which ships with Paper, no new dependency).

**Approach:**
- Add a small `Messages` helper with a shared `MiniMessage` instance and a prefix
  (e.g. `<gradient:#ff8fb1:#b18cff><bold>Unsent</bold></gradient> ›`).
- Refactor user-facing text in `UnsentCommand`, `ReadCommand`, `RecoverCommand`, and the new admin
  command to MiniMessage. Keep it tasteful and readable.

### 5D — Per-user moderation log + `/unsentadmin inspect` (two modes)

**Goal:** Record each player's activity so an admin can review it, in **two ways**:
1. **`/unsentadmin inspect <user>`** — print that user's **sent** messages AND their **flagged**
   messages (blocked by the word filter or AI, never sent).
2. **`/unsentadmin inspect`** (no args) — toggle **inspect mode**: while it's on, an admin
   right-clicking a placed item-frame map sees **who wrote it and when**. Admins only.

**Approach (the log):**
- Log keyed by the **sender's** account (UUID + cached name), not the recipient.
- New `UserLog` store (e.g. `users/<uuid>.yml`, or a single `user-log.yml`) with entries like
  `{type: SENT|FLAGGED, recipient, message, reason, timestamp}`, where `reason` ∈ word-filter /
  ai-moderation.
- Hook points in `UnsentCommand`: on a successful send → log `SENT`; when the word filter or AI
  blocks a message → log `FLAGGED` with the reason and the attempted text.

**Approach (click-to-inspect):**
- **Store the author** so a placed map can reveal it. Extend `MapStore` to also record the writer's
  UUID + name (and creation time) keyed by map id — keeping authorship **server-side** (preferred
  over the item's PDC, which players with NBT access could read; the notes are otherwise anonymous).
- Track which admins are in inspect mode in an in-memory `Set<UUID>` (cleared on quit). Add a toggle
  via `/unsentadmin inspect` with no user.
- In `ItemFrameListener` (the interact handler), if the clicker is in inspect mode and the frame
  holds an unsent map: look up the author by the map's id and send "Written by <name> on <date>";
  **cancel the interaction** so it doesn't rotate/do anything else while inspecting. This must take
  priority over the normal admin rotate-bypass.
- Edge case: maps created before authorship was stored (no author on record) → show "unknown".

**Command + rendering:**
- New `AdminCommand` for `/unsentadmin <subcommand>` (`inspect`), permission `unsent.admin`; declare
  in `plugin.yml`. Resolve `<user>` → UUID (online player, the cache, or 5A).
- Render with MiniMessage (ties into 5C). Consider paging when a user has many entries.

### Decisions
1. **5B model:** ✅ whitelist (allow-list) of blocks.
2. **5A failover:** ✅ ordered list of providers in config, tried until one gives a definitive
   answer. Default **off**; `fail-closed` after *all* providers fail.
   *(Still open: tighten names to Mojang's 3–16 rule? grandfather existing free-form recipient names?)*
3. **5D inspect:** ✅ two modes — `inspect <user>` (sender's sent + flagged log) and `inspect`
   (toggle click-to-inspect on placed frames, showing author + date). Both gated by `unsent.admin`.
4. **Ship order:** suggested one feature per version (5A → 5D) — confirm when we start.

---

## Quick reference — file map

| File | Responsibility |
|---|---|
| [UnsentPlugin.java](src/main/java/com/unsentplugin/UnsentPlugin.java) | Plugin entry point, `onEnable` wiring (register listeners here) |
| [UnsentCommand.java](src/main/java/com/unsentplugin/UnsentCommand.java) | `/unsent <name> <message>` — validates, saves, gives the map |
| [ReadCommand.java](src/main/java/com/unsentplugin/ReadCommand.java) | `/unsentread <name>` — prints saved messages with dates |
| [MapFactory.java](src/main/java/com/unsentplugin/MapFactory.java) | Builds the `FILLED_MAP` item + attaches the renderer |
| [UnsentMapRenderer.java](src/main/java/com/unsentplugin/UnsentMapRenderer.java) | Draws the note onto the 128×128 map canvas (Java2D) |
| [MessageStore.java](src/main/java/com/unsentplugin/MessageStore.java) | YAML persistence per name; stores `epochMillis\|message` |
| [WordFilter.java](src/main/java/com/unsentplugin/WordFilter.java) | Name/message validation + blocked-word filter |
| [plugin.yml](src/main/resources/plugin.yml) | Commands + permissions declaration |
| [config.yml](src/main/resources/config.yml) | Defaults: blocked words, max length, max per name |
