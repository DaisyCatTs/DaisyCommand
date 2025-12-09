# DaisyCommand

[![JitPack](https://jitpack.io/v/fu3i0n/DaisyCommand.svg)](https://jitpack.io/#fu3i0n/DaisyCommand)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blue.svg)](https://kotlinlang.org)
[![Paper](https://img.shields.io/badge/Paper-1.21+-green.svg)](https://papermc.io)

A modern, high-performance Kotlin command framework for Paper/Spigot plugins. Built with type safety, security, and developer experience in mind.

## ✨ Features

- **🎯 Beautiful Kotlin DSL** - Clean, type-safe syntax for defining commands
- **☕ Full Java Support** - Works seamlessly with Java plugins via fluent API
- **🎨 MiniMessage Support** - Native support for modern text formatting with gradients and hex colors
- **🔒 Type-Safe Arguments** - Built-in parsers with validation for players, materials, worlds, and more
- **📁 Nested Subcommands** - Infinite subcommand nesting with independent permissions
- **⏱️ Cooldown System** - Thread-safe cooldowns with bypass permissions
- **📝 Tab Completion** - Automatic and customizable tab completion
- **⚡ Zero Configuration** - No plugin.yml command entries needed
- **🛡️ Security First** - Input validation, length limits, and sanitization built-in
- **🚀 High Performance** - Zero-reflection execution after initial setup

## 📦 Installation

### Gradle (Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.fu3i0n:DaisyCommand:1.0.0")
}
```

### Gradle (Groovy)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.fu3i0n:DaisyCommand:1.0.0'
}
```

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.fu3i0n</groupId>
    <artifactId>DaisyCommand</artifactId>
    <version>1.0.0</version>
</dependency>
```

> 💡 **Tip:** Use `main-SNAPSHOT` as version to get the latest commit from main branch.
```

## 🚀 Quick Start

### Kotlin

```kotlin
import cat.daisy.command.core.DaisyCommands
import cat.daisy.command.dsl.daisyCommand
import org.bukkit.plugin.java.JavaPlugin

class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        // Initialize the framework
        DaisyCommands.initialize(this)
        
        // Register your commands
        registerCommands()
    }
    
    override fun onDisable() {
        // Clean up
        DaisyCommands.shutdown()
    }
    
    private fun registerCommands() {
        daisyCommand("hello") {
            description = "A friendly greeting command"
            permission = "myplugin.hello"
            
            playerArgument("target", optional = true)
            
            playerExecutor {
                val target = getPlayer("target") ?: player
                success("Hello, ${target.name}!")
            }
        }
    }
}
```

### Java

```java
import cat.daisy.command.DaisyCommandAPI;
import cat.daisy.command.core.DaisyCommands;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        // Initialize the framework
        DaisyCommands.INSTANCE.initialize(this);
        
        // Register commands
        registerCommands();
    }
    
    @Override
    public void onDisable() {
        // Clean up
        DaisyCommands.INSTANCE.shutdown();
    }
    
    private void registerCommands() {
        DaisyCommandAPI.create("hello", builder -> {
            builder.setDescription("A friendly greeting command");
            builder.setPermission("myplugin.hello");
            builder.playerArgument("target", true); // optional = true
            
            builder.onExecute(ctx -> {
                var target = ctx.getPlayer("target");
                if (target == null) target = ctx.getPlayer();
                if (target != null) {
                    ctx.success("Hello, " + target.getName() + "!");
                }
            });
        });
    }
}
```
```

## 📖 DSL Reference

### Basic Command

```kotlin
daisyCommand("example") {
    description = "An example command"
    permission = "myplugin.example"
    withAliases("ex", "e")
    cooldown = 5  // 5 seconds
    
    onExecute {
        success("Hello from the example command!")
    }
}
```

### Player-Only Command

```kotlin
daisyCommand("fly") {
    description = "Toggle flight mode"
    permission = "myplugin.fly"
    
    playerExecutor {
        player.allowFlight = !player.allowFlight
        val status = if (player.allowFlight) "enabled" else "disabled"
        success("Flight mode $status!")
    }
}
```

### Subcommands

```kotlin
daisyCommand("team") {
    description = "Team management"
    
    subcommand("create") {
        description = "Create a new team"
        permission = "teams.create"
        stringArgument("name")
        
        onExecute {
            val name = getString("name") ?: return@onExecute error("Name required!")
            success("Team '$name' created!")
        }
    }
    
    subcommand("invite") {
        description = "Invite a player"
        playerArgument("player")
        
        playerExecutor {
            val target = getPlayer("player") ?: return@playerExecutor error("Player not found!")
            success("Invited ${target.name}!")
        }
    }
    
    // Nested subcommands
    subcommand("settings") {
        subcommand("name") {
            stringArgument("newName")
            onExecute { 
                val newName = getString("newName")!!
                success("Team renamed to '$newName'!")
            }
        }
        
        subcommand("privacy") {
            choiceArgument("mode", "public", "private", "invite")
            onExecute { 
                val mode = getString("mode")!!
                success("Privacy set to $mode!")
            }
        }
    }
}
```

### Available Argument Types

| Method | Type | Description |
|--------|------|-------------|
| `stringArgument` | `String` | Single word (max 256 chars) |
| `greedyStringArgument` | `String` | All remaining text (max 1024 chars) |
| `intArgument` | `Int` | Integer with optional min/max |
| `longArgument` | `Long` | Long integer with optional min/max |
| `doubleArgument` | `Double` | Decimal number with optional min/max |
| `floatArgument` | `Float` | Float number with optional min/max |
| `booleanArgument` | `Boolean` | true/false, yes/no, on/off, 1/0 |
| `playerArgument` | `Player` | Online player with tab completion |
| `offlinePlayerArgument` | `OfflinePlayer` | Any player who has joined |
| `worldArgument` | `World` | Loaded world with tab completion |
| `materialArgument` | `Material` | Minecraft material with tab completion |
| `gameModeArgument` | `GameMode` | survival, creative, spectator, adventure |
| `entityTypeArgument` | `EntityType` | Entity type with tab completion |
| `uuidArgument` | `UUID` | Valid UUID |
| `durationArgument` | `Duration` | Time format: 1d2h30m45s |
| `choiceArgument` | `String` | Fixed set of choices |
| `enumArgument<E>` | `E` | Any enum type |
| `customArgument` | `T` | Custom parser implementation |

### Argument Validation Examples

```kotlin
daisyCommand("setlevel") {
    // Integer with range validation
    intArgument("level", min = 1, max = 100)
    
    playerExecutor {
        val level = getInt("level") ?: return@playerExecutor error("Invalid level!")
        success("Level set to $level!")
    }
}

daisyCommand("gamemode") {
    // Enum argument with auto tab-completion
    enumArgument<GameMode>("mode")
    playerArgument("target", optional = true)
    
    onExecute {
        val mode = getArg<GameMode>("mode")!!
        val target = getPlayer("target") ?: player ?: return@onExecute error("Player required!")
        target.gameMode = mode
        success("Set ${target.name}'s gamemode to ${mode.name}!")
    }
}
```

## 💬 Context Methods

### CommandContext / PlayerContext

#### Argument Access

```kotlin
// Named arguments (from DSL definitions)
getString("name")           // String?
getInt("amount")           // Int?
getLong("timestamp")       // Long?
getDouble("multiplier")    // Double?
getFloat("speed")          // Float?
getBoolean("enabled")      // Boolean?
getPlayer("target")        // Player?
getArg<CustomType>("key")  // T?

// Positional arguments (raw access)
arg(0)                     // String? - First argument
argInt(0)                  // Int? - Parse as int
argDouble(0)               // Double? - Parse as double
argPlayer(0)               // Player? - Get online player
argOr(0, "default")        // String with default
joinArgs(1)                // Join args from index 1
argCount                   // Number of arguments
```

#### Messaging (MiniMessage)

```kotlin
send("<gradient:red:blue>Hello World!</gradient>")
success("Operation completed!")      // ✔ prefix (green)
error("Something went wrong!")       // ✖ prefix (red)
warn("Be careful!")                  // ⚠ prefix (yellow)
info("Did you know?")                // ✦ prefix (blue)
broadcast("Server announcement!")   // Send to all players
```

#### Player-Only Features (PlayerContext)

```kotlin
playerExecutor {
    // Action bar
    actionBar("<yellow>+50 XP</yellow>")
    
    // Title
    title(
        title = "<gold>Level Up!</gold>",
        subtitle = "<gray>You are now level 10</gray>",
        fadeIn = Duration.ofMillis(500),
        stay = Duration.ofSeconds(3),
        fadeOut = Duration.ofMillis(500)
    )
    
    // Sounds
    sound(Sound.ENTITY_PLAYER_LEVELUP, volume = 0.5f, pitch = 1.5f)
    
    // Combined message + sound
    successWithSound("Achievement unlocked!")
    errorWithSound("Not enough resources!")
    infoWithSound("New quest available!")
}
```

#### Flow Control

```kotlin
onExecute {
    // Require player
    requirePlayer {
        // This block only runs if sender is a player
        player.health = player.maxHealth
    }
    
    // Require permission
    requirePermission("admin.special") {
        // Only runs if sender has permission
        success("Admin action performed!")
    }
    
    // Require argument count
    requireArgs(2, "Usage: /cmd <arg1> <arg2>") {
        // Only runs if at least 2 args provided
    }
    
    // Parse with validation
    withPlayer(0) { target ->
        success("Found player: ${target.name}")
    }
    
    withInt(1) { amount ->
        success("Amount: $amount")
    }
}
```

## 🔧 Tab Completion

### Automatic Completion

Arguments automatically provide tab completion based on their type:
- `playerArgument` → Online player names
- `worldArgument` → Loaded world names
- `materialArgument` → Material names (limited to 30)
- `gameModeArgument` → Gamemode names
- `booleanArgument` → "true", "false"
- `choiceArgument` → Defined choices
- `enumArgument` → Enum values

### Custom Completion

```kotlin
daisyCommand("warp") {
    stringArgument("location")
    
    tabComplete {
        when (argIndex) {
            0 -> filter("spawn", "hub", "arena", "shop", "mine")
            else -> none()
        }
    }
}
```

### TabContext Helpers

```kotlin
tabComplete {
    players()      // Online player names filtered by current input
    worlds()       // World names filtered by current input
    filter("a", "b", "c")  // Filter options by current input
    currentArg     // Current argument being typed
    argIndex       // Index of current argument (0-based)
    none()         // Empty list
}
```

## ⏱️ Cooldowns

```kotlin
daisyCommand("heal") {
    cooldown = 60  // 60 seconds
    cooldownMessage = "<red>Please wait <white>{remaining}</white> seconds before healing again!"
    cooldownBypassPermission = "myplugin.heal.bypass"
    
    playerExecutor {
        player.health = player.maxHealth
        successWithSound("You have been healed!")
    }
}
```

### Manual Cooldown Control

```kotlin
onExecute {
    // Check cooldown without triggering it
    val remaining = checkCooldown("special-action", 30)
    if (remaining > 0) {
        error("Wait $remaining seconds!")
        return@onExecute
    }
    
    // Check and trigger cooldown
    if (isOnCooldown("special-action", 30, bypassPermission = "admin.bypass")) {
        return@onExecute
    }
    
    success("Action performed!")
}
```

## 🎨 DaisyText

MiniMessage utilities available throughout your code:

```kotlin
import cat.daisy.command.text.DaisyText.mm
import cat.daisy.command.text.DaisyText.Colors

// Parse MiniMessage to Component
val component = "<gradient:red:blue>Hello World!</gradient>".mm()

// Gradients
val rainbow = "Rainbow Text".rainbow()
val custom = "Custom".gradient("#FF0000", "#00FF00", "#0000FF")

// Placeholders
val msg = "Hello, {player}! You have {coins} coins."
    .replacePlaceholders(
        "player" to player.name,
        "coins" to 100
    )

// Predefined colors
val primary = Colors.PRIMARY      // #3498db
val success = Colors.SUCCESS      // #2ecc71
val error = Colors.ERROR          // #e74c3c
val warning = Colors.WARNING      // #f1c40f

// Legacy color conversion (& codes to MiniMessage)
val converted = "&aGreen &cRed".convertLegacyColors()  // <green>Green <red>Red

// Strip all formatting
val plain = "<bold>Hello</bold>".stripColors()  // "Hello"
```

## 🛡️ Security

DaisyCommand includes built-in security measures:

### Input Validation
- **String length limits**: Single arguments max 256 chars, greedy strings max 1024 chars
- **Type validation**: All parsers validate input types before processing
- **Range checking**: Numeric arguments support min/max constraints

### Permission Checks
- Commands check permissions before execution
- Subcommands have independent permission checks
- Tab completion respects permissions (hidden commands not shown)

### Thread Safety
- `ConcurrentHashMap` used for command and cooldown storage
- Safe for async access patterns

### Best Practices

```kotlin
daisyCommand("admin") {
    // Always set permissions for sensitive commands
    permission = "myplugin.admin"
    
    subcommand("execute") {
        // Validate greedy input
        greedyStringArgument("command")
        
        onExecute {
            val cmd = getString("command") ?: return@onExecute
            // Input is already length-validated by the framework
            // Add additional validation as needed
            if (cmd.contains("rm") || cmd.contains("delete")) {
                error("Dangerous command blocked!")
                return@onExecute
            }
            // Process safely...
        }
    }
}
```

## 🔄 Lifecycle

```kotlin
class MyPlugin : JavaPlugin() {
    override fun onEnable() {
        // Initialize FIRST
        DaisyCommands.initialize(this)
        
        // Then register commands
        registerCommands()
    }
    
    override fun onDisable() {
        // Cleanup - unregisters all commands and clears cooldowns
        DaisyCommands.shutdown()
    }
}
```

### Dynamic Registration

```kotlin
// Register at runtime
val cmd = buildCommand("dynamic") {
    onExecute { success("Dynamic command!") }
}
DaisyCommands.register(cmd)

// Unregister specific command
DaisyCommands.unregister("dynamic")

// Check if registered
if (DaisyCommands.isRegistered("hello")) {
    // ...
}

// Get all commands
val allCommands = DaisyCommands.getAll()
```

## 📋 Requirements

- **Java**: 21+
- **Kotlin**: 2.1.0+
- **Paper**: 1.21+ (or compatible fork)

## 📄 License

MIT License - See [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/fu3i0n/DaisyCommand/issues)
- **Discussions**: [GitHub Discussions](https://github.com/fu3i0n/DaisyCommand/discussions)

