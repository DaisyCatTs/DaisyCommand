@file:JvmName("DaisyCommand")
@file:Suppress("unused", "DEPRECATION")

package cat.daisy.command

import cat.daisy.command.context.CommandContext
import cat.daisy.command.context.ConsoleCommandContext
import cat.daisy.command.context.PlayerCommandContext
import cat.daisy.command.core.CommandSpec
import cat.daisy.command.core.DaisyCommands
import cat.daisy.command.dsl.CommandBuilder
import java.util.function.Consumer

object DaisyCommandAPI {
    const val VERSION = "2.0.0"

    @JvmStatic
    fun create(
        name: String,
        configure: Consumer<CommandBuilder>,
    ): CommandSpec {
        val builder = CommandBuilder(name, root = true)
        configure.accept(builder)
        val command = builder.build()
        DaisyCommands.register(command)
        return command
    }

    @JvmStatic
    fun build(
        name: String,
        configure: Consumer<CommandBuilder>,
    ): CommandSpec {
        val builder = CommandBuilder(name, root = true)
        configure.accept(builder)
        return builder.build()
    }
}

typealias DaisyCmd = CommandSpec
typealias CmdContext = CommandContext
typealias PlayerCtx = PlayerCommandContext
typealias ConsoleCtx = ConsoleCommandContext
