# Migrating From DaisyCommands 1.x To 2.0

DaisyCommands 2.0 keeps the same core goal, a clean Kotlin command framework for Paper, but the mechanics changed in deliberate ways. This guide is for existing 1.x users who need to move to the new registration model, typed argument flow, and 2.0-first DSL.

## What Changed In 2.0

- DaisyCommands is now Paper-only.
- Commands are registered through `JavaPlugin.registerCommands(...)`.
- The runtime model is compiled and immutable.
- Typed `ArgumentRef<T>` values are the preferred way to access parsed arguments.
- Help, usage, sender constraints, and cooldown handling are built into the compiled command pipeline.
- Old builder-style entrypoints still exist as deprecated compatibility shims.

## Upgrade Checklist

- Replace `DaisyCommands.initialize(...)` plus implicit registration with `registerCommands(...)`.
- Convert `subcommand(...)` calls to `sub(...)`.
- Convert `onExecute { ... }` to `execute { ... }`.
- Convert `playerExecutor { ... }` to `executePlayer { ... }`.
- Move new code away from string-key-first argument access toward `ArgumentRef<T>`.
- Review your plugin assumptions around Paper-only support.
- Remove any dependency on the old runtime registration model beyond the deprecated shims.

## API Before/After

### Registration

Before:

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

After:

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

### Subcommand Declaration

Before:

```kotlin
daisyCommand("island") {
    subcommand("create") {
        playerExecutor {
            reply("Created")
        }
    }
}
```

After:

```kotlin
command("island") {
    sub("create") {
        executePlayer {
            reply("Created")
        }
    }
}
```

### Typed Player Argument

Before:

```kotlin
daisyCommand("island") {
    subcommand("invite") {
        playerArgument("target")

        playerExecutor {
            val target = getPlayer("target") ?: return@playerExecutor
            reply("Invited ${target.name}")
        }
    }
}
```

After:

```kotlin
command("island") {
    sub("invite") {
        val target = player("target")

        executePlayer {
            reply("Invited ${target().name}")
        }
    }
}
```

### Optional Text Argument

Before:

```kotlin
daisyCommand("mail") {
    greedyStringArgument("message", optional = true)

    onExecute {
        val message = getString("message") ?: "Empty"
        reply(message)
    }
}
```

After:

```kotlin
command("mail") {
    val message = text("message").optional()

    execute {
        reply(message() ?: "Empty")
    }
}
```

### Cooldown Declaration

Before:

```kotlin
daisyCommand("heal") {
    cooldown = 30
    cooldownBypassPermission = "myplugin.heal.bypass"

    playerExecutor {
        reply("Healed")
    }
}
```

After:

```kotlin
command("heal") {
    cooldown(
        java.time.Duration.ofSeconds(30),
        bypassPermission = "myplugin.heal.bypass",
    )

    executePlayer {
        reply("Healed")
    }
}
```

## Registration Changes

In 1.x, registration was centered around `DaisyCommands.initialize(...)` and compatibility-style command creation. In 2.0, the preferred path is explicit Paper registration from your plugin class:

```kotlin
registerCommands(
    command("example") {
        execute {
            reply("Ready")
        }
    },
)
```

You can also use the builder form:

```kotlin
registerCommands {
    command("example") {
        execute {
            reply("Ready")
        }
    }
}
```

## Arguments And Typed Access

The main conceptual shift is that 2.0 wants you to define argument refs during DSL construction and resolve them inside the handler:

```kotlin
command("warp") {
    val target = player("target")
    val note = text("note").optional()

    executePlayer {
        val message = note() ?: "No note"
        reply("Warping ${target().name}: $message")
    }
}
```

This is the preferred style for new code. String-key getters still exist on the execution context, but they are there to ease migration rather than define the long-term API shape.

## Compatibility Shims

These APIs still exist, but they are deprecated and should be treated as migration helpers:

- `daisyCommand(...)`
- `buildCommand(...)`
- `subcommand(...)`
- `onExecute { ... }`
- `playerExecutor { ... }`
- String-key getters such as `getString("name")`

They are retained to reduce upgrade friction, not as the recommended long-term interface for 2.0 code.

## Breaking Changes Summary

- Paper-only target.
- New `registerCommands(...)` registration path.
- Immutable compiled command model.
- Typed refs are now the preferred argument access style.
- Old builder-shaped entrypoints are deprecated.
- Official docs and examples now assume the 2.0 DSL first.
