@file:Suppress("unused")

package cat.daisy.command.core

import cat.daisy.command.cooldown.DaisyCooldowns
import cat.daisy.command.dsl.CommandSetBuilder
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ConcurrentHashMap

fun JavaPlugin.registerCommands(vararg commands: CommandSpec) {
    for (command in commands) {
        val compiled = command.compiled
        val runtime = CommandRuntime(logger)
        registerCommand(command.name, command.description, command.aliases, PaperCommandAdapter(compiled, runtime))
    }
}

fun JavaPlugin.registerCommands(block: CommandSetBuilder.() -> Unit) {
    val builder = CommandSetBuilder().apply(block)
    registerCommands(*builder.build().toTypedArray())
}

@Deprecated("Use JavaPlugin.registerCommands(...) instead.")
object DaisyCommands {
    private var plugin: JavaPlugin? = null
    private val registered = ConcurrentHashMap<String, CommandSpec>()

    fun initialize(plugin: JavaPlugin) {
        this.plugin = plugin
    }

    fun register(command: CommandSpec) {
        val plugin = plugin ?: error("DaisyCommands is not initialized. Call initialize(plugin) first.")
        registered[command.name.lowercase()] = command
        plugin.registerCommands(command)
    }

    operator fun get(name: String): CommandSpec? = registered[name.lowercase()]

    fun getAll(): Collection<CommandSpec> = registered.values.toSet()

    fun isRegistered(name: String): Boolean = registered.containsKey(name.lowercase())

    fun shutdown() {
        registered.clear()
        plugin = null
        DaisyCooldowns.clearAll()
    }
}

internal class PaperCommandAdapter(
    private val command: CompiledCommand,
    private val runtime: CommandRuntime,
) : BasicCommand {
    override fun execute(
        commandSourceStack: CommandSourceStack,
        args: Array<String>,
    ) {
        command.execute(commandSourceStack.sender, command.name, args.toList(), runtime)
    }

    override fun suggest(
        commandSourceStack: CommandSourceStack,
        args: Array<String>,
    ): Collection<String> = command.suggest(commandSourceStack.sender, args.toList(), runtime)

    override fun canUse(sender: CommandSender): Boolean =
        when (command.root.senderConstraint) {
            SenderConstraint.ANY -> {
                command.root.ownPermission == null || sender.hasPermission(command.root.ownPermission)
            }

            SenderConstraint.PLAYER_ONLY -> {
                sender is org.bukkit.entity.Player &&
                    (command.root.ownPermission == null || sender.hasPermission(command.root.ownPermission))
            }

            SenderConstraint.CONSOLE_ONLY -> {
                sender is org.bukkit.command.ConsoleCommandSender &&
                    (command.root.ownPermission == null || sender.hasPermission(command.root.ownPermission))
            }
        }

    override fun permission(): String? = command.root.ownPermission
}
