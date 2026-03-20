<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-First-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin First"/>
  <img src="https://img.shields.io/badge/Paper-Only-4CAF50?style=for-the-badge" alt="Paper Only"/>
  <img src="https://img.shields.io/badge/Version-2.0.0-111111?style=for-the-badge" alt="Version 2.0.0"/>
</p>

<h1 align="center">DaisyCommands</h1>

<p align="center">
  Kotlin-first command framework for modern Paper plugins.
</p>

<p align="center">
  Typed arguments, clean DSL, fast routing, and Paper-native registration without dragging your plugin into a giant command abstraction layer.
</p>

## Why DaisyCommands

- Built for Kotlin DSL ergonomics instead of Java-style builder noise.
- Uses typed `ArgumentRef<T>` values so command handlers are not centered around string-key lookups.
- Registers through Paper APIs instead of command-map reflection.
- Inherits parent permissions and sender constraints through the command tree.
- Generates usage and help output from the compiled command model.
- Stays intentionally small: focused on commands, parsing, completions, and execution flow.

## Features

| Feature | What it gives you |
|---|---|
| Nested subcommands | Clean tree-shaped command definitions with `sub(...)` |
| Typed arguments | `ArgumentRef<T>` values resolved inside handlers |
| Optional arguments | Nullable refs via `optional()` |
| Built-in parsers | Strings, text, numbers, booleans, players, worlds, materials, UUIDs, durations, choices, enums, and custom parsers |
| Execution constraints | `playerOnly()`, `consoleOnly()`, `executePlayer {}`, `executeConsole {}` |
| Path-scoped cooldowns | Cooldowns attached to executable nodes with bypass permission support |
| Generated help and usage | Container help and usage output based on the compiled command tree |
| Paper registration | `JavaPlugin.registerCommands(...)` and `JavaPlugin.registerCommands { ... }` |
| Migration shims | Deprecated `daisyCommand(...)`, `buildCommand(...)`, `subcommand(...)`, `onExecute`, and `playerExecutor` support for upgrades |

## Installation

Requirements: Java 21+, modern Paper, Kotlin-first API usage.

### Gradle Kotlin DSL

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.fu3i0n:DaisyCommand:2.0.0")
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
    implementation 'com.github.fu3i0n:DaisyCommand:2.0.0'
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
import cat.daisy.command.core.registerCommands
import cat.daisy.command.dsl.command
import org.bukkit.plugin.java.JavaPlugin

class ExamplePlugin : JavaPlugin() {
    override fun onEnable() {
        registerCommands(
            command("island") {
                description("Island management")
                aliases("is", "islandcmd")

                sub("invite") {
                    permission("island.invite")
                    val target = player("target")

                    executePlayer {
                        reply("Invited ${target().name} to your island.")
                    }
                }

                sub("visit") {
                    val islandName = string("name")

                    execute {
                        reply("Visiting island ${islandName()}.")
                    }
                }
            },
        )
    }
}
```

## Typed Arguments

```kotlin
command("island") {
    sub("invite") {
        val target = player("target")
        val note = text("note").optional()

        executePlayer {
            val message = note() ?: "No note"
            reply("Invited ${target().name}: $message")
        }
    }

    sub("privacy") {
        val mode = choice("mode", "public", "private", "invite")

        executePlayer {
            reply("Privacy set to ${mode()}.")
        }
    }
}
```

`ArgumentRef<T>` values are declared while building the DSL and resolved inside the handler by calling the ref like a function. Required arguments return a non-null typed value. Optional arguments return a nullable typed value.

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

- `execute {}` works for any sender and exposes `sender`.
- `executePlayer {}` exposes `player`.
- `executeConsole {}` exposes `console`.
- Sender constraints are enforced before the handler runs.

## Permissions, Help, and Cooldowns

- Parent permissions apply to nested subcommands.
- Container nodes without a handler show generated help output.
- Invalid input shows an error plus generated usage.
- Cooldowns are declared with `Duration` and can define a bypass permission.
- Cooldowns are applied after successful execution, not before.

```kotlin
import java.time.Duration

command("island") {
    permission("island.use")

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
```

## Migration Note

DaisyCommands 2.0 is intentionally breaking. Deprecated shims exist to ease upgrades, but the recommended API is the 2.0 DSL first. Full migration notes live in [MIGRATION.md](MIGRATION.md).

## License

MIT. See [LICENSE](LICENSE).
