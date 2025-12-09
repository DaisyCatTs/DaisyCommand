package cat.daisy.command.context
import cat.daisy.command.arguments.ArgParser
import cat.daisy.command.arguments.ParseResult
import cat.daisy.command.cooldown.DaisyCooldowns
import cat.daisy.command.text.DaisyText.mm
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Duration

// DaisyCommand Context Classes
//
// Rich execution contexts with:
// - MiniMessage messaging
// - Cooldown utilities
// - Player-specific features (sounds, titles, action bars)
// - Type-safe argument access

// ═══════════════════════════════════════════════════════════════════════════════
// COMMAND CONTEXT
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Main execution context for commands.
 * Provides access to sender, arguments, and utility methods.
 */
class CommandContext(
    val sender: CommandSender,
    val args: List<String>,
    val namedArgs: Map<String, Any> = emptyMap(),
    val label: String = "",
) {
    val player: Player? get() = sender as? Player
    val isPlayer: Boolean get() = sender is Player
    val isConsole: Boolean get() = !isPlayer

    // ─────────────────────────────────────────────────────────────────────────
    // NAMED ARGUMENT ACCESS
    // ─────────────────────────────────────────────────────────────────────────
    operator fun get(key: String): Any? = namedArgs[key]

    @Suppress("UNCHECKED_CAST")
    fun <T> getArg(key: String): T? = namedArgs[key] as? T

    fun getString(key: String): String? = namedArgs[key] as? String

    fun getInt(key: String): Int? = namedArgs[key] as? Int

    fun getLong(key: String): Long? = namedArgs[key] as? Long

    fun getDouble(key: String): Double? = namedArgs[key] as? Double

    fun getFloat(key: String): Float? = namedArgs[key] as? Float

    fun getBoolean(key: String): Boolean? = namedArgs[key] as? Boolean

    fun getPlayer(key: String): Player? = namedArgs[key] as? Player

    // ─────────────────────────────────────────────────────────────────────────
    // POSITIONAL ARGUMENT ACCESS
    // ─────────────────────────────────────────────────────────────────────────
    fun arg(index: Int): String? = args.getOrNull(index)

    fun argOr(
        index: Int,
        default: String,
    ): String = args.getOrElse(index) { default }

    fun argInt(index: Int): Int? = arg(index)?.toIntOrNull()

    fun argLong(index: Int): Long? = arg(index)?.toLongOrNull()

    fun argDouble(index: Int): Double? = arg(index)?.toDoubleOrNull()

    fun argFloat(index: Int): Float? = arg(index)?.toFloatOrNull()

    fun argBoolean(index: Int): Boolean? =
        arg(index)?.lowercase()?.let {
            when (it) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> null
            }
        }

    fun argPlayer(index: Int): Player? = arg(index)?.let { Bukkit.getPlayer(it) }

    fun joinArgs(from: Int = 0): String = args.drop(from).joinToString(" ")

    val argCount: Int get() = args.size

    // ─────────────────────────────────────────────────────────────────────────
    // COOLDOWN UTILITIES
    // ─────────────────────────────────────────────────────────────────────────
    fun isOnCooldown(
        command: String,
        seconds: Int,
        bypassPermission: String? = null,
    ): Boolean {
        val p = player ?: return false
        return DaisyCooldowns.isOnCooldown(p, command, seconds, bypassPermission)
    }

    fun getCooldown(
        command: String,
        seconds: Int,
    ): Long {
        val p = player ?: return 0
        return DaisyCooldowns.getRemainingCooldown(p, command, seconds)
    }

    fun checkCooldown(
        command: String,
        seconds: Int,
    ): Long {
        val p = player ?: return 0
        return DaisyCooldowns.checkCooldown(p, command, seconds)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MINIMESSAGE RESPONSES
    // ─────────────────────────────────────────────────────────────────────────
    fun send(message: String) = sender.sendMessage(message.mm())

    fun send(component: Component) = sender.sendMessage(component)

    fun reply(message: String) = send("<gray>» </gray>$message")

    fun success(message: String) = send("<#2ecc71>✔</> <gray>$message")

    fun error(message: String) = send("<#e74c3c>✖</> <gray>$message")

    fun warn(message: String) = send("<#f1c40f>⚠</> <gray>$message")

    fun info(message: String) = send("<#3498db>✦</> <gray>$message")

    fun broadcast(message: String) = Bukkit.broadcast("<#9b59b6>»</> $message".mm())

    // ─────────────────────────────────────────────────────────────────────────
    // PLAYER UTILITIES
    // ─────────────────────────────────────────────────────────────────────────
    fun asPlayer(): Player? =
        player ?: run {
            error("This command requires a player!")
            null
        }

    inline fun requirePlayer(block: PlayerContext.() -> Unit) {
        asPlayer()?.let { PlayerContext(it, args, namedArgs, label).block() }
    }

    inline fun requirePermission(
        permission: String,
        block: () -> Unit,
    ) {
        if (sender.hasPermission(permission)) {
            block()
        } else {
            error("You don't have permission to do this!")
        }
    }

    inline fun requireArgs(
        count: Int,
        usageMsg: String = "Not enough arguments!",
        block: () -> Unit,
    ) {
        if (args.size >= count) {
            block()
        } else {
            error(usageMsg)
        }
    }

    inline fun <T> withArg(
        index: Int,
        parser: (String) -> T?,
        errorMsg: String = "Invalid argument!",
        block: (T) -> Unit,
    ) {
        val raw = arg(index)
        if (raw == null) {
            error("Missing argument at position ${index + 1}!")
            return
        }
        val parsed = parser(raw)
        if (parsed == null) {
            error(errorMsg)
            return
        }
        block(parsed)
    }

    inline fun withPlayer(
        index: Int,
        block: (Player) -> Unit,
    ) = withArg(index, Bukkit::getPlayer, "Player not found!", block)

    inline fun withInt(
        index: Int,
        block: (Int) -> Unit,
    ) = withArg(index, String::toIntOrNull, "Invalid number!", block)

    inline fun withDouble(
        index: Int,
        block: (Double) -> Unit,
    ) = withArg(index, String::toDoubleOrNull, "Invalid number!", block)

    // ─────────────────────────────────────────────────────────────────────────
    // PARSER INTEGRATION
    // ─────────────────────────────────────────────────────────────────────────
    fun <T> parse(
        index: Int,
        parser: ArgParser<T>,
    ): ParseResult<T> {
        val input = arg(index) ?: return ParseResult.Failure("Missing argument at position ${index + 1}")
        return parser.parse(input)
    }

    inline fun <T> withParsed(
        index: Int,
        parser: ArgParser<T>,
        block: (T) -> Unit,
    ) {
        parse(index, parser).onSuccess(block).onFailure { error(it) }
    }
}
// ═══════════════════════════════════════════════════════════════════════════════
// PLAYER CONTEXT
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Extended context for player-only commands.
 * Includes sounds, titles, action bars, and more.
 */
class PlayerContext(
    val player: Player,
    val args: List<String>,
    val namedArgs: Map<String, Any> = emptyMap(),
    val label: String = "",
) {
    val sender: CommandSender get() = player

    // ─────────────────────────────────────────────────────────────────────────
    // NAMED ARGUMENT ACCESS
    // ─────────────────────────────────────────────────────────────────────────
    operator fun get(key: String): Any? = namedArgs[key]

    @Suppress("UNCHECKED_CAST")
    fun <T> getArg(key: String): T? = namedArgs[key] as? T

    fun getString(key: String): String? = namedArgs[key] as? String

    fun getInt(key: String): Int? = namedArgs[key] as? Int

    fun getLong(key: String): Long? = namedArgs[key] as? Long

    fun getDouble(key: String): Double? = namedArgs[key] as? Double

    fun getBoolean(key: String): Boolean? = namedArgs[key] as? Boolean

    fun getPlayer(key: String): Player? = namedArgs[key] as? Player

    // ─────────────────────────────────────────────────────────────────────────
    // POSITIONAL ARGUMENT ACCESS
    // ─────────────────────────────────────────────────────────────────────────
    fun arg(index: Int): String? = args.getOrNull(index)

    fun argOr(
        index: Int,
        default: String,
    ): String = args.getOrElse(index) { default }

    fun argInt(index: Int): Int? = arg(index)?.toIntOrNull()

    fun argLong(index: Int): Long? = arg(index)?.toLongOrNull()

    fun argDouble(index: Int): Double? = arg(index)?.toDoubleOrNull()

    fun argPlayer(index: Int): Player? = arg(index)?.let { Bukkit.getPlayer(it) }

    fun joinArgs(from: Int = 0): String = args.drop(from).joinToString(" ")

    val argCount: Int get() = args.size

    // ─────────────────────────────────────────────────────────────────────────
    // COOLDOWN UTILITIES
    // ─────────────────────────────────────────────────────────────────────────
    fun isOnCooldown(
        command: String,
        seconds: Int,
        bypassPermission: String? = null,
    ): Boolean = DaisyCooldowns.isOnCooldown(player, command, seconds, bypassPermission)

    fun getCooldown(
        command: String,
        seconds: Int,
    ): Long = DaisyCooldowns.getRemainingCooldown(player, command, seconds)

    // ─────────────────────────────────────────────────────────────────────────
    // MINIMESSAGE RESPONSES
    // ─────────────────────────────────────────────────────────────────────────
    fun send(message: String) = player.sendMessage(message.mm())

    fun send(component: Component) = player.sendMessage(component)

    fun reply(message: String) = send("<gray>» </gray>$message")

    fun success(message: String) = send("<#2ecc71>✔</> <gray>$message")

    fun error(message: String) = send("<#e74c3c>✖</> <gray>$message")

    fun warn(message: String) = send("<#f1c40f>⚠</> <gray>$message")

    fun info(message: String) = send("<#3498db>✦</> <gray>$message")

    // ─────────────────────────────────────────────────────────────────────────
    // PLAYER-EXCLUSIVE FEATURES
    // ─────────────────────────────────────────────────────────────────────────
    fun actionBar(message: String) = player.sendActionBar(message.mm())

    fun title(
        title: String = "",
        subtitle: String = "",
        fadeIn: Duration = Duration.ofMillis(500),
        stay: Duration = Duration.ofSeconds(3),
        fadeOut: Duration = Duration.ofMillis(500),
    ) {
        player.showTitle(
            Title.title(
                title.mm(),
                subtitle.mm(),
                Title.Times.times(fadeIn, stay, fadeOut),
            ),
        )
    }

    fun sound(
        sound: Sound,
        volume: Float = 1f,
        pitch: Float = 1f,
    ) {
        player.playSound(player.location, sound, volume, pitch)
    }

    fun successWithSound(
        message: String,
        sound: Sound = Sound.ENTITY_PLAYER_LEVELUP,
    ) {
        success(message)
        sound(sound, 0.5f, 1.5f)
    }

    fun errorWithSound(
        message: String,
        sound: Sound = Sound.ENTITY_VILLAGER_NO,
    ) {
        error(message)
        sound(sound, 0.5f, 1f)
    }

    fun infoWithSound(
        message: String,
        sound: Sound = Sound.BLOCK_NOTE_BLOCK_PLING,
    ) {
        info(message)
        sound(sound, 0.5f, 1.2f)
    }

    inline fun requirePermission(
        permission: String,
        block: () -> Unit,
    ) {
        if (player.hasPermission(permission)) {
            block()
        } else {
            error("You don't have permission to do this!")
        }
    }

    inline fun requireArgs(
        count: Int,
        usageMsg: String = "Not enough arguments!",
        block: () -> Unit,
    ) {
        if (args.size >= count) {
            block()
        } else {
            error(usageMsg)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PARSER INTEGRATION
    // ─────────────────────────────────────────────────────────────────────────
    fun <T> parse(
        index: Int,
        parser: ArgParser<T>,
    ): ParseResult<T> {
        val input = arg(index) ?: return ParseResult.Failure("Missing argument at position ${index + 1}")
        return parser.parse(input)
    }

    inline fun <T> withParsed(
        index: Int,
        parser: ArgParser<T>,
        block: (T) -> Unit,
    ) {
        parse(index, parser).onSuccess(block).onFailure { error(it) }
    }
}
// ═══════════════════════════════════════════════════════════════════════════════
// TAB CONTEXT
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Context for tab completion.
 */
class TabContext(
    val sender: CommandSender,
    val args: List<String>,
) {
    val player: Player? get() = sender as? Player
    val isPlayer: Boolean get() = sender is Player
    val currentArg: String get() = args.lastOrNull() ?: ""
    val argIndex: Int get() = (args.size - 1).coerceAtLeast(0)

    fun filter(vararg options: String): List<String> = options.filter { it.startsWith(currentArg, ignoreCase = true) }

    fun filter(options: Collection<String>): List<String> = options.filter { it.startsWith(currentArg, ignoreCase = true) }

    fun players(): List<String> =
        Bukkit
            .getOnlinePlayers()
            .asSequence()
            .map { it.name }
            .filter { it.startsWith(currentArg, ignoreCase = true) }
            .toList()

    fun worlds(): List<String> =
        Bukkit
            .getWorlds()
            .asSequence()
            .map { it.name }
            .filter { it.startsWith(currentArg, ignoreCase = true) }
            .toList()

    fun none(): List<String> = emptyList()

    fun combine(vararg sources: () -> List<String>): List<String> = sources.flatMap { it() }.distinct()

    inline fun whenArg(
        targetIndex: Int,
        block: () -> List<String>,
    ): List<String> = if (argIndex == targetIndex) block() else emptyList()

    inline fun byIndex(block: TabIndexBuilder.() -> Unit): List<String> = TabIndexBuilder(this).apply(block).resolve()

    fun <T> complete(
        index: Int,
        parser: ArgParser<T>,
    ): List<String> = if (argIndex == index) parser.complete(currentArg) else emptyList()
}

class TabIndexBuilder(
    private val ctx: TabContext,
) {
    private val handlers = mutableMapOf<Int, () -> List<String>>()
    private var defaultHandler: (() -> List<String>)? = null

    fun at(
        index: Int,
        block: TabContext.() -> List<String>,
    ) {
        handlers[index] = { ctx.block() }
    }

    fun default(block: TabContext.() -> List<String>) {
        defaultHandler = { ctx.block() }
    }

    fun resolve(): List<String> = handlers[ctx.argIndex]?.invoke() ?: defaultHandler?.invoke() ?: emptyList()
}
