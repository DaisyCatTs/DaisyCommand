# Migrating From DaisyCommands 2.x To 3.0

DaisyCommands 3.0 is a cleanup release focused on one thing: a Kotlin-only, Paper-first command API with one obvious DSL. If you are upgrading from 2.x, this guide covers the main breaking changes and the new preferred patterns.

## What Changed In 3.0

- DaisyCommands is now explicitly Kotlin-only in public API design and documentation.
- Legacy compatibility APIs were removed from the main artifact.
- String-key argument access was removed from execution contexts.
- Flags and options are now first-class alongside positional arguments.
- Message customization moved to `registerCommands { config { ... } }`.
- The repository branding is now `DaisyCatTs/DaisyCommand`.
- Package namespace and current published coordinates stay stable for now.

## Upgrade Checklist

- Replace any legacy command builders with `command(name) { ... }`.
- Replace string-key getters with typed `ArgumentRef<T>` values.
- Move registration to `registerCommands(...)` or `registerCommands { ... }`.
- Convert optional values to `optional()` or `default(...)`.
- Move message customization into registration config.
- Adopt flags and options where they remove positional boilerplate.
- Review plugin assumptions for Paper `1.21.11` and Java `21`.

## API Before And After

### Registration

Before:

```kotlin
registerCommands(
    command("hello") {
        execute {
            reply("Hello")
        }
    },
)
```

After:

```kotlin
registerCommands {
    config {
        messages {
            prefix = "<gray>[<yellow>Example</yellow>]</gray> "
        }
    }

    command("hello") {
        execute {
            reply("Hello")
        }
    }
}
```

### Typed Positional Access

Before:

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

After:

```kotlin
command("island") {
    sub("invite") {
        val target = player("target")
        val silent = flag("silent", "s")

        executePlayer {
            islandService.invite(player, target(), silent())
        }
    }
}
```

### Optional And Defaulted Values

Before:

```kotlin
command("mail") {
    val message = text("message").optional()

    execute {
        reply(message() ?: "Empty")
    }
}
```

After:

```kotlin
command("mail") {
    val message = text("message").optional()
    val mode = choice("mode", "normal", "priority").default("normal")

    executePlayer {
        reply("${mode()}: ${message() ?: "Empty"}")
    }
}
```

### Flags And Options

Before:

```kotlin
command("ban") {
    val target = player("target")
    executePlayer { }
}
```

After:

```kotlin
command("ban") {
    val target = player("target")
    val reason = stringOption("reason", "r").default("No reason")
    val silent = flag("silent", "s")

    executePlayer {
        moderationService.ban(player, target(), reason(), silent())
    }
}
```

## Registration Changes

The two supported registration paths are:

```kotlin
registerCommands(
    command("example") {
        execute { }
    },
)
```

```kotlin
registerCommands {
    config {
        messages {
            prefix = "<gray>[<yellow>Example</yellow>]</gray> "
        }
    }

    command("example") {
        execute { }
    }
}
```

Use the builder form when you want framework-wide message or theme customization.

## Arguments And Typed Access

3.0 expects you to define refs during DSL construction and resolve them directly inside handlers:

```kotlin
command("warp") {
    val target = player("target")
    val expires = durationOption("expires", "e").optional()

    executePlayer {
        reply("Warping ${target().name} for ${expires()}.")
    }
}
```

The main pattern is:

- required ref -> non-null typed value
- `optional()` -> nullable typed value
- `default(value)` -> non-null fallback value

## Removed Main-Artifact APIs

These are no longer part of the main 3.0 artifact:

- deprecated compatibility builders from older releases
- string-key getters like `getString("name")`
- broad legacy execution helpers that duplicated the typed-ref flow

The 3.0 API is intentionally narrower so plugin authors have one clear way to write commands.

## Breaking Changes Summary

- Kotlin-only public API direction
- Paper `1.21.11` target
- legacy compatibility APIs removed from the main artifact
- string-key argument access removed
- flags and options added as first-class syntax
- docs and examples now assume the 3.0 DSL only
