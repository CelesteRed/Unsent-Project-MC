# UnsentPlugin

A Minecraft Paper plugin inspired by [The Unsent Project](https://unsentproject.com/). Players write anonymous messages addressed to a name, and receive a physical **map item** in-game with the message rendered on it.

## Features

- `/unsent <name> <message>` — writes your message and gives you a map item
- `/unsentread <name>` — reads all saved messages for a name in chat
- White-background map with handwritten-style text rendering
- Built-in word filter with 80+ blocked words/phrases
- Messages saved to YAML files per name (`plugins/UnsentPlugin/messages/`)
- Namespaced commands (`/unsentplugin:unsent`) are intentionally disabled

## Requirements

- **Paper 26.1.2+**
- **Java 25+** (Paper API 26.1.2 is compiled to Java 25 bytecode)

## Installation

1. Drop `UnsentPlugin-1.1.0.jar` into your server's `plugins/` folder
2. Restart the server
3. Done — no configuration needed

## Building from Source

Requires **JDK 25+** and **Maven 3.8+**.

```bash
git clone https://github.com/YOUR_USERNAME/UnsentPlugin.git
cd UnsentPlugin
mvn package
# Output jar: target/UnsentPlugin-1.1.0.jar
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

## Permissions

| Permission | Default | Description |
|---|---|---|
| `unsent.use` | everyone | Use `/unsent` |
| `unsent.read` | everyone | Use `/unsentread` |
| `unsent.admin` | op | Admin access |

## License

MIT
