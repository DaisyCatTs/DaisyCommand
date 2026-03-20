<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-Only-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin Only"/>
  <img src="https://img.shields.io/badge/Paper-1.21.11-4CAF50?style=for-the-badge" alt="Paper 1.21.11"/>
  <img src="https://img.shields.io/badge/Version-3.0.0-111111?style=for-the-badge" alt="Version 3.0.0"/>
</p>

<h1 align="center">DaisyCommands</h1>

<p align="center">
  Kotlin-first command framework for modern Paper plugins.
</p>

<p align="center">
  Typed arguments, clean DSL, flags and options, fast routing, and Paper-native registration with one obvious API.
</p>

## Why DaisyCommands

- Built for Kotlin plugin authors first, not Java builders translated into Kotlin.
- Uses typed `ArgumentRef<T>` values instead of string-key argument lookups.
- Keeps one clear DSL instead of multiple overlapping command styles.
- Supports real plugin command trees with subcommands, requirements, options, cooldowns, and completions.
- Generates help and usage from the compiled command model.
- Stays focused on commands instead of turning into a giant framework.

## Features

| Feature | What it gives you |
|---|---|
| Nested subcommands | Clean command trees with `sub(...)` |
| Typed refs | `ArgumentRef<T>` values resolved directly inside handlers |
| Positional args plus flags/options | `player("target")`, `flag("silent")`, `durationOption("expires")` |
| Optional and defaulted values | `optional()` and `default(...)` for cleaner handlers |
| Built-in parsers | Strings, text, numbers, booleans, players, worlds, materials, UUIDs, durations, choices, enums, and custom parsers |
| Sender constraints | `playerOnly()`, `consoleOnly()`, `executePlayer {}`, `executeConsole {}` |
| Custom requirements | `requires { ... }` checks alongside permissions |
| Path-scoped cooldowns | Success-only cooldowns with bypass permissions |
| Generated help and usage | Container help, usage output, and argument errors out of the box |
| Message theming | Lightweight `config { messages { ... } theme { ... } }` customization |

## Installation

Requirements: Java 21, modern Paper 1.21.11, Kotlin-first API usage.

Repository identity is now `DaisyCatTs/DaisyCommand`. Published coordinates stay stable for now.

### Gradle Kotlin DSL

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.fu3i0n:DaisyCommand:3.0.0")
}
```

### Gradle Groovy

```groovy
repositories {
    mavenCentral()
    maven { url 'https://repo.papermc.io/repository/maven-public/' }
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.fu3i0n:DaisyCommand:3.0.0'
}
```

## Quick Start

```kotlin
import cat.daisy.command.core.registerCommands
import cat.daisy.command.dsl.command
import org.bukkit.plugin.java.JavaPlugin

class ExamplePlugin : JavaPlugin() {
    override fun onEnable() {
        registerCommands(
            command("island") {
                sub("create") {
                    playerOnly()

                    executePlayer {
                        reply("Island created for ${player.name}.")
                    }
                }
            },
        )
    }
}
```

## DSL Overview

```kotlin
command("island") {
    description("Island management")
    aliases("is")

    sub("invite") {
        permission("island.invite")
        val target = player("target")
        val silent = flag("silent", "s")

        executePlayer {
            islandService.invite(player, target(), silent())
        }
    }

    sub("visit") {
        val island = string("name")
        val expires = durationOption("expires", "e").optional()

        execute {
            reply("Visiting ${island()} for ${expires() ?: java.time.Duration.ofMinutes(5)}.")
        }
    }
}
```

## Typed Arguments

```kotlin
command("mail") {
    val target = player("target")
    val message = text("message").optional()
    val mode = choice("mode", "normal", "priority").default("normal")

    executePlayer {
        reply("Mail to ${target().name}: ${message() ?: "No message"} (${mode()})")
    }
}
```

`ArgumentRef<T>` values are declared while building the command and resolved by calling the ref like a function inside the handler. Required refs are non-null. Optional refs return nullable values. Defaulted refs resolve to the provided value when omitted.

## Flags And Options

```kotlin
command("ban") {
    val target = player("target")
    val silent = flag("silent", "s")
    val reason = stringOption("reason", "r").default("No reason")
    val expires = durationOption("expires", "e").optional()

    executePlayer {
        moderationService.ban(
            actor = player,
            target = target(),
            reason = reason(),
            silent = silent(),
            expires = expires(),
        )
    }
}
```

Supported syntax:

- `--reason griefing`
- `-r griefing`
- `--silent`
- options before or after positional arguments
- `--` to stop option parsing

## Execution Contexts

```kotlin
command("demo") {
    sub("info") {
        execute {
            reply("Sender: ${sender.name}")
        }
    }

    sub("heal") {
        executePlayer {
            reply("Healing ${player.name}.")
        }
    }

    sub("reload") {
        executeConsole {
            reply("Reload requested by console.")
        }
    }
}
```

- `execute {}` works for any sender.
- `executePlayer {}` exposes `player`.
- `executeConsole {}` exposes `console`.
- Sender constraints are enforced before the handler runs.

## Permissions, Help, And Cooldowns

- Parent permissions and requirements are inherited by children.
- Container nodes show generated help output automatically.
- Invalid input renders an error plus usage.
- Cooldowns apply only after successful execution.
- Message styling can be customized through registration config.

```kotlin
import java.time.Duration

registerCommands {
    config {
        messages {
            prefix = "<gray>[<yellow>Islands</yellow>]</gray> "
        }
    }

    command("island") {
        permission("island.use")
        requires("You must own an island.") { player.hasPermission("island.owner") }

        sub("home") {
            cooldown(
                Duration.ofSeconds(30),
                bypassPermission = "island.home.bypass",
            )

            executePlayer {
                reply("Teleported home.")
            }
        }
    }
}
```

## Why Kotlin-Only

DaisyCommands 3.0 is intentionally optimized for Kotlin plugin authors. The public API is designed around Kotlin nullability, DSL ergonomics, and typed references. There are no legacy Java-style builders or string-key-first command handlers in the main artifact.

## Migration Note

3.0 is intentionally breaking. Legacy compatibility APIs were removed from the main artifact, string-key argument access is gone, and the docs now assume the Kotlin-first DSL only. See [MIGRATION.md](MIGRATION.md) for the 2.x to 3.0 upgrade notes.

## License

MIT. See [LICENSE](LICENSE).
