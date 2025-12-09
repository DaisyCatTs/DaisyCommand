package cat.daisy.command.core
import cat.daisy.command.arguments.ArgumentDef
import cat.daisy.command.context.CommandContext
import cat.daisy.command.context.TabContext
import cat.daisy.command.cooldown.CooldownManager
import cat.daisy.command.text.TextUtils.mm
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

/**
 * DaisyCommand Core Framework
 *
 * High-performance command registration with:
 * - Zero-reflection execution (after initial setup)
 * - Thread-safe command storage
 * - Dynamic registration (no plugin.yml needed)
 * - Nested subcommands support
 * - Automatic help generation
 */
object CommandFramework {
    private val commands = ConcurrentHashMap<String, DaisyCommand>()
    private var commandMap: CommandMap? = null
    private var pluginInstance: JavaPlugin? = null
    private var pluginName: String = "daisycommand"
    // ═══════════════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Initialize the framework with your plugin instance.
     * Call this in your plugin's onEnable().
     */
    fun initialize(plugin: JavaPlugin) {
        pluginInstance = plugin
        pluginName = plugin.name.lowercase()
        commandMap = fetchCommandMap(plugin)
    }

    private fun fetchCommandMap(plugin: JavaPlugin): CommandMap =
        plugin.server.javaClass.getDeclaredField("commandMap").run {
            isAccessible = true
            get(plugin.server) as CommandMap
        }
    // ═══════════════════════════════════════════════════════════════════════════════
    // REGISTRATION
    // ═══════════════════════════════════════════════════════════════════════════════

    /**
     * Register a command with the framework.
     */
    fun register(command: DaisyCommand) {
        val map =
            commandMap ?: throw IllegalStateException("CommandFramework not initialized! Call CommandFramework.initialize(plugin) first.")
        val wrapper = BukkitCommandWrapper(command)
        map.register(pluginName, wrapper)
        commands[command.name.lowercase()] = command
        command.aliases.forEach { alias ->
            commands[alias.lowercase()] = command
        }
    }

    /**
     * Unregister a specific command.
     */
    fun unregister(name: String) {
        val map = commandMap ?: return
        val lowerName = name.lowercase()
        try {
            val knownCommands = map.knownCommands
            knownCommands.remove(lowerName)
            knownCommands.remove("$pluginName:$lowerName")
        } catch (_: Exception) {
        }
        commands.remove(lowerName)
    }

    /**
     * Unregister all commands from this framework.
     */
    fun unregisterAll() {
        val map = commandMap ?: return
        commands.keys.forEach { name ->
            try {
                val knownCommands = map.knownCommands
                knownCommands.remove(name)
                knownCommands.remove("$pluginName:$name")
            } catch (_: Exception) {
            }
        }
        commands.clear()
    }

    /**
     * Get a registered command by name.
     */
    operator fun get(name: String): DaisyCommand? = commands[name.lowercase()]

    /**
     * Check if a command is registered.
     */
    fun isRegistered(name: String): Boolean = commands.containsKey(name.lowercase())

    /**
     * Get all registered commands.
     */
    fun getAll(): Collection<DaisyCommand> = commands.values.distinctBy { it.name }

    /**
     * Shutdown the framework - call in onDisable().
     */
    fun shutdown() {
        unregisterAll()
        CooldownManager.clearAll()
        commandMap = null
        pluginInstance = null
    }
}
// ═══════════════════════════════════════════════════════════════════════════════
// BUKKIT WRAPPER
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Bridges DaisyCommand to Bukkit's command system.
 */
private class BukkitCommandWrapper(
    private val command: DaisyCommand,
) : Command(command.name) {
    init {
        description = command.description
        usageMessage = command.usage
        aliases = command.aliases.toList()
        permission = command.permission
    }

    override fun execute(
        sender: CommandSender,
        label: String,
        args: Array<out String>,
    ): Boolean = command.execute(sender, args.toList(), label)

    override fun tabComplete(
        sender: CommandSender,
        alias: String,
        args: Array<out String>,
    ): List<String> = command.tabComplete(sender, args.toList())
}
// ═══════════════════════════════════════════════════════════════════════════════
// DAISY COMMAND
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Core command class with subcommand support.
 */
class DaisyCommand(
    val name: String,
    val description: String = "",
    val usage: String = "/$name",
    val permission: String? = null,
    val aliases: Array<String> = emptyArray(),
    val playerOnly: Boolean = false,
    val cooldown: Int = 0,
    val cooldownMessage: String? = null,
    val cooldownBypassPermission: String? = null,
    val arguments: List<ArgumentDef> = emptyList(),
) {
    private val subcommands = ConcurrentHashMap<String, SubCommand>()
    private var executor: (CommandContext.() -> Unit)? = null
    private var tabProvider: (TabContext.() -> List<String>)? = null

    // ─────────────────────────────────────────────────────────────────────────
    // CONFIGURATION
    // ─────────────────────────────────────────────────────────────────────────
    fun addSubcommand(
        name: String,
        subcommand: SubCommand,
    ) {
        subcommands[name.lowercase()] = subcommand
        subcommand.aliases.forEach { alias ->
            subcommands[alias.lowercase()] = subcommand
        }
    }

    fun onExecute(block: CommandContext.() -> Unit): DaisyCommand {
        executor = block
        return this
    }

    fun tabComplete(block: TabContext.() -> List<String>): DaisyCommand {
        tabProvider = block
        return this
    }

    fun getSubcommands(): Map<String, SubCommand> = subcommands.toMap()

    // ─────────────────────────────────────────────────────────────────────────
    // EXECUTION
    // ─────────────────────────────────────────────────────────────────────────
    fun execute(
        sender: CommandSender,
        args: List<String>,
        label: String = name,
    ): Boolean {
        // Player-only check
        if (playerOnly && sender !is Player) {
            sender.sendMessage("<#e74c3c>✖</> <gray>This command can only be used by players!".mm())
            return true
        }
        // Permission check
        if (permission != null && !sender.hasPermission(permission)) {
            sender.sendMessage("<#e74c3c>✖</> <gray>You don't have permission to use this command!".mm())
            return true
        }
        // Cooldown check
        if (cooldown > 0 && sender is Player) {
            val remaining = CooldownManager.getRemainingCooldown(sender, name, cooldown)
            if (remaining > 0 && (cooldownBypassPermission == null || !sender.hasPermission(cooldownBypassPermission))) {
                val msg = cooldownMessage ?: "<#e74c3c>✖</> <gray>Please wait <white>$remaining</white> seconds before using this again."
                sender.sendMessage(msg.mm())
                return true
            }
        }
        // Try subcommand first
        if (args.isNotEmpty()) {
            val subName = args[0].lowercase()
            subcommands[subName]?.let { sub ->
                return sub.execute(sender, args.drop(1), label)
            }
        }
        // Parse named arguments
        val namedArgs = parseArguments(args, arguments)
        // Execute main handler or show help
        executor?.let {
            CommandContext(sender, args, namedArgs, label).it()
            return true
        }
        // No handler, show subcommands if available
        if (subcommands.isNotEmpty()) {
            sendHelp(sender)
        }
        return true
    }

    fun tabComplete(
        sender: CommandSender,
        args: List<String>,
    ): List<String> {
        if (permission != null && !sender.hasPermission(permission)) {
            return emptyList()
        }
        return when {
            args.size == 1 -> {
                val prefix = args[0].lowercase()
                val subSuggestions =
                    subcommands.entries
                        .asSequence()
                        .filter { (name, sub) -> name.startsWith(prefix) && sub.hasPermission(sender) }
                        .map { it.key }
                        .distinct()
                        .toList()
                val argSuggestions =
                    if (arguments.isNotEmpty()) {
                        getArgumentCompletions(0, args[0], arguments, sender)
                    } else {
                        emptyList()
                    }
                val customSuggestions = tabProvider?.let { TabContext(sender, args).it() } ?: emptyList()
                (subSuggestions + argSuggestions + customSuggestions).distinct()
            }

            args.size > 1 -> {
                val subName = args[0].lowercase()
                subcommands[subName]?.tabComplete(sender, args.drop(1))
                    ?: tabProvider?.let { TabContext(sender, args).it() }
                    ?: getArgumentCompletions(args.size - 1, args.last(), arguments, sender)
            }

            else -> {
                tabProvider?.let { TabContext(sender, args).it() } ?: emptyList()
            }
        }
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage("<#3498db>━━━━━━━━━━ <white><bold>$name</bold></white> <#3498db>━━━━━━━━━━".mm())
        val visibleSubs =
            subcommands.entries
                .filter { it.value.hasPermission(sender) }
                .distinctBy { it.value }
        if (visibleSubs.isEmpty()) {
            sender.sendMessage("<gray>No available commands.".mm())
        } else {
            visibleSubs.forEach { (subName, sub) ->
                sender.sendMessage("<#f1c40f>/$name $subName <dark_gray>- <gray>${sub.description}".mm())
            }
        }
        sender.sendMessage("<#3498db>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━".mm())
    }
}
