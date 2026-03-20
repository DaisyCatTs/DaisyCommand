# Migrating From DaisyCommands 1.x To 2.0

## What changed

- DaisyCommands 2.0 is Paper-only.
- Registration now goes through `JavaPlugin.registerCommands(...)`.
- The old reflection-based command-map registration path is removed from the main API.
- The command model is immutable and compiled before registration.
- Arguments now resolve through typed `ArgumentRef<T>` values instead of string-key access as the primary API.
- Parsing and suggestions now use one parser contract.
- Help, usage, sender checks, permissions, and cooldowns are handled by the compiled dispatcher.

## New preferred style

### Before

```kotlin
daisyCommand("island") {
    subcommand("create") {
        playerExecutor {
            success("Created")
        }
    }
}
```

### After

```kotlin
command("island") {
    sub("create") {
        executePlayer {
            reply("Created")
        }
    }
}
```

## Registration

### Before

```kotlin
override fun onEnable() {
    DaisyCommands.initialize(this)
    daisyCommand("hello") {
        onExecute {
            reply("Hello")
        }
    }
}
```

### After

```kotlin
override fun onEnable() {
    registerCommands(
        command("hello") {
            execute {
                reply("Hello")
            }
        },
    )
}
```

## Arguments

### Before

```kotlin
daisyCommand("warp") {
    playerArgument("target")

    playerExecutor {
        val target = getPlayer("target") ?: return@playerExecutor
        reply(target.name)
    }
}
```

### After

```kotlin
command("warp") {
    val target = player("target")

    executePlayer {
        reply(target().name)
    }
}
```

## Compatibility shims

These still exist, but they are deprecated:

- `daisyCommand(...)`
- `buildCommand(...)`
- `subcommand(...)`
- `onExecute { ... }`
- `playerExecutor { ... }`
- String-key getters such as `getString("name")`

They are retained only to ease migration. New code should use the 2.0 DSL directly.
