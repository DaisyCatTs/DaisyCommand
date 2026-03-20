@file:Suppress("unused")

package cat.daisy.command.context

import cat.daisy.command.arguments.ArgumentRef
import cat.daisy.command.core.CommandRuntime
import cat.daisy.command.text.DaisyText.mm
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player

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
    private val runtime: CommandRuntime,
) {
    private var successful = true

    val isPlayer: Boolean
        get() = sender is Player

    val isConsole: Boolean
        get() = sender is ConsoleCommandSender

    open val player: Player
        get() = sender as? Player ?: error("This command is not executing as a player.")

    open val console: ConsoleCommandSender
        get() = sender as? ConsoleCommandSender ?: error("This command is not executing from the console.")

    internal fun wasSuccessful(): Boolean = successful

    fun reply(message: String) {
        sendWithPrefix(message)
    }

    fun reply(component: Component) {
        sender.sendMessage(component)
    }

    fun fail(message: String): Nothing {
        successful = false
        sendWithPrefix(message)
        throw AbortExecution
    }

    fun <T> get(ref: ArgumentRef<T>): T = resolvedArguments.get(ref)

    operator fun <T> ArgumentRef<T>.invoke(): T = get(this)

    internal fun sendFramework(message: String) {
        successful = false
        sendWithPrefix(message)
    }

    internal fun logFailure(
        message: String,
        throwable: Throwable,
    ) {
        runtime.logger.severe(message)
        runtime.logger.severe(throwable.stackTraceToString())
    }

    private fun sendWithPrefix(message: String) {
        sender.sendMessage((runtime.config.messages.prefix + message).mm())
    }
}

class PlayerCommandContext internal constructor(
    override val player: Player,
    label: String,
    path: List<String>,
    args: List<String>,
    resolvedArguments: ResolvedArguments,
    runtime: CommandRuntime,
) : CommandContext(player, label, path, args, resolvedArguments, runtime)

class ConsoleCommandContext internal constructor(
    override val console: ConsoleCommandSender,
    label: String,
    path: List<String>,
    args: List<String>,
    resolvedArguments: ResolvedArguments,
    runtime: CommandRuntime,
) : CommandContext(console, label, path, args, resolvedArguments, runtime)
