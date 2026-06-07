# UnsentPlugin

A Minecraft Paper plugin inspired by [The Unsent Project](https://unsentproject.com/). Players write anonymous messages addressed to a name, and receive a physical **map item** in-game with the message rendered on it.

## Features

- `/unsent <name> <message>` — writes your message and gives you a map item (white, default text size)
- `/unsentvip <name> <size> <color> <message>` — (perm `unsent.color`) custom body text size (12–32) + background (hex or a name like `red`)
- `/unsentread <name>` — reads all saved messages for a name in chat
- `/unsentrecover <name> [number]` — (admin) rebuilds the note on a held map from stored messages
- `/unsentadmin whitelist <add|remove|list|reload> [block|hand]` — (admin) manage the placement whitelist; `hand` uses the held block
- `/unsentreload` — (admin) reload `config.yml` and `whitelist.yml` from disk without a restart
- White-background map with handwritten-style text rendering
- Built-in word filter with 80+ blocked words/phrases
- Optional **AI moderation** (OpenAI) as a second layer to catch filter bypasses — tuned for 13+
- Optional **real-username validation** — only accept recipients that are real Minecraft accounts (multi-API failover)
- Tab-complete recipient names from online **and** previously-joined players
- `unsent.color` unlocks `/unsentvip` — a custom **body text size (12–32)** and **background**, with adaptive text contrast
- Duplicate notes are rejected — the same message can't be sent to the same name twice (anti-flood)
- **Note credits**: start with 1, earn one every week (configurable duration), shown as an "Unsent Note" paper item in your inventory
- Per-player message limit (`max-messages-per-player`) and creation cooldown (`creation-cooldown-seconds`), both bypassed by `unsent.unlimited`
- Placed maps are sealed in item frames (can't be popped out, broken, or rotated)
- Right-click a wall block with a note to hang it — the plugin spawns the item frame for you
- Placement restricted to whitelisted wall blocks (`whitelist.yml`) — no floor/ceiling, no other blocks
- Messages saved to YAML files per name (`plugins/UnsentPlugin/messages/`)
- Namespaced commands (`/unsentplugin:unsent`) are intentionally disabled

## Requirements

- **Paper 26.1.2+**
- **Java 25+** (Paper API 26.1.2 is compiled to Java 25 bytecode)

## Installation

1. Drop `UnsentPlugin-1.17.0.jar` into your server's `plugins/` folder
2. Restart the server
3. Done — no configuration needed

## Building from Source

Requires **JDK 25+** and **Maven 3.8+**.

```bash
git clone https://github.com/YOUR_USERNAME/UnsentPlugin.git
cd UnsentPlugin
mvn package
# Output jar: target/UnsentPlugin-1.17.0.jar
```

## Configuration

Edit `plugins/UnsentPlugin/config.yml` after first run:

```yaml
blocked-words:
  - "rape"
  - "fuck"
  # add more as needed...

max-message-length: 80
max-messages-per-name: 100
```

Changes to `config.yml` take effect after `/reload confirm` or server restart.

When you update the plugin, any **new** config options are merged in automatically on startup —
your existing settings are kept. A `config-version` stamp at the bottom of the file drives this;
don't edit it.

### AI moderation (optional)

A second moderation layer that sends each message to OpenAI and asks whether it's safe for a
13+ audience (no sexual or threatening content), catching bypasses the static word list misses.
Disabled by default — add your API key and flip `enabled` to turn it on:

```yaml
ai-moderation:
  enabled: true
  api-key: "sk-..."        # keep this secret
  model: "gpt-4o-mini"
  timeout-seconds: 8
  fail-closed: true         # reject messages if a check can't complete (recommended for 13+)
```

The check runs asynchronously (off the main thread); players see a brief *"Checking your
message…"* while it resolves. The word filter always runs first, so AI calls only happen for
messages that already passed it.

### Username validation (optional)

When enabled, `/unsent` only accepts a recipient that is a **real Minecraft account**. The name is
checked against a configurable, ordered list of provider APIs — the first to answer decides, and if
one is down the next is tried (failover):

```yaml
username-validation:
  enabled: true
  fail-closed: true          # reject if ALL providers are unreachable
  cache-ttl-minutes: 1440
  timeout-seconds: 6
  providers:                 # tried in order; built-ins: mojang, mojang-services, playerdb, ashcon
    - mojang
    - playerdb
    - ashcon
```

Off by default (so offline-mode / creative servers aren't affected). Results are cached per name to
respect rate limits. The check runs asynchronously alongside AI moderation.

`/unsent <name>` also tab-completes from online **and** previously-joined offline players, so you
rarely need to type a full name.

### Map placement whitelist

To hang a note, a player **right-clicks a wall block** while holding the map — the plugin spawns an
**invisible** wall-mounted item frame, drops the note into it, and takes one map from the player. The
hidden frame makes the note appear to float on the wall. Once placed, a note can't be popped out,
broken, or rotated by non-admins.

`plugins/UnsentPlugin/whitelist.yml` is an **allow-list** of blocks a note may be hung on. It
**starts empty** — no blocks are added for you, so nothing can be placed until you add the blocks
you want. Only listed blocks accept a note; right-clicking any other block — or a floor/ceiling
face — is refused with *"This note can't be placed here."* and the map stays in hand. Players with
`unsent.admin` bypass the restriction. (Functional blocks like chests/doors still work normally
while holding a note — sneak-right-click to place on those.)

Edit the list two ways, both applied live with no restart:

```text
/unsentadmin whitelist add hand        # add the block you're holding
/unsentadmin whitelist remove hand     # remove it
# …or edit whitelist.yml by hand, then:
/unsentreload                          # re-read config.yml + whitelist.yml from disk
```

Manage the list in-game with `/unsentadmin whitelist`:

```text
/unsentadmin whitelist add hand        # add the block you're holding (WorldEdit-style)
/unsentadmin whitelist add STONE       # add by material name
/unsentadmin whitelist remove hand     # remove the held block
/unsentadmin whitelist list            # show the current whitelist
/unsentadmin whitelist reload          # re-read whitelist.yml after editing by hand
```

You can also edit `whitelist.yml` directly (it's never auto-merged, so removed blocks stay removed).

### Note appearance (font & colours)

- **Font:** put a font file named **exactly `font.ttf`** (or `font.otf`) inside the plugin's own
  folder — `plugins/UnsentPlugin/font.ttf`, **not** `plugins/` — to render notes in a custom (e.g.
  Minecraft) font. It takes priority; otherwise `map.font` names a system font family, falling back
  to SansSerif. On startup the console logs whether it loaded (and the exact path if not). Run
  `/unsentreload` or restart after adding it. (The plugin can't ship Minecraft's own font.)
- **Named colours:** `/unsentvip` accepts a hex (`#FF5555`) **or** a name (`red`, `blue`, …). The
  names and their hex live in the `colors:` section of `config.yml` — reassign them however you like.
- `/unsentreload` re-reads the config, the whitelist, **and** the font.

## Permissions

| Permission | Default | Description |
|---|---|---|
| `unsent.use` | **op** | Send notes (`/unsent`). No longer everyone — grant to a VIP rank (or via `unsent.color`) |
| `unsent.read` | everyone | Use `/unsentread` |
| `unsent.unlimited` | op | Bypass the `max-messages-per-player` limit |
| `unsent.color` | op | `/unsentvip` custom size + colour; **implies `unsent.use`** (so VIPs can send) |
| `unsent.admin` | op | Admin access — bypass frame protection, `/unsentrecover`, `/unsentadmin` |

> **Sending is now VIP/OP-only.** `unsent.use` defaults to `op`; grant it to your VIP rank (or give
> that rank `unsent.color`, which includes it). Normal players can still *read* notes.

## License

MIT
