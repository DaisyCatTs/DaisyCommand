@file:JvmName("DaisyCommand")

package cat.daisy.command

import cat.daisy.command.arguments.ArgParser
import cat.daisy.command.arguments.ArgumentDef
import cat.daisy.command.arguments.ParseResult
import cat.daisy.command.arguments.Parsers
import cat.daisy.command.context.CommandContext
import cat.daisy.command.context.PlayerContext
import cat.daisy.command.context.TabContext
import cat.daisy.command.cooldown.DaisyCooldowns
import cat.daisy.command.core.DaisyCommands
import cat.daisy.command.core.SubCommand
import cat.daisy.command.dsl.DaisyCommandBuilder
import cat.daisy.command.dsl.SubCommandBuilder
import cat.daisy.command.dsl.buildCommand
import cat.daisy.command.dsl.daisyCommand
import cat.daisy.command.text.DaisyText

/**
 * # DaisyCommand - Modern Kotlin Command Framework for Paper/Spigot
 *
 * A high-performance, type-safe command framework featuring:
 * - **Beautiful Kotlin DSL** - Clean, expressive syntax
 * - **Full Java Support** - Works seamlessly with Java plugins
 * - **MiniMessage Integration** - Modern text formatting with gradients and hex colors
 * - **Type-Safe Arguments** - Built-in parsers with validation
 * - **Nested Subcommands** - Infinite nesting with independent permissions
 * - **Cooldown System** - Thread-safe with bypass permissions
 * - **Smart Tab Completion** - Automatic and customizable
 * - **Zero Configuration** - No plugin.yml entries needed
 * - **Security First** - Input validation and sanitization
 *
 * ## Quick Start (Kotlin)
 * ```kotlin
 * class MyPlugin : JavaPlugin() {
 *     override fun onEnable() {
 *         DaisyCommands.initialize(this)
 *
 *         daisyCommand("greet") {
 *             description = "Greet a player"
 *             permission = "myplugin.greet"
 *             playerArgument("target")
 *
 *             playerExecutor {
 *                 val target = getPlayer("target") ?: player
 *                 success("Hello, ${target.name}!")
 *             }
 *         }
 *     }
 *
 *     override fun onDisable() {
 *         DaisyCommands.shutdown()
 *     }
 * }
 * ```
 *
 * ## Quick Start (Java)
 * ```java
 * public class MyPlugin extends JavaPlugin {
 *     @Override
 *     public void onEnable() {
 *         DaisyCommands.INSTANCE.initialize(this);
 *
 *         DaisyCommand.create("greet", builder -> {
 *             builder.setDescription("Greet a player");
 *             builder.setPermission("myplugin.greet");
 *             builder.playerArgument("target", false);
 *             builder.onExecute(ctx -> {
 *                 ctx.success("Hello!");
 *             });
 *         });
 *     }
 *
 *     @Override
 *     public void onDisable() {
 *         DaisyCommands.INSTANCE.shutdown();
 *     }
 * }
 * ```
 *
 * @author Daisy
 * @version 1.0.0
 * @see DaisyCommands
 * @see daisyCommand
 */
object DaisyCommandAPI {
    /** Current library version */
    const val VERSION = "1.0.0"

    /**
     * Create and register a command (Java-friendly).
     * @param name Command name
     * @param configure Configuration block
     * @return The created command
     */
    @JvmStatic
    fun create(
        name: String,
        configure: java.util.function.Consumer<DaisyCommandBuilder>,
    ): cat.daisy.command.core.DaisyCommand {
        val builder = DaisyCommandBuilder(name)
        configure.accept(builder)
        val command = builder.build()
        DaisyCommands.register(command)
        return command
    }

    /**
     * Build a command without registering it (Java-friendly).
     * @param name Command name
     * @param configure Configuration block
     * @return The created command
     */
    @JvmStatic
    fun build(
        name: String,
        configure: java.util.function.Consumer<DaisyCommandBuilder>,
    ): cat.daisy.command.core.DaisyCommand {
        val builder = DaisyCommandBuilder(name)
        configure.accept(builder)
        return builder.build()
    }
}

// Type aliases for convenience
typealias DaisyCmd = cat.daisy.command.core.DaisyCommand
typealias SubCmd = SubCommand
typealias CmdContext = CommandContext
typealias PlayerCtx = PlayerContext
typealias TabCtx = TabContext
