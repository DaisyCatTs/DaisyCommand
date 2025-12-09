package cat.daisy.command.arguments
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import java.time.Duration
import java.util.UUID

// DaisyCommand Argument System
//
// Type-safe argument parsing with:
// - Sealed result types for clean error handling
// - Built-in parsers for common Bukkit types
// - Tab completion support
// - Validation and range checking
// - Input sanitization for security

// ═══════════════════════════════════════════════════════════════════════════════
// CONSTANTS
// ═══════════════════════════════════════════════════════════════════════════════

/** Maximum allowed input length to prevent memory attacks */
private const val MAX_INPUT_LENGTH = 256

/** Maximum allowed greedy string length */
private const val MAX_GREEDY_LENGTH = 1024

// ═══════════════════════════════════════════════════════════════════════════════
// RESULT TYPES
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Sealed result type for argument parsing.
 * Enables functional-style error handling.
 */
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
        if (this is Success) block(value)
        return this
    }

    inline fun onFailure(block: (String) -> Unit): ParseResult<T> {
        if (this is Failure) block(error)
        return this
    }

    fun getOrNull(): T? = (this as? Success)?.value

    fun getOrElse(default: @UnsafeVariance T): T = getOrNull() ?: default

    fun getOrThrow(): T = getOrNull() ?: throw IllegalStateException((this as Failure).error)

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    companion object {
        fun <T> success(value: T): ParseResult<T> = Success(value)

        fun failure(error: String): ParseResult<Nothing> = Failure(error)
    }
}
// ═══════════════════════════════════════════════════════════════════════════════
// ARGUMENT DEFINITION
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Represents a command argument definition.
 */
sealed class ArgumentDef(
    val name: String,
    val optional: Boolean = false,
) {
    class StringArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class GreedyStringArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class IntArg(
        name: String,
        val min: Int = Int.MIN_VALUE,
        val max: Int = Int.MAX_VALUE,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class LongArg(
        name: String,
        val min: Long = Long.MIN_VALUE,
        val max: Long = Long.MAX_VALUE,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class DoubleArg(
        name: String,
        val min: Double = Double.MIN_VALUE,
        val max: Double = Double.MAX_VALUE,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class FloatArg(
        name: String,
        val min: Float = Float.MIN_VALUE,
        val max: Float = Float.MAX_VALUE,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class BooleanArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class PlayerArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class OfflinePlayerArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class WorldArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class MaterialArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class GameModeArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class EntityTypeArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class UUIDArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class DurationArg(
        name: String,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class ChoiceArg(
        name: String,
        val choices: List<String>,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class EnumArg<E : Enum<E>>(
        name: String,
        val enumClass: Class<E>,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)

    class CustomArg<T>(
        name: String,
        val parser: ArgParser<T>,
        optional: Boolean = false,
    ) : ArgumentDef(name, optional)
}
// ═══════════════════════════════════════════════════════════════════════════════
// ARGUMENT PARSER INTERFACE
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Type-safe argument parser with tab completion support.
 */
interface ArgParser<T> {
    fun parse(input: String): ParseResult<T>

    fun complete(input: String): List<String> = emptyList()
}
// ═══════════════════════════════════════════════════════════════════════════════
// BUILT-IN PARSERS
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Collection of optimized argument parsers.
 */
object Parsers {
    // ─────────────────────────────────────────────────────────────────────────
    // PRIMITIVES
    // ─────────────────────────────────────────────────────────────────────────
    val STRING =
        object : ArgParser<String> {
            override fun parse(input: String): ParseResult<String> {
                if (input.length > MAX_INPUT_LENGTH) {
                    return ParseResult.failure("Input too long (max $MAX_INPUT_LENGTH characters)")
                }
                return ParseResult.success(input)
            }
        }

    /** Greedy string parser that captures all remaining arguments */
    val GREEDY_STRING =
        object : ArgParser<String> {
            override fun parse(input: String): ParseResult<String> {
                if (input.length > MAX_GREEDY_LENGTH) {
                    return ParseResult.failure("Input too long (max $MAX_GREEDY_LENGTH characters)")
                }
                return ParseResult.success(input)
            }
        }

    fun int(
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
    ) = object : ArgParser<Int> {
        override fun parse(input: String): ParseResult<Int> {
            val value = input.toIntOrNull() ?: return ParseResult.failure("'$input' is not a valid number")
            return if (value in min..max) {
                ParseResult.success(value)
            } else {
                ParseResult.failure("Number must be between $min and $max")
            }
        }

        override fun complete(input: String) = if (input.isEmpty()) listOf("1", "5", "10", "50", "100") else emptyList()
    }

    fun long(
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
    ) = object : ArgParser<Long> {
        override fun parse(input: String): ParseResult<Long> {
            val value = input.toLongOrNull() ?: return ParseResult.failure("'$input' is not a valid number")
            return if (value in min..max) {
                ParseResult.success(value)
            } else {
                ParseResult.failure("Number must be between $min and $max")
            }
        }
    }

    fun double(
        min: Double = Double.MIN_VALUE,
        max: Double = Double.MAX_VALUE,
    ) = object : ArgParser<Double> {
        override fun parse(input: String): ParseResult<Double> {
            val value = input.toDoubleOrNull() ?: return ParseResult.failure("'$input' is not a valid decimal")
            return if (value in min..max) {
                ParseResult.success(value)
            } else {
                ParseResult.failure("Number must be between $min and $max")
            }
        }
    }

    fun float(
        min: Float = Float.MIN_VALUE,
        max: Float = Float.MAX_VALUE,
    ) = object : ArgParser<Float> {
        override fun parse(input: String): ParseResult<Float> {
            val value = input.toFloatOrNull() ?: return ParseResult.failure("'$input' is not a valid decimal")
            return if (value in min..max) {
                ParseResult.success(value)
            } else {
                ParseResult.failure("Number must be between $min and $max")
            }
        }
    }

    val BOOLEAN =
        object : ArgParser<Boolean> {
            private val trueValues = setOf("true", "yes", "on", "1", "enable", "y")
            private val falseValues = setOf("false", "no", "off", "0", "disable", "n")

            override fun parse(input: String): ParseResult<Boolean> {
                val lower = input.lowercase()
                return when {
                    lower in trueValues -> ParseResult.success(true)
                    lower in falseValues -> ParseResult.success(false)
                    else -> ParseResult.failure("'$input' is not a valid boolean (true/false)")
                }
            }

            override fun complete(input: String) = listOf("true", "false").filter { it.startsWith(input, ignoreCase = true) }
        }

    // ─────────────────────────────────────────────────────────────────────────
    // BUKKIT TYPES
    // ─────────────────────────────────────────────────────────────────────────
    val PLAYER =
        object : ArgParser<Player> {
            override fun parse(input: String): ParseResult<Player> =
                Bukkit.getPlayer(input)?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Player '$input' is not online")

            override fun complete(input: String) =
                Bukkit
                    .getOnlinePlayers()
                    .asSequence()
                    .map { it.name }
                    .filter { it.startsWith(input, ignoreCase = true) }
                    .toList()
        }
    val OFFLINE_PLAYER =
        object : ArgParser<OfflinePlayer> {
            override fun parse(input: String): ParseResult<OfflinePlayer> {
                @Suppress("DEPRECATION")
                val player = Bukkit.getOfflinePlayer(input)
                return if (player.hasPlayedBefore() || player.isOnline) {
                    ParseResult.success(player)
                } else {
                    ParseResult.failure("Player '$input' has never played here")
                }
            }

            override fun complete(input: String) = PLAYER.complete(input)
        }
    val WORLD =
        object : ArgParser<World> {
            override fun parse(input: String): ParseResult<World> =
                Bukkit.getWorld(input)?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("World '$input' does not exist")

            override fun complete(input: String) =
                Bukkit
                    .getWorlds()
                    .asSequence()
                    .map { it.name }
                    .filter { it.startsWith(input, ignoreCase = true) }
                    .toList()
        }
    val MATERIAL =
        object : ArgParser<Material> {
            override fun parse(input: String): ParseResult<Material> =
                Material.matchMaterial(input)?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Material '$input' does not exist")

            override fun complete(input: String): List<String> {
                val upper = input.uppercase()
                return Material.entries
                    .asSequence()
                    .filter { it.name.startsWith(upper) }
                    .take(30)
                    .map { it.name.lowercase() }
                    .toList()
            }
        }
    val GAMEMODE =
        object : ArgParser<GameMode> {
            override fun parse(input: String): ParseResult<GameMode> =
                GameMode.entries
                    .find { it.name.equals(input, ignoreCase = true) }
                    ?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Invalid gamemode '$input'")

            override fun complete(input: String) =
                GameMode.entries
                    .map { it.name.lowercase() }
                    .filter { it.startsWith(input.lowercase()) }
        }
    val ENTITY_TYPE =
        object : ArgParser<EntityType> {
            override fun parse(input: String): ParseResult<EntityType> =
                EntityType.entries
                    .find { it.name.equals(input, ignoreCase = true) }
                    ?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Invalid entity type '$input'")

            override fun complete(input: String) =
                EntityType.entries
                    .filter { it.isSpawnable }
                    .map { it.name.lowercase() }
                    .filter { it.startsWith(input.lowercase()) }
                    .take(30)
        }
    val UUID_PARSER =
        object : ArgParser<UUID> {
            override fun parse(input: String): ParseResult<UUID> =
                try {
                    ParseResult.success(UUID.fromString(input))
                } catch (_: IllegalArgumentException) {
                    ParseResult.failure("'$input' is not a valid UUID")
                }
        }

    /**
     * Duration parser - supports formats like: 1d, 2h, 30m, 45s, 1d2h30m
     */
    val DURATION =
        object : ArgParser<Duration> {
            private val pattern = Regex("""(?:(\d+)d)?(?:(\d+)h)?(?:(\d+)m)?(?:(\d+)s)?""")

            override fun parse(input: String): ParseResult<Duration> {
                val match =
                    pattern.matchEntire(input.lowercase())
                        ?: return ParseResult.failure("Invalid duration format. Use: 1d2h30m45s")
                val days = match.groupValues[1].toLongOrNull() ?: 0
                val hours = match.groupValues[2].toLongOrNull() ?: 0
                val minutes = match.groupValues[3].toLongOrNull() ?: 0
                val seconds = match.groupValues[4].toLongOrNull() ?: 0
                if (days == 0L && hours == 0L && minutes == 0L && seconds == 0L) {
                    return ParseResult.failure("Invalid duration. Use formats like: 1d, 2h, 30m, 45s")
                }
                return ParseResult.success(
                    Duration
                        .ofDays(days)
                        .plusHours(hours)
                        .plusMinutes(minutes)
                        .plusSeconds(seconds),
                )
            }

            override fun complete(input: String) = listOf("1h", "30m", "1d", "12h", "7d")
        }
    // ─────────────────────────────────────────────────────────────────────────
    // GENERIC PARSERS
    // ─────────────────────────────────────────────────────────────────────────

    /** Create an enum parser */
    inline fun <reified E : Enum<E>> enum() =
        object : ArgParser<E> {
            private val values = enumValues<E>()
            private val names = values.map { it.name.lowercase() }

            override fun parse(input: String): ParseResult<E> =
                values
                    .find { it.name.equals(input, ignoreCase = true) }
                    ?.let { ParseResult.success(it) }
                    ?: ParseResult.failure("Invalid option '$input'. Available: ${names.joinToString()}")

            override fun complete(input: String) = names.filter { it.startsWith(input.lowercase()) }
        }

    /** Fixed choices parser */
    fun choice(vararg options: String) =
        object : ArgParser<String> {
            private val optionSet = options.map { it.lowercase() }.toSet()

            override fun parse(input: String): ParseResult<String> =
                if (input.lowercase() in optionSet) {
                    ParseResult.success(input)
                } else {
                    ParseResult.failure("Invalid choice. Options: ${options.joinToString()}")
                }

            override fun complete(input: String) = options.filter { it.startsWith(input, ignoreCase = true) }.toList()
        }

    /** Positive integer (> 0) */
    val POSITIVE_INT = int(min = 1)

    /** Non-negative integer (>= 0) */
    val NON_NEGATIVE_INT = int(min = 0)

    /** Percentage (0-100) */
    val PERCENTAGE = int(min = 0, max = 100)
}
