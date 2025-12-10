<p align="center">
  <img src="https://img.shields.io/badge/DaisyCommand-Modern%20Command%20Framework-ff69b4?style=for-the-badge&logo=kotlin&logoColor=white" alt="DaisyCommand"/>
</p>

<h1 align="center">🌸 DaisyCommand</h1>

<p align="center">
  <strong>A modern, high-performance Kotlin command framework for Paper/Spigot plugins</strong>
</p>

<p align="center">
  <a href="https://jitpack.io/#fu3i0n/DaisyCommand"><img src="https://jitpack.io/v/fu3i0n/DaisyCommand.svg" alt="JitPack"/></a>
  <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT"/></a>
  <a href="https://kotlinlang.org"><img src="https://img.shields.io/badge/Kotlin-2.2.21-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin"/></a>
  <a href="https://papermc.io"><img src="https://img.shields.io/badge/Paper-1.21+-4CAF50.svg" alt="Paper"/></a>
  <a href="https://openjdk.org"><img src="https://img.shields.io/badge/Java-21+-ED8B00.svg?logo=openjdk&logoColor=white" alt="Java 21+"/></a>
</p>

<p align="center">
  <a href="#-features">Features</a> •
  <a href="#-installation">Installation</a> •
  <a href="#-quick-start">Quick Start</a> •
  <a href="#-dsl-reference">DSL Reference</a> •
  <a href="#-argument-types">Arguments</a> •
  <a href="#-context-api">Context</a> •
  <a href="#-advanced">Advanced</a>
</p>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🎯 **Beautiful Kotlin DSL** | Clean, type-safe, expressive syntax for defining commands |
| ☕ **Full Java Support** | Fluent API that works seamlessly with Java plugins |
| 🎨 **MiniMessage Native** | Built-in support for gradients, hex colors, and modern formatting |
| 🔒 **Type-Safe Arguments** | 17+ built-in parsers with automatic validation |
| 📁 **Nested Subcommands** | Unlimited subcommand depth with independent permissions |
| ⏱️ **Cooldown System** | Thread-safe cooldowns with bypass permissions |
| 📝 **Smart Tab Completion** | Automatic completions + custom providers |
| ⚡ **Zero Configuration** | No plugin.yml command entries needed |
| 🛡️ **Security First** | Input validation, length limits, and sanitization |
| 🚀 **High Performance** | Zero-reflection execution after initial setup |

---

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

> 💡 **Tip:** Replace `1.0.0` with `main-SNAPSHOT` for the latest development build.

---

## 🚀 Quick Start

### Kotlin

```kotlin
import cat.daisy.command.core.DaisyCommands
import cat.daisy.command.dsl.daisyCommand
import org.bukkit.plugin.java.JavaPlugin

class MyPlugin : JavaPlugin() {
    
    override fun onEnable() {
        // Initialize DaisyCommand
        DaisyCommands.initialize(this)
        
        // Create your first command!
        daisyCommand("hello") {
            description = "Say hello to a player"
            permission = "myplugin.hello"
            
            playerArgument("target", optional = true)
            
            playerExecutor {
                val target = getPlayer("target") ?: player
                success("Hello, ${target.name}!")
            }
        }
    }
    
    override fun onDisable() {
        DaisyCommands.shutdown()
    }
}
```

### Java

```java
import cat.daisy.command.DaisyCommandAPI;
import cat.daisy.command.core.DaisyCommands;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {
    
    @Override
    public void onEnable() {
        // Initialize DaisyCommand
        DaisyCommands.INSTANCE.initialize(this);
        
        // Create your first command!
        DaisyCommandAPI.create("hello", builder -> {
            builder.setDescription("Say hello to a player");
            builder.setPermission("myplugin.hello");
            builder.playerArgument("target", true);  // optional = true
            
            builder.onExecute(ctx -> {
                Player target = ctx.getPlayer("target");
                if (target == null) target = ctx.getPlayer();
                if (target != null) {
                    ctx.success("Hello, " + target.getName() + "!");
                }
            });
        });
    }
    
    @Override
    public void onDisable() {
        DaisyCommands.INSTANCE.shutdown();
    }
}
```

---

## 📖 DSL Reference

### Basic Command

```kotlin
daisyCommand("example") {
    description = "Example command"
    permission = "myplugin.example"
    withAliases("ex", "e")
    
    onExecute {
        success("Hello from example command!")
    }
}
```

### Player-Only Command

```kotlin
daisyCommand("fly") {
    description = "Toggle flight"
    permission = "myplugin.fly"
    
    playerExecutor {
        player.allowFlight = !player.allowFlight
        val status = if (player.allowFlight) "enabled" else "disabled"
        successWithSound("Flight $status!")
    }
}
```

### Subcommands

```kotlin
daisyCommand("team") {
    description = "Team management"
    
    subcommand("create") {
        description = "Create a team"
        permission = "team.create"
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
    
    // Nested subcommands work too!
    subcommand("settings") {
        subcommand("name") {
            stringArgument("newName")
            onExecute { success("Renamed to '${getString("newName")}'!") }
        }
        
        subcommand("privacy") {
            choiceArgument("mode", "public", "private", "invite")
            onExecute { success("Privacy set to ${getString("mode")}!") }
        }
    }
}
```

### Cooldowns

```kotlin
daisyCommand("heal") {
    description = "Heal yourself"
    cooldown = 60  // 60 seconds
    cooldownMessage = "<red>Wait <white>{remaining}</white> seconds!"
    cooldownBypassPermission = "myplugin.heal.bypass"
    
    playerExecutor {
        player.health = player.maxHealth
        successWithSound("Healed!")
    }
}
```

---

## 🔧 Argument Types

DaisyCommand provides **17+ built-in argument types** with automatic validation and tab completion:

### Primitives

| Method | Type | Description |
|--------|------|-------------|
| `stringArgument` | `String` | Single word (max 256 chars) |
| `greedyStringArgument` | `String` | All remaining text (max 1024 chars) |
| `intArgument` | `Int` | Integer with optional min/max |
| `longArgument` | `Long` | Long integer with optional min/max |
| `doubleArgument` | `Double` | Decimal with optional min/max |
| `floatArgument` | `Float` | Float with optional min/max |
| `booleanArgument` | `Boolean` | `true/false`, `yes/no`, `on/off`, `1/0` |

### Minecraft Types

| Method | Type | Description |
|--------|------|-------------|
| `playerArgument` | `Player` | Online player with tab completion |
| `offlinePlayerArgument` | `OfflinePlayer` | Any player who has joined |
| `worldArgument` | `World` | Loaded world with tab completion |
| `materialArgument` | `Material` | Minecraft material |
| `gameModeArgument` | `GameMode` | survival, creative, spectator, adventure |
| `entityTypeArgument` | `EntityType` | Entity type |

### Special Types

| Method | Type | Description |
|--------|------|-------------|
| `uuidArgument` | `UUID` | Valid UUID |
| `durationArgument` | `Duration` | Time format: `1d2h30m45s` |
| `choiceArgument` | `String` | Fixed set of choices |
| `enumArgument<E>` | `E` | Any enum type |
| `customArgument` | `T` | Custom parser |

### Examples

```kotlin
// Range validation
daisyCommand("setlevel") {
    intArgument("level", min = 1, max = 100)
    
    playerExecutor {
        val level = getInt("level")!!
        success("Level set to $level!")
    }
}

// Enum argument
daisyCommand("gamemode") {
    enumArgument<GameMode>("mode")
    playerArgument("target", optional = true)
    
    onExecute {
        val mode = getArg<GameMode>("mode")!!
        val target = getPlayer("target") ?: player!!
        target.gameMode = mode
        success("Set ${target.name}'s gamemode to ${mode.name}!")
    }
}

// Duration parsing (1d2h30m = 1 day, 2 hours, 30 minutes)
daisyCommand("tempban") {
    playerArgument("player")
    durationArgument("duration")
    greedyStringArgument("reason", optional = true)
    
    onExecute {
        val target = getPlayer("player")!!
        val duration = getArg<Duration>("duration")!!
        val reason = getString("reason") ?: "No reason"
        success("Banned ${target.name} for ${duration.toMinutes()} minutes: $reason")
    }
}

// Custom choices
daisyCommand("difficulty") {
    choiceArgument("level", "peaceful", "easy", "normal", "hard")
    
    onExecute {
        val level = getString("level")!!
        success("Difficulty set to $level!")
    }
}
```

---

## 💬 Context API

### Argument Access

```kotlin
// Named arguments (from DSL definitions)
getString("name")           // String?
getInt("amount")            // Int?
getLong("timestamp")        // Long?
getDouble("multiplier")     // Double?
getFloat("speed")           // Float?
getBoolean("enabled")       // Boolean?
getPlayer("target")         // Player?
getArg<CustomType>("key")   // T?

// Positional arguments (raw access)
arg(0)                      // String? - first argument
argInt(0)                   // Int?
argDouble(0)                // Double?
argPlayer(0)                // Player?
argOr(0, "default")         // String with default
joinArgs(1)                 // Join args from index 1
argCount                    // Number of arguments
```

### Messaging (MiniMessage)

```kotlin
// Basic messaging
send("<gradient:red:blue>Hello World!</gradient>")
reply("This is a reply")

// Prefixed messages
success("Operation completed!")     // ✔ green prefix
error("Something went wrong!")      // ✖ red prefix
warn("Be careful!")                 // ⚠ yellow prefix
info("Did you know?")               // ✦ blue prefix

// Broadcast to all players
broadcast("Server announcement!")
```

### Player-Only Features (PlayerContext)

```kotlin
playerExecutor {
    // Action bar
    actionBar("<yellow>+50 XP</yellow>")
    
    // Titles
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

### Flow Control

```kotlin
onExecute {
    // Require player
    requirePlayer {
        player.health = player.maxHealth
    }
    
    // Require permission
    requirePermission("admin.special") {
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

---

## 📝 Tab Completion

### Automatic Completion

Arguments automatically provide tab completion:
- `playerArgument` → Online player names
- `worldArgument` → Loaded world names
- `materialArgument` → Material names
- `gameModeArgument` → Gamemode names
- `booleanArgument` → "true", "false"
- `choiceArgument` → Your defined choices
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
    players()           // Online player names (filtered)
    worlds()            // World names (filtered)
    filter("a", "b")    // Filter options by current input
    currentArg          // Current argument being typed
    argIndex            // Index of current argument (0-based)
    none()              // Empty list
}
```

---

## 🎨 DaisyText

MiniMessage utilities available throughout your code:

```kotlin
import cat.daisy.command.text.DaisyText.mm
import cat.daisy.command.text.DaisyText.Colors

// Parse MiniMessage to Component
val component = "<gradient:red:blue>Hello!</gradient>".mm()

// Gradients
val rainbow = "Rainbow Text".rainbow()
val custom = "Custom".gradient("#FF0000", "#00FF00")

// Placeholders
val msg = "Hello, {player}!".replacePlaceholders("player" to player.name)

// Predefined colors
Colors.PRIMARY    // #3498db
Colors.SUCCESS    // #2ecc71
Colors.ERROR      // #e74c3c
Colors.WARNING    // #f1c40f

// Legacy color conversion
val converted = "&aGreen &cRed".convertLegacyColors()

// Strip all formatting
val plain = "<bold>Hello</bold>".stripColors()
```

---

## 🔄 Lifecycle & Dynamic Registration

```kotlin
class MyPlugin : JavaPlugin() {
    
    override fun onEnable() {
        // Initialize FIRST
        DaisyCommands.initialize(this)
        
        // Register commands
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
// Build without registering
val cmd = buildCommand("dynamic") {
    onExecute { success("Dynamic!") }
}

// Register later
DaisyCommands.register(cmd)

// Unregister
DaisyCommands.unregister("dynamic")

// Check status
DaisyCommands.isRegistered("hello")

// Get all commands
DaisyCommands.getAll()
```

---

## 🛡️ Security

DaisyCommand includes comprehensive security measures:

### Input Validation
- **String limits**: Single args max 256 chars, greedy max 1024 chars
- **Type validation**: All parsers validate before processing
- **Range checking**: Numeric arguments support min/max

### Permission System
- Commands check permissions before execution
- Subcommands have independent permissions
- Tab completion respects permissions

### Thread Safety
- `ConcurrentHashMap` for command/cooldown storage
- Safe for async access patterns

### Best Practices

```kotlin
daisyCommand("admin") {
    permission = "myplugin.admin"  // Always set for sensitive commands
    
    subcommand("execute") {
        greedyStringArgument("command")
        
        onExecute {
            val cmd = getString("command") ?: return@onExecute
            // Add additional validation as needed
            if (cmd.contains("dangerous")) {
                error("Blocked!")
                return@onExecute
            }
        }
    }
}
```

---

## 📋 Requirements

| Requirement | Version |
|-------------|---------|
| Java | 21+ |
| Kotlin | 2.1.0+ |
| Paper | 1.21+ (or compatible fork) |

---

## 📄 License

This project is licensed under the **MIT License** - see the [LICENSE](LICENSE) file for details.

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing`)
5. Open a Pull Request

---

## 📞 Support

- 🐛 **Issues**: [GitHub Issues](https://github.com/fu3i0n/DaisyCommand/issues)
- 💬 **Discussions**: [GitHub Discussions](https://github.com/fu3i0n/DaisyCommand/discussions)

---

<p align="center">
  Made with 💜 by <a href="https://github.com/fu3i0n">fu3i0n</a>
</p>

