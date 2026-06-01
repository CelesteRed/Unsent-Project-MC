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
