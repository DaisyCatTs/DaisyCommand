# DaisyCommands

DaisyCommands is a Kotlin-first command framework for modern Paper plugins.

It focuses on a small public surface:

- Kotlin DSL for command trees
- Typed arguments via `ArgumentRef<T>`
- Fast subcommand routing and tab completion
- Permission inheritance
- Player and console execution constraints
- Generated usage and help output
- Optional path-scoped cooldowns
- Paper `BasicCommand` registration instead of command-map reflection

Migration notes for `1.x` users are in [MIGRATION.md](MIGRATION.md).

## Features

- Clean DSL for root commands and nested subcommands
- Required arguments are non-null; optional arguments are nullable
- One parser contract for parsing and suggestions
- Built-in parsers for common Paper and Minecraft types
- Paper-only registration with `JavaPlugin.registerCommands(...)`
- Thin deprecated compatibility shims for older `daisyCommand` style entrypoints

## Install

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

## Simple Example

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

## Advanced Example

```kotlin
import cat.daisy.command.core.registerCommands
import cat.daisy.command.dsl.command
import java.time.Duration
import org.bukkit.plugin.java.JavaPlugin

class ExamplePlugin : JavaPlugin() {
    override fun onEnable() {
        registerCommands(
            command("island") {
                description("Island management")
                aliases("is")

                sub("invite") {
                    permission("island.invite")
                    cooldown(Duration.ofSeconds(30), bypassPermission = "island.invite.bypass")

                    val target = player("target")
                    val note = text("note").optional()

                    executePlayer {
                        val extraNote = note() ?: "No note"
                        reply("Invited ${target().name} with note: $extraNote")
                    }
                }

                sub("visit") {
                    val islandName = string("name")

                    execute {
                        reply("Visiting island ${islandName()}")
                    }
                }
            },
        )
    }
}
```
