@file:Suppress("unused")

package cat.daisy.command.arguments

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID

private const val MAX_INPUT_LENGTH = 256
private const val MAX_TEXT_LENGTH = 1024

sealed class ParseResult<out T> {
    data class Success<T>(
        val value: T,
    ) : ParseResult<T>()

    data class Failure(
        val error: String,
    ) : ParseResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ParseResult<R> =
        when (this) {
            is Success -> Success(transform(value))
            is Failure -> this
        }

    inline fun <R> flatMap(transform: (T) -> ParseResult<R>): ParseResult<R> =
        when (this) {
            is Success -> transform(value)
            is Failure -> this
        }

    inline fun onSuccess(block: (T) -> Unit): ParseResult<T> {
        if (this is Success) {
            block(value)
        }
        return this
    }

    inline fun onFailure(block: (String) -> Unit): ParseResult<T> {
        if (this is Failure) {
            block(error)
        }
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.value

    fun getOrThrow(): T =
        when (this) {
            is Success -> value
            is Failure -> throw IllegalStateException(error)
        }

    companion object {
        fun <T> success(value: T): ParseResult<T> = Success(value)

        fun failure(message: String): ParseResult<Nothing> = Failure(message)
    }
}

interface DaisyPlatform {
    fun findPlayer(name: String): Player?

    fun onlinePlayers(): Collection<Player>

    fun findOfflinePlayer(name: String): OfflinePlayer?

    fun findWorld(name: String): World?

    fun worlds(): Collection<World>
}

object BukkitPlatform : DaisyPlatform {
    override fun findPlayer(name: String): Player? = Bukkit.getPlayer(name)

    override fun onlinePlayers(): Collection<Player> = Bukkit.getOnlinePlayers()

    override fun findOfflinePlayer(name: String): OfflinePlayer? {
        @Suppress("DEPRECATION")
        val player = Bukkit.getOfflinePlayer(name)
        return player.takeIf { it.hasPlayedBefore() || it.isOnline }
    }

    override fun findWorld(name: String): World? = Bukkit.getWorld(name)

    override fun worlds(): Collection<World> = Bukkit.getWorlds()
}

data class ParseContext(
    val sender: CommandSender,
    val platform: DaisyPlatform,
    val commandPath: List<String>,
    val argumentName: String,
)

data class SuggestContext(
    val sender: CommandSender,
    val platform: DaisyPlatform,
    val commandPath: List<String>,
    val argumentIndex: Int,
    val currentInput: String,
    val previousArguments: Map<String, Any?>,
)

interface DaisyParser<T> {
    val displayName: String
    val greedy: Boolean get() = false

    fun parse(
        input: String,
        context: ParseContext,
    ): ParseResult<T>

    fun suggest(context: SuggestContext): List<String> = emptyList()
}

class ArgumentRef<T> internal constructor(
    internal val definition: MutableArgumentDefinition,
    val name: String,
) {
    @Suppress("UNCHECKED_CAST")
    fun optional(): ArgumentRef<T?> {
        definition.optional = true
        return this as ArgumentRef<T?>
    }
}

@Suppress("UNCHECKED_CAST")
fun <T> ArgumentRef<T>.optional(): ArgumentRef<T?> = optional()

internal class MutableArgumentDefinition(
    val slot: Int,
    val name: String,
    val parser: DaisyParser<Any?>,
    var optional: Boolean = false,
)

internal data class CompiledArgument(
    val slot: Int,
    val name: String,
    val parser: DaisyParser<Any?>,
    val optional: Boolean,
)

object Parsers {
    val STRING: DaisyParser<String> =
        object : DaisyParser<String> {
            override val displayName: String = "string"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<String> {
                if (input.length > MAX_INPUT_LENGTH) {
                    return ParseResult.failure("Input is too long. Max $MAX_INPUT_LENGTH characters.")
                }
                return ParseResult.success(input)
            }
        }

    val TEXT: DaisyParser<String> =
        object : DaisyParser<String> {
            override val displayName: String = "text"
            override val greedy: Boolean = true

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<String> {
                if (input.length > MAX_TEXT_LENGTH) {
                    return ParseResult.failure("Input is too long. Max $MAX_TEXT_LENGTH characters.")
                }
                return ParseResult.success(input)
            }
        }

    fun int(
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
    ): DaisyParser<Int> =
        object : DaisyParser<Int> {
            override val displayName: String = "int"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<Int> {
                val value = input.toIntOrNull() ?: return ParseResult.failure("'$input' is not a valid integer.")
                return if (value in min..max) {
                    ParseResult.success(value)
                } else {
                    ParseResult.failure("Value must be between $min and $max.")
                }
            }

            override fun suggest(context: SuggestContext): List<String> =
                if (context.currentInput.isEmpty()) {
                    listOf("1", "5", "10", "50", "100")
                } else {
                    emptyList()
                }
        }

    fun long(
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
    ): DaisyParser<Long> =
        object : DaisyParser<Long> {
            override val displayName: String = "long"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<Long> {
                val value = input.toLongOrNull() ?: return ParseResult.failure("'$input' is not a valid long.")
                return if (value in min..max) {
                    ParseResult.success(value)
                } else {
                    ParseResult.failure("Value must be between $min and $max.")
                }
            }
        }

    fun double(
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
    ): DaisyParser<Double> =
        object : DaisyParser<Double> {
            override val displayName: String = "double"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<Double> {
                val value = input.toDoubleOrNull() ?: return ParseResult.failure("'$input' is not a valid number.")
                return if (value in min..max) {
                    ParseResult.success(value)
                } else {
                    ParseResult.failure("Value must be between $min and $max.")
                }
            }
        }

    fun float(
        min: Float = Float.NEGATIVE_INFINITY,
        max: Float = Float.POSITIVE_INFINITY,
    ): DaisyParser<Float> =
        object : DaisyParser<Float> {
            override val displayName: String = "float"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<Float> {
                val value = input.toFloatOrNull() ?: return ParseResult.failure("'$input' is not a valid number.")
                return if (value in min..max) {
                    ParseResult.success(value)
                } else {
                    ParseResult.failure("Value must be between $min and $max.")
                }
            }
        }

    val BOOLEAN: DaisyParser<Boolean> =
        object : DaisyParser<Boolean> {
            override val displayName: String = "boolean"

            private val trueValues = setOf("true", "yes", "on", "1", "enable", "y")
            private val falseValues = setOf("false", "no", "off", "0", "disable", "n")

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<Boolean> {
                val normalized = input.lowercase()
                return when {
                    normalized in trueValues -> ParseResult.success(true)
                    normalized in falseValues -> ParseResult.success(false)
                    else -> ParseResult.failure("'$input' is not a valid boolean.")
                }
            }

            override fun suggest(context: SuggestContext): List<String> = filterByInput(listOf("true", "false"), context.currentInput)
        }

    val PLAYER: DaisyParser<Player> =
        object : DaisyParser<Player> {
            override val displayName: String = "player"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<Player> =
                context.platform.findPlayer(input)?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Player '$input' is not online.")

            override fun suggest(context: SuggestContext): List<String> =
                context.platform
                    .onlinePlayers()
                    .map(Player::getName)
                    .let { filterByInput(it, context.currentInput) }
        }

    val OFFLINE_PLAYER: DaisyParser<OfflinePlayer> =
        object : DaisyParser<OfflinePlayer> {
            override val displayName: String = "offline-player"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<OfflinePlayer> =
                context.platform.findOfflinePlayer(input)?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Player '$input' was not found.")

            override fun suggest(context: SuggestContext): List<String> = PLAYER.suggest(context)
        }

    val WORLD: DaisyParser<World> =
        object : DaisyParser<World> {
            override val displayName: String = "world"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<World> =
                context.platform.findWorld(input)?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("World '$input' was not found.")

            override fun suggest(context: SuggestContext): List<String> =
                context.platform
                    .worlds()
                    .map(World::getName)
                    .let { filterByInput(it, context.currentInput) }
        }

    val MATERIAL: DaisyParser<Material> =
        object : DaisyParser<Material> {
            override val displayName: String = "material"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<Material> =
                Material.matchMaterial(input)?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Material '$input' was not found.")

            override fun suggest(context: SuggestContext): List<String> =
                Material.entries
                    .map { it.name.lowercase() }
                    .let { filterByInput(it, context.currentInput) }
                    .take(30)
        }

    val GAME_MODE: DaisyParser<GameMode> =
        object : DaisyParser<GameMode> {
            override val displayName: String = "gamemode"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<GameMode> =
                GameMode.entries.find { it.name.equals(input, ignoreCase = true) }?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Game mode '$input' is invalid.")

            override fun suggest(context: SuggestContext): List<String> =
                GameMode.entries.map { it.name.lowercase() }.let { filterByInput(it, context.currentInput) }
        }

    val ENTITY_TYPE: DaisyParser<EntityType> =
        object : DaisyParser<EntityType> {
            override val displayName: String = "entity-type"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<EntityType> =
                EntityType.entries.find { it.name.equals(input, ignoreCase = true) }?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Entity type '$input' is invalid.")

            override fun suggest(context: SuggestContext): List<String> =
                EntityType.entries
                    .filter { it.isSpawnable }
                    .map { it.name.lowercase() }
                    .let { filterByInput(it, context.currentInput) }
                    .take(30)
        }

    val UUID_PARSER: DaisyParser<UUID> =
        object : DaisyParser<UUID> {
            override val displayName: String = "uuid"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<UUID> =
                try {
                    ParseResult.success(UUID.fromString(input))
                } catch (_: IllegalArgumentException) {
                    ParseResult.failure("'$input' is not a valid UUID.")
                }
        }

    val DURATION: DaisyParser<Duration> =
        object : DaisyParser<Duration> {
            override val displayName: String = "duration"
            private val pattern = Regex("""(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""")

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<Duration> {
                val match =
                    pattern.matchEntire(input.lowercase())
                        ?: return ParseResult.failure("Invalid duration. Use values like 30m, 2h, or 1d2h.")
                val days = match.groupValues[1].toLongOrNull() ?: 0L
                val hours = match.groupValues[2].toLongOrNull() ?: 0L
                val minutes = match.groupValues[3].toLongOrNull() ?: 0L
                val seconds = match.groupValues[4].toLongOrNull() ?: 0L

                if (days == 0L && hours == 0L && minutes == 0L && seconds == 0L) {
                    return ParseResult.failure("Duration must be greater than zero.")
                }

                return ParseResult.success(
                    Duration
                        .ofDays(days)
                        .plusHours(hours)
                        .plusMinutes(minutes)
                        .plusSeconds(seconds),
                )
            }

            override fun suggest(context: SuggestContext): List<String> =
                filterByInput(listOf("30m", "1h", "12h", "1d", "7d"), context.currentInput)
        }

    fun choice(vararg options: String): DaisyParser<String> {
        val normalizedToOriginal = LinkedHashMap<String, String>()
        for (option in options) {
            normalizedToOriginal[option.lowercase()] = option
        }

        return object : DaisyParser<String> {
            override val displayName: String = "choice"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<String> =
                normalizedToOriginal[input.lowercase()]?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Expected one of: ${options.joinToString(", ")}.")

            override fun suggest(context: SuggestContext): List<String> = filterByInput(options.toList(), context.currentInput)
        }
    }

    inline fun <reified E : Enum<E>> enum(): DaisyParser<E> {
        val values = enumValues<E>()
        val valuesByName = values.associateBy { it.name.lowercase() }

        return object : DaisyParser<E> {
            override val displayName: String = "enum"

            override fun parse(
                input: String,
                context: ParseContext,
            ): ParseResult<E> =
                valuesByName[input.lowercase()]?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Expected one of: ${values.joinToString(", ") { it.name.lowercase() }}.")

            override fun suggest(context: SuggestContext): List<String> =
                filterByInput(values.map { it.name.lowercase() }, context.currentInput)
        }
    }
}

@PublishedApi
internal fun filterByInput(
    values: Collection<String>,
    currentInput: String,
): List<String> = values.filter { it.startsWith(currentInput, ignoreCase = true) }
