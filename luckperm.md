# UnsentPlugin — LuckPerms & Permissions Guide

Everything you need to manage UnsentPlugin permissions with **LuckPerms**. UnsentPlugin uses
plain Bukkit permission nodes, so any permissions plugin works — the LuckPerms (`/lp`) commands
below are just the most common way to apply them.

> Accurate as of **v1.4.0**. LuckPerms commands use the `/lp` alias (same as `/luckperms` or
> `/perm`). Replace `Steve` with a real player name and `staff`/`admin` with your group names.

---

## TL;DR — recommended setup

```bash
# 1) A staff group that can fix maps and manage placed notes
/lp creategroup staff
/lp group staff permission set unsent.admin true        # /unsentrecover + remove/rotate placed maps
/lp group staff permission set unsent.unlimited true    # no map-count cap

# 2) Put a player in it
/lp user Steve parent add staff

# 3) (Optional) trusted builder who just needs unlimited maps, nothing else
/lp user Alex permission set unsent.unlimited true
```

That's it. Normal players can already send and read notes out of the box (those nodes default to
everyone — see below).

---

## Permission nodes at a glance

| Node | Default | Grants |
|---|---|---|
| `unsent.use` | **everyone** | Run `/unsent <name> <message>` (create a note-map) |
| `unsent.read` | **everyone** | Run `/unsentread <name>` (list stored notes in chat) |
| `unsent.unlimited` | **op only** | Ignore the `max-messages-per-player` limit; your messages aren't counted |
| `unsent.admin` | **op only** | Run `/unsentrecover`, **and** bypass item-frame protection (pop out / break / rotate placed maps) |
| `unsent.*` | — | Wildcard: everything above. Good for a full-admin group |

"Default everyone" = the node's plugin default is `true`. "Default op only" = plugin default is
`op`, so only operators have it until you grant it in LuckPerms.

---

## Commands → required permission

| Command | Needs | Notes |
|---|---|---|
| `/unsent <name> <message>` | `unsent.use` | Subject to `max-messages-per-player` unless the player has `unsent.unlimited` |
| `/unsentread <name>` | `unsent.read` | Anyone can read any name's notes by default |
| `/unsentrecover <name> [number]` | `unsent.admin` | Player must be **holding** the blank map; rebinds a stored message to it |

If a player lacks the node, Bukkit blocks the command before the plugin runs and shows the
standard "no permission" message.

---

## Behaviour gated by permission (not a command)

Two protections check a permission directly in code, so they apply even though there's no command:

### `unsent.admin` → bypass item-frame protection
By design, an unsent map placed in an item frame **cannot be popped out, broken off, or rotated**
by anyone — that's the whole point (notes stay where they're hung). Players with `unsent.admin`
are the exception: they can remove/rotate placed maps for moderation or cleanup.

- Environmental breaks (explosions, breaking the block behind the frame) are **always** blocked,
  even for admins, so notes can't be lost by accident.

### `unsent.unlimited` → bypass the map cap
`config.yml` has `max-messages-per-player` (default `10`). A player at the cap gets
*"You've reached your limit of 10 messages."* Players with `unsent.unlimited`:
- never hit the cap, and
- don't have their messages counted at all (their tally stays at 0).

Set `max-messages-per-player: 0` in `config.yml` to disable the cap for everyone instead.
(The old key `max-maps-per-player` is still honored as a fallback.)

---

## How defaults work with LuckPerms (read this once)

LuckPerms respects each node's plugin-declared default (it has
`apply-bukkit-default-permissions: true` on by default), so:

- **`unsent.use` / `unsent.read`** are `default: true` → **every player already has them**, even
  without any LuckPerms setup. You only touch these if you want to *take them away*.
- **`unsent.admin` / `unsent.unlimited`** are `default: op` → only **server operators** have them
  until you grant them in LuckPerms.

**Best practice:** don't rely on op. De-op your staff and grant the admin nodes to a group instead
— it's auditable and revocable. A granted node always wins over op status.

**Three states for any node in LuckPerms:**

| You want | Command | Result |
|---|---|---|
| Grant | `/lp user Steve permission set unsent.admin true` | explicitly allowed |
| Deny | `/lp user Steve permission set unsent.use false` | explicitly blocked (overrides the `true` default) |
| Reset to default | `/lp user Steve permission unset unsent.admin` | falls back to the plugin default |

---

## Recommended group structure

```bash
# --- members: the default group. Already can /unsent and /unsentread via defaults. ---
# Nothing to do unless you want to restrict them (see cookbook).

# --- mod: can recover/clean up placed maps, but normal map cap still applies ---
/lp creategroup mod
/lp group mod permission set unsent.admin true

# --- admin: everything ---
/lp creategroup admin
/lp group admin permission set unsent.* true

# assign people
/lp user Steve parent add admin
/lp user Robin parent add mod
```

If your groups inherit (e.g. `admin` inherits `mod` inherits `default`), only add the *extra*
nodes at each tier.

---

## Cookbook (common tasks)

**Give one player unlimited maps (and nothing else):**
```bash
/lp user Alex permission set unsent.unlimited true
```

**Let a group manage placed maps + recover them:**
```bash
/lp group staff permission set unsent.admin true
```

**Stop a specific player from sending notes (e.g. while muted):**
```bash
/lp user Troll permission set unsent.use false
# undo later:
/lp user Troll permission unset unsent.use
```

**Make reading notes staff-only (hide `/unsentread` from normal players):**
```bash
/lp group default permission set unsent.read false
/lp group staff   permission set unsent.read true
```

**Grant everything with the wildcard:**
```bash
/lp group admin permission set unsent.* true
```

**Temporary perk (expires automatically) — 7 days of unlimited maps:**
```bash
/lp user Alex permission settemp unsent.unlimited true 7d
```

**Only in one world (LuckPerms context) — unlimited maps on the creative world only:**
```bash
/lp user Alex permission set unsent.unlimited true world=creative
```

---

## Verifying & troubleshooting

**Check whether a player has a node (and why):**
```bash
/lp user Steve permission check unsent.unlimited
```

**See everything a player resolves to:**
```bash
/lp user Steve info
/lp user Steve permission info
```

**Common gotchas**

- *"My admin can't use `/unsentrecover`."* They need `unsent.admin` (or op). Confirm with
  `permission check`, and remember a granted `false` anywhere beats a `true` elsewhere.
- *"A player still can't make maps even though I gave `unsent.unlimited`."* `unsent.unlimited`
  only lifts the **count cap** — they still need `unsent.use` (which everyone has unless you
  denied it) to run the command at all.
- *"Op can do everything but my group can't."* That's expected — op satisfies `default: op`
  nodes. Grant the nodes explicitly to the group so non-op staff get them too.
- *Changes not applying?* Run `/lp sync`, or have the player relog. LuckPerms applies most
  changes instantly, but command-permission caches refresh on rejoin.

---

## Full node reference (copy-paste)

```text
unsent.use         default: true   # /unsent  (send a note-map)
unsent.read        default: true   # /unsentread  (list notes in chat)
unsent.unlimited   default: op     # ignore max-messages-per-player; messages not counted
unsent.admin       default: op     # /unsentrecover + remove/rotate placed unsent maps
unsent.*           -               # wildcard for all of the above
```

---

### Note on the in-jar description

The `unsent.admin` line inside `plugin.yml` still reads *"Admin access (clear messages, bypass
filter)."* That text is **stale** — there is currently **no** clear-messages command and **no**
word-filter bypass for admins (the filter applies to everyone). The table in this guide reflects
what the code actually does. If you'd like, those two abilities can be implemented (an admin
`/unsent clear <name>` and a filter bypass under `unsent.admin`) — just say the word.
