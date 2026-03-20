@file:Suppress("unused")

package cat.daisy.command.core

import cat.daisy.command.dsl.CommandSetBuilder
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin

fun JavaPlugin.registerCommands(vararg commands: CommandSpec) {
    registerCompiled(commands.toList(), DaisyConfig())
}

fun JavaPlugin.registerCommands(block: CommandSetBuilder.() -> Unit) {
    val built = CommandSetBuilder().apply(block).build()
    registerCompiled(built.commands, built.config)
}

private fun JavaPlugin.registerCompiled(
    commands: List<CommandSpec>,
    config: DaisyConfig,
) {
    validateRootKeys(commands)
    val runtime = CommandRuntime(logger = logger, config = config)
    for (command in commands) {
        val compiled = command.compiled
        registerCommand(command.name, command.description, command.aliases, PaperCommandAdapter(compiled, runtime))
    }
}

private fun validateRootKeys(commands: List<CommandSpec>) {
    val keys = LinkedHashMap<String, String>()
    for (command in commands) {
        registerRootKey(keys, command.name, command.name)
        for (alias in command.aliases) {
            registerRootKey(keys, alias, command.name)
        }
    }
}

private fun registerRootKey(
    keys: MutableMap<String, String>,
    rawKey: String,
    owner: String,
) {
    val normalized = rawKey.lowercase()
    require(keys.putIfAbsent(normalized, owner) == null) {
        "Duplicate root command key '$rawKey'."
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
