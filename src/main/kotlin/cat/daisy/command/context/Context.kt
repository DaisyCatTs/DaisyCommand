@file:Suppress("unused")

package cat.daisy.command.context

import cat.daisy.command.arguments.ArgumentRef
import cat.daisy.command.text.DaisyText.mm
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import java.util.logging.Logger

internal class ResolvedArguments(
    private val valuesBySlot: Array<Any?>,
    private val valuesByName: Map<String, Any?>,
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> get(ref: ArgumentRef<T>): T = valuesBySlot[ref.definition.slot] as T

    fun byName(name: String): Any? = valuesByName[name]
}

internal object AbortExecution : RuntimeException(null, null, false, false)

open class CommandContext internal constructor(
    val sender: CommandSender,
    val label: String,
    val path: List<String>,
    val args: List<String>,
    private val resolvedArguments: ResolvedArguments,
    private val logger: Logger,
) {
    private var successful = true

    val isPlayer: Boolean get() = sender is Player
    val isConsole: Boolean get() = sender is ConsoleCommandSender
    open val player: Player? get() = sender as? Player

    internal fun markFailed() {
        successful = false
    }

    internal fun wasSuccessful(): Boolean = successful

    operator fun get(name: String): Any? = resolvedArguments.byName(name)

    @Suppress("UNCHECKED_CAST")
    fun <T> getArg(name: String): T? = resolvedArguments.byName(name) as? T

    fun getString(name: String): String? = getArg(name)

    fun getInt(name: String): Int? = getArg(name)

    fun getLong(name: String): Long? = getArg(name)

    fun getDouble(name: String): Double? = getArg(name)

    fun getFloat(name: String): Float? = getArg(name)

    fun getBoolean(name: String): Boolean? = getArg(name)

    fun getPlayer(name: String): Player? = getArg(name)

    fun arg(index: Int): String? = args.getOrNull(index)

    fun joinArgs(fromIndex: Int = 0): String = args.drop(fromIndex).joinToString(" ")

    val argCount: Int get() = args.size

    fun send(message: String) {
        sender.sendMessage(message.mm())
    }

    fun send(component: Component) {
        sender.sendMessage(component)
    }

    fun reply(message: String) {
        send("<gray>$message")
    }

    fun info(message: String) {
        send("<gray>$message")
    }

    fun warn(message: String) {
        send("<yellow>$message")
    }

    fun error(message: String) {
        successful = false
        send("<red>$message")
    }

    fun fail(message: String): Nothing {
        error(message)
        throw AbortExecution
    }

    fun broadcast(message: String) {
        Bukkit.broadcast(message.mm())
    }

    fun requirePlayer(): Player = player ?: fail("This command can only be used by a player.")

    fun requirePermission(permission: String) {
        if (!sender.hasPermission(permission)) {
            fail("You do not have permission to do that.")
        }
    }

    fun logFailure(
        message: String,
        throwable: Throwable,
    ) {
        logger.severe(message)
        logger.severe(throwable.stackTraceToString())
    }

    fun <T> get(ref: ArgumentRef<T>): T = resolvedArguments.get(ref)

    operator fun <T> ArgumentRef<T>.invoke(): T = get(this)
}

class PlayerCommandContext internal constructor(
    override val player: Player,
    label: String,
    path: List<String>,
    args: List<String>,
    resolvedArguments: ResolvedArguments,
    logger: Logger,
) : CommandContext(player, label, path, args, resolvedArguments, logger)

class ConsoleCommandContext internal constructor(
    val console: ConsoleCommandSender,
    label: String,
    path: List<String>,
    args: List<String>,
    resolvedArguments: ResolvedArguments,
    logger: Logger,
) : CommandContext(console, label, path, args, resolvedArguments, logger)
