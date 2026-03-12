# DonutAuth

## Authentication Plugin for Minecraft Servers (Paper / Folia)

DonutAuth is a lightweight authentication plugin for Minecraft servers running Paper or Folia. It handles both premium (Mojang-authenticated) and cracked (offline-mode) players, providing registration and login functionality with per-server branding, multi-language support, and session management.

---

## Overview

The plugin targets servers running in offline mode that need to support a mixed player base of premium and cracked accounts. When a player joins, the plugin queries the Mojang API to determine whether the username corresponds to a legitimate Mojang account. Premium players can be auto-authenticated, while cracked players are required to register and log in with a password.

Unauthenticated players are fully restricted: movement, chat, commands, block interaction, inventory access, combat, and item pickup/drop are all blocked until authentication is complete.

Account data is stored in a local JSON file using salted SHA-256 password hashing. Premium and cracked accounts are stored under separate keys to prevent impersonation (cracked accounts are prefixed with a dot).

---

## Features

- **Premium detection** -- Asynchronous Mojang API lookup to identify premium accounts, with result caching and configurable timeout.
- **Premium auto-login** -- Premium players can be automatically authenticated on join (configurable).
- **Registration and login** -- Cracked players register with `/register <password> <password>` and log in with `/login <password>` on subsequent joins.
- **Session persistence** -- Authenticated players who reconnect within a configurable timeout are automatically re-authenticated.
- **Brute-force protection** -- Configurable maximum login attempts before kick.
- **Login timeout** -- Players who do not authenticate within a configurable time are kicked.
- **Full action blocking** -- Unauthenticated players cannot move, chat, use commands (except whitelisted ones), break/place blocks, take damage, deal damage, interact with inventories, or drop/pick up items.
- **Customizable branding** -- Server name, name segments, and colors are defined in `config.yml` and injected into all messages via placeholders.
- **Multi-language support** -- Ships with 10 languages: English, French, Spanish, German, Portuguese, Italian, Dutch, Polish, Russian, and Turkish. Language files use MiniMessage format and are extracted to the plugin data folder for editing.
- **Folia compatibility** -- Runtime detection of Folia; all scheduling uses the correct API (entity schedulers on Folia, Bukkit schedulers on Paper).
- **Action bar prompts** -- Periodic action bar messages remind unauthenticated players to log in or register.
- **Title messages** -- Titles and subtitles are displayed on join and on successful authentication.
- **Admin commands** -- Reload configuration, view plugin stats (account counts, premium/crack breakdown, authenticated players), and dump account data to console for debugging.
- **Hot reload** -- `/lockauth reload` reloads configuration, language files, and clears the premium check cache without restarting the server.

---

## Requirements

- Java 21 or later
- Paper 1.21.4+ or Folia 1.21.4+
- Server must be running in offline mode (otherwise all players are already authenticated by Mojang)

---

## Installation

1. Download or build the plugin JAR.
2. Place it in the server's `plugins/` directory.
3. Start the server. The plugin will generate `config.yml` and language files in `plugins/DonutAuth/`.
4. Edit `config.yml` to configure branding, language, timeouts, and password requirements.
5. Reload with `/lockauth reload` or restart the server.

---

## Building from Source

The project uses Gradle with the Paper API.

```bash
./gradlew build
```

The compiled JAR is output to `build/libs/DonutAuth-<version>.jar`.

---

## Configuration

### config.yml

The configuration file controls branding, authentication behavior, and language selection.

**Branding:**

```yaml
display-name: "DonutSMP"
name-part1: "Donut"
name-color1: "gold"
name-part2: "SMP"
name-color2: "white"
```

These values are injected into all messages wherever `{name}`, `{name1}`, `{name2}`, `{color1}`, and `{color2}` placeholders appear. This allows full visual customization without editing language files.

**Authentication settings:**

| Key | Description | Default |
|---|---|---|
| `language` | Language code (en, fr, es, de, pt, it, nl, pl, ru, tr) | `en` |
| `premium-auto-login` | Automatically authenticate premium players | `true` |
| `premium-check-timeout` | Mojang API timeout in milliseconds | `3000` |
| `session-timeout` | Session validity in minutes (0 to disable) | `5` |
| `login-timeout` | Seconds before kicking unauthenticated players (0 to disable) | `120` |
| `max-login-attempts` | Failed password attempts before kick | `5` |
| `password.min-length` | Minimum password length | `4` |
| `password.max-length` | Maximum password length | `32` |
| `actionbar-interval` | Action bar reminder interval in ticks | `30` |
| `allowed-commands` | Commands usable before authentication | `["/login", "/register", "/l", "/reg"]` |

**Branding examples:**

```yaml
# PvP server
display-name: "PvPLand"
name-part1: "PvP"
name-color1: "red"
name-part2: "Land"
name-color2: "yellow"

# SkyBlock server
display-name: "SkyBlock"
name-part1: "Sky"
name-color1: "aqua"
name-part2: "Block"
name-color2: "green"
```

### Language Files

Language files are stored in `plugins/DonutAuth/lang/` as YAML files. They are extracted from the JAR on first run and can be freely edited. All strings use MiniMessage formatting.

Branding placeholders (`{name1}`, `{name2}`, `{color1}`, `{color2}`) are replaced at runtime, so language files do not need to reference specific server names.

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/register <password> <password>` | Create an account (aliases: `/reg`) | None |
| `/login <password>` | Log in to an existing account (aliases: `/l`) | None |
| `/lockauth reload` | Reload configuration and language files (aliases: `/lauth`) | `lockauth.admin` |
| `/lockauth info` | Display plugin statistics | `lockauth.admin` |
| `/lockauth debug` | Dump all accounts to the server console | `lockauth.admin` |

---

## Permissions

| Permission | Description | Default |
|---|---|---|
| `lockauth.admin` | Access to `/lockauth` admin commands | OP |

---

## How It Works

### Player Join Flow

1. Player connects. Join message is suppressed.
2. The plugin queries the Mojang API asynchronously to determine if the username belongs to a premium account.
3. A storage key is derived: premium players are stored as `username` (lowercase), cracked players as `.username` (dot prefix).
4. Based on the result:
   - **Premium + auto-login enabled**: player is immediately authenticated and shown a premium welcome message.
   - **Returning player with valid session**: player is re-authenticated silently.
   - **Registered cracked player**: player is prompted to `/login`.
   - **New player**: player is prompted to `/register`.
5. If the player does not authenticate within the configured timeout, they are kicked.

### Storage Key Design

Premium and cracked accounts sharing the same username are stored separately. A cracked player named `Steve` is stored under the key `.steve`, while a premium player named `Steve` is stored under `steve`. This prevents a cracked player from accessing a premium player's account or vice versa.

### Password Storage

Passwords are hashed using double SHA-256 with a 16-byte random salt generated via `SecureRandom`. The salt and hash are stored in Base64 encoding in `accounts.json`. Passwords are never stored or logged in plaintext.

### Folia Support

The plugin detects Folia at runtime by checking for the presence of `io.papermc.paper.threadedregions.RegionizedServer`. All task scheduling goes through the `Scheduler` utility class, which routes to entity-bound schedulers on Folia and to `BukkitScheduler` on Paper. The plugin declares `folia-supported: true` in `plugin.yml`.

---

## Data Storage

All persistent data is stored in `plugins/DonutAuth/accounts.json` as a JSON object mapping storage keys to account records. Each record contains:

- `salt` -- Base64-encoded password salt
- `hash` -- Base64-encoded password hash
- `registeredAt` -- Registration timestamp (epoch milliseconds)
- `lastLogin` -- Last successful login timestamp (epoch milliseconds)
- `isPremium` -- Whether the account was registered as premium
- `displayName` -- The player's display name at registration time

The file is saved on every registration and login. It is also saved on plugin disable.

---

## Supported Languages

| Code | Language |
|---|---|
| `en` | English |
| `fr` | French |
| `es` | Spanish |
| `de` | German |
| `pt` | Portuguese |
| `it` | Italian |
| `nl` | Dutch |
| `pl` | Polish |
| `ru` | Russian |
| `tr` | Turkish |

---

## Project Structure

```
src/main/java/com/donutauth/
    DonutAuth.java           Main plugin class
    AuthManager.java         Account storage, password hashing, session management
    AuthListener.java        Event listener (join, quit, action blocking)
    PremiumChecker.java      Mojang API integration
    ActionBarTask.java       Periodic action bar reminders
    command/
        RegisterCommand.java     /register handler
        LoginCommand.java        /login handler
        DonutAuthCommand.java    /lockauth admin handler
    lang/
        LangManager.java     Language file loading, MiniMessage parsing, branding injection
    util/
        Scheduler.java       Paper/Folia scheduler abstraction

src/main/resources/
    plugin.yml               Plugin descriptor
    config.yml               Default configuration
    lang/                    Language files (en.yml, fr.yml, es.yml, ...)
```
