# UnsentPlugin

A Minecraft Paper plugin inspired by [The Unsent Project](https://unsentproject.com/). Players write anonymous messages addressed to a name, and receive a physical **map item** in-game with the message rendered on it.

## Features

- `/unsent <name> <message>` — writes your message and gives you a map item
- `/unsentread <name>` — reads all saved messages for a name in chat
- `/unsentrecover <name> [number]` — (admin) rebuilds the note on a held map from stored messages
- `/unsentadmin whitelist <add|remove|list|reload> [block|hand]` — (admin) manage the placement whitelist; `hand` uses the held block
- White-background map with handwritten-style text rendering
- Built-in word filter with 80+ blocked words/phrases
- Optional **AI moderation** (OpenAI) as a second layer to catch filter bypasses — tuned for 13+
- Per-player message limit (`max-messages-per-player`) and creation cooldown (`creation-cooldown-seconds`), both bypassed by `unsent.unlimited`
- Placed maps are sealed in item frames (can't be popped out, broken, or rotated)
- Map placement restricted to whitelisted wall blocks (`whitelist.yml`) — no floor/ceiling frames
- Messages saved to YAML files per name (`plugins/UnsentPlugin/messages/`)
- Namespaced commands (`/unsentplugin:unsent`) are intentionally disabled

## Requirements

- **Paper 26.1.2+**
- **Java 25+** (Paper API 26.1.2 is compiled to Java 25 bytecode)

## Installation

1. Drop `UnsentPlugin-1.8.0.jar` into your server's `plugins/` folder
2. Restart the server
3. Done — no configuration needed

## Building from Source

Requires **JDK 25+** and **Maven 3.8+**.

```bash
git clone https://github.com/YOUR_USERNAME/UnsentPlugin.git
cd UnsentPlugin
mvn package
# Output jar: target/UnsentPlugin-1.8.0.jar
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

### Map placement whitelist

`plugins/UnsentPlugin/whitelist.yml` is an **allow-list** of block materials a map may be placed
on (in a wall-mounted item frame). Only blocks listed there accept a map; placing on any other
block — or on a floor/ceiling frame — is blocked and the map stays in the player's hand. Players
with `unsent.admin` bypass the restriction.

Manage the list in-game with `/unsentadmin whitelist`:

```text
/unsentadmin whitelist add hand        # add the block you're holding (WorldEdit-style)
/unsentadmin whitelist add STONE       # add by material name
/unsentadmin whitelist remove hand     # remove the held block
/unsentadmin whitelist list            # show the current whitelist
/unsentadmin whitelist reload          # re-read whitelist.yml after editing by hand
```

You can also edit `whitelist.yml` directly (it's never auto-merged, so removed blocks stay removed).

## Permissions

| Permission | Default | Description |
|---|---|---|
| `unsent.use` | everyone | Use `/unsent` |
| `unsent.read` | everyone | Use `/unsentread` |
| `unsent.unlimited` | op | Bypass the `max-messages-per-player` limit |
| `unsent.admin` | op | Admin access — bypass frame protection, `/unsentrecover` |

## License

MIT
