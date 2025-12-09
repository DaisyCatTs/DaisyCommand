package cat.daisy.command.core
import cat.daisy.command.arguments.ArgumentDef
import cat.daisy.command.context.CommandContext
import cat.daisy.command.context.TabContext
import cat.daisy.command.cooldown.DaisyCooldowns
import cat.daisy.command.text.DaisyText.mm
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * SubCommand with independent permission and execution.
 * Supports infinite nesting of subcommands.
 */
class SubCommand(
    val name: String,
    val description: String = "",
    val permission: String? = null,
    val playerOnly: Boolean = false,
    val cooldown: Int = 0,
    val cooldownMessage: String? = null,
    val cooldownBypassPermission: String? = null,
    val aliases: List<String> = emptyList(),
    val arguments: List<ArgumentDef> = emptyList(),
    nestedSubcommands: List<SubCommandData> = emptyList(),
    private val executor: CommandContext.() -> Unit,
    private val tabProvider: (TabContext.() -> List<String>)? = null,
) {
    private val subcommands = ConcurrentHashMap<String, SubCommand>()

    init {
        nestedSubcommands.forEach { data ->
            val sub =
                SubCommand(
                    data.name,
                    data.description,
                    data.permission,
                    data.playerOnly,
                    data.cooldown,
                    data.cooldownMessage,
                    data.cooldownBypassPermission,
                    data.aliases,
                    data.arguments,
                    data.subcommands,
                    data.executor,
                    data.tabProvider,
                )
            subcommands[data.name.lowercase()] = sub
            data.aliases.forEach { alias -> subcommands[alias.lowercase()] = sub }
        }
    }

    fun execute(
        sender: CommandSender,
        args: List<String>,
        label: String = "",
    ): Boolean {
        if (playerOnly && sender !is Player) {
            sender.sendMessage("<#e74c3c>✖</> <gray>This command can only be used by players!".mm())
            return true
        }
        if (!hasPermission(sender)) {
            sender.sendMessage("<#e74c3c>✖</> <gray>You don't have permission to use this command!".mm())
            return true
        }
        if (cooldown > 0 && sender is Player) {
            val remaining = DaisyCooldowns.getRemainingCooldown(sender, name, cooldown)
            if (remaining > 0 && (cooldownBypassPermission == null || !sender.hasPermission(cooldownBypassPermission))) {
                val msg = cooldownMessage ?: "<#e74c3c>✖</> <gray>Please wait <white>$remaining</white> seconds."
                sender.sendMessage(msg.mm())
                return true
            }
        }
        // Check for nested subcommands
        if (args.isNotEmpty() && subcommands.isNotEmpty()) {
            val subName = args[0].lowercase()
            subcommands[subName]?.let { sub ->
                return sub.execute(sender, args.drop(1), label)
            }
        }
        val namedArgs = parseArguments(args, arguments)
        CommandContext(sender, args, namedArgs, label).executor()
        return true
    }

    fun tabComplete(
        sender: CommandSender,
        args: List<String>,
    ): List<String> {
        if (!hasPermission(sender)) return emptyList()
        if (subcommands.isNotEmpty()) {
            if (args.size == 1) {
                val prefix = args[0].lowercase()
                val subSuggestions =
                    subcommands.entries
                        .asSequence()
                        .filter { (name, sub) -> name.startsWith(prefix) && sub.hasPermission(sender) }
                        .map { it.key }
                        .distinct()
                        .toList()
                if (subSuggestions.isNotEmpty()) return subSuggestions
            } else if (args.size > 1) {
                val subName = args[0].lowercase()
                subcommands[subName]?.let { sub ->
                    return sub.tabComplete(sender, args.drop(1))
                }
            }
        }
        val argCompletions = getArgumentCompletions(args.size - 1, args.lastOrNull() ?: "", arguments, sender)
        return tabProvider?.let { TabContext(sender, args).it() + argCompletions } ?: argCompletions
    }

    fun hasPermission(sender: CommandSender): Boolean = permission == null || sender.hasPermission(permission)
}

/**
 * Data class for subcommand construction.
 */
data class SubCommandData(
    val name: String,
    val description: String,
    val permission: String?,
    val playerOnly: Boolean,
    val cooldown: Int,
    val cooldownMessage: String?,
    val cooldownBypassPermission: String?,
    val aliases: List<String>,
    val arguments: List<ArgumentDef>,
    val subcommands: List<SubCommandData>,
    val executor: CommandContext.() -> Unit,
    val tabProvider: (TabContext.() -> List<String>)?,
)
// ═══════════════════════════════════════════════════════════════════════════════
// ARGUMENT PARSING UTILITIES
// ═══════════════════════════════════════════════════════════════════════════════

/** Maximum allowed input length for security */
private const val MAX_ARG_LENGTH = 256
private const val MAX_GREEDY_LENGTH = 1024

/**
 * Parse command arguments into named values with input validation.
 */
internal fun parseArguments(
    args: List<String>,
    definitions: List<ArgumentDef>,
): Map<String, Any> {
    val result = mutableMapOf<String, Any>()
    var argIndex = 0
    for (def in definitions) {
        when (def) {
            is ArgumentDef.StringArg -> {
                args.getOrNull(argIndex)?.let { value ->
                    if (value.length <= MAX_ARG_LENGTH) {
                        result[def.name] = value
                    }
                }
                argIndex++
            }

            is ArgumentDef.GreedyStringArg -> {
                if (argIndex < args.size) {
                    val joined = args.drop(argIndex).joinToString(" ")
                    if (joined.length <= MAX_GREEDY_LENGTH) {
                        result[def.name] = joined
                    }
                }
                break
            }

            is ArgumentDef.IntArg -> {
                args.getOrNull(argIndex)?.toIntOrNull()?.let { value ->
                    if (value in def.min..def.max) result[def.name] = value
                }
                argIndex++
            }

            is ArgumentDef.LongArg -> {
                args.getOrNull(argIndex)?.toLongOrNull()?.let { value ->
                    if (value in def.min..def.max) result[def.name] = value
                }
                argIndex++
            }

            is ArgumentDef.DoubleArg -> {
                args.getOrNull(argIndex)?.toDoubleOrNull()?.let { value ->
                    if (value in def.min..def.max) result[def.name] = value
                }
                argIndex++
            }

            is ArgumentDef.FloatArg -> {
                args.getOrNull(argIndex)?.toFloatOrNull()?.let { value ->
                    if (value in def.min..def.max) result[def.name] = value
                }
                argIndex++
            }

            is ArgumentDef.BooleanArg -> {
                args.getOrNull(argIndex)?.lowercase()?.let { value ->
                    when (value) {
                        "true", "yes", "on", "1" -> result[def.name] = true
                        "false", "no", "off", "0" -> result[def.name] = false
                    }
                }
                argIndex++
            }

            is ArgumentDef.PlayerArg -> {
                args.getOrNull(argIndex)?.let { name ->
                    Bukkit.getPlayer(name)?.let { result[def.name] = it }
                }
                argIndex++
            }

            is ArgumentDef.OfflinePlayerArg -> {
                args.getOrNull(argIndex)?.let { name ->
                    @Suppress("DEPRECATION")
                    val player = Bukkit.getOfflinePlayer(name)
                    if (player.hasPlayedBefore() || player.isOnline) {
                        result[def.name] = player
                    }
                }
                argIndex++
            }

            is ArgumentDef.WorldArg -> {
                args.getOrNull(argIndex)?.let { name ->
                    Bukkit.getWorld(name)?.let { result[def.name] = it }
                }
                argIndex++
            }

            is ArgumentDef.MaterialArg -> {
                args.getOrNull(argIndex)?.let { name ->
                    Material.matchMaterial(name)?.let { result[def.name] = it }
                }
                argIndex++
            }

            is ArgumentDef.GameModeArg -> {
                args.getOrNull(argIndex)?.let { name ->
                    GameMode.entries.find { it.name.equals(name, ignoreCase = true) }?.let {
                        result[def.name] = it
                    }
                }
                argIndex++
            }

            is ArgumentDef.EntityTypeArg -> {
                args.getOrNull(argIndex)?.let { name ->
                    org.bukkit.entity.EntityType.entries.find { it.name.equals(name, ignoreCase = true) }?.let {
                        result[def.name] = it
                    }
                }
                argIndex++
            }

            is ArgumentDef.UUIDArg -> {
                args.getOrNull(argIndex)?.let { value ->
                    try {
                        result[def.name] = java.util.UUID.fromString(value)
                    } catch (_: Exception) {
                    }
                }
                argIndex++
            }

            is ArgumentDef.DurationArg -> {
                args.getOrNull(argIndex)?.let { value ->
                    parseDuration(value)?.let { result[def.name] = it }
                }
                argIndex++
            }

            is ArgumentDef.ChoiceArg -> {
                args.getOrNull(argIndex)?.let { value ->
                    if (def.choices.any { it.equals(value, ignoreCase = true) }) {
                        result[def.name] = value
                    }
                }
                argIndex++
            }

            is ArgumentDef.EnumArg<*> -> {
                args.getOrNull(argIndex)?.let { value ->
                    def.enumClass.enumConstants?.find { it.name.equals(value, ignoreCase = true) }?.let {
                        result[def.name] = it
                    }
                }
                argIndex++
            }

            is ArgumentDef.CustomArg<*> -> {
                args.getOrNull(argIndex)?.let { value ->
                    def.parser
                        .parse(value)
                        .getOrNull()
                        ?.let { result[def.name] = it }
                }
                argIndex++
            }
        }
    }
    return result
}

private fun parseDuration(input: String): java.time.Duration? {
    val pattern = Regex("""(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""")
    val match = pattern.matchEntire(input.lowercase()) ?: return null
    val days = match.groupValues[1].toLongOrNull() ?: 0
    val hours = match.groupValues[2].toLongOrNull() ?: 0
    val minutes = match.groupValues[3].toLongOrNull() ?: 0
    val seconds = match.groupValues[4].toLongOrNull() ?: 0
    if (days == 0L && hours == 0L && minutes == 0L && seconds == 0L) return null
    return java.time.Duration
        .ofDays(days)
        .plusHours(hours)
        .plusMinutes(minutes)
        .plusSeconds(seconds)
}

/**
 * Get tab completions for argument position.
 */
@Suppress("UNUSED_PARAMETER")
internal fun getArgumentCompletions(
    index: Int,
    current: String,
    definitions: List<ArgumentDef>,
    sender: CommandSender,
): List<String> {
    val def = definitions.getOrNull(index) ?: return emptyList()
    return when (def) {
        is ArgumentDef.PlayerArg -> {
            Bukkit
                .getOnlinePlayers()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith(current, ignoreCase = true) }
                .toList()
        }

        is ArgumentDef.OfflinePlayerArg -> {
            Bukkit
                .getOnlinePlayers()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith(current, ignoreCase = true) }
                .toList()
        }

        is ArgumentDef.WorldArg -> {
            Bukkit
                .getWorlds()
                .asSequence()
                .map { it.name }
                .filter { it.startsWith(current, ignoreCase = true) }
                .toList()
        }

        is ArgumentDef.MaterialArg -> {
            val upper = current.uppercase()
            Material.entries
                .asSequence()
                .filter { it.name.startsWith(upper) }
                .take(30)
                .map { it.name.lowercase() }
                .toList()
        }

        is ArgumentDef.GameModeArg -> {
            GameMode.entries
                .map { it.name.lowercase() }
                .filter { it.startsWith(current.lowercase()) }
        }

        is ArgumentDef.EntityTypeArg -> {
            org.bukkit.entity.EntityType.entries
                .filter { it.isSpawnable }
                .map { it.name.lowercase() }
                .filter { it.startsWith(current.lowercase()) }
                .take(30)
        }

        is ArgumentDef.BooleanArg -> {
            listOf("true", "false").filter { it.startsWith(current, ignoreCase = true) }
        }

        is ArgumentDef.IntArg, is ArgumentDef.LongArg -> {
            if (current.isEmpty()) listOf("1", "5", "10", "50", "100") else emptyList()
        }

        is ArgumentDef.ChoiceArg -> {
            def.choices.filter { it.startsWith(current, ignoreCase = true) }
        }

        is ArgumentDef.EnumArg<*> -> {
            def.enumClass.enumConstants
                ?.map { it.name.lowercase() }
                ?.filter { it.startsWith(current.lowercase()) }
                ?: emptyList()
        }

        is ArgumentDef.DurationArg -> {
            if (current.isEmpty()) listOf("1h", "30m", "1d", "12h", "7d") else emptyList()
        }

        is ArgumentDef.CustomArg<*> -> {
            def.parser.complete(current)
        }

        else -> {
            emptyList()
        }
    }
}
