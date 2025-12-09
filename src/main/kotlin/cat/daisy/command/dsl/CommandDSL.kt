package cat.daisy.command.dsl
import cat.daisy.command.arguments.ArgParser
import cat.daisy.command.arguments.ArgumentDef
import cat.daisy.command.context.CommandContext
import cat.daisy.command.context.PlayerContext
import cat.daisy.command.context.TabContext
import cat.daisy.command.core.DaisyCommand
import cat.daisy.command.core.DaisyCommands
import cat.daisy.command.core.SubCommand
import cat.daisy.command.core.SubCommandData

// DaisyCommand DSL
//
// Modern Kotlin DSL for creating commands with:
// - Clean, type-safe syntax
// - Full argument support
// - Nested subcommands
// - Cooldowns and permissions

// DSL ENTRY POINTS
inline fun daisyCommand(
    name: String,
    block: DaisyCommandBuilder.() -> Unit,
): DaisyCommand = DaisyCommandBuilder(name).apply(block).build().also { DaisyCommands.register(it) }

inline fun buildCommand(
    name: String,
    block: DaisyCommandBuilder.() -> Unit,
): DaisyCommand = DaisyCommandBuilder(name).apply(block).build()

// COMMAND BUILDER
class DaisyCommandBuilder(
    private val name: String,
) {
    @JvmField var description: String = ""

    @JvmField var usage: String = "/$name"

    @JvmField var permission: String? = null

    @JvmField var aliases: Array<String> = emptyArray()

    @JvmField var playerOnly: Boolean = false

    @JvmField var cooldown: Int = 0

    @JvmField var cooldownMessage: String? = null

    @JvmField var cooldownBypassPermission: String? = null

    @PublishedApi internal val subcommands = mutableListOf<SubCommandData>()

    @PublishedApi internal val arguments = mutableListOf<ArgumentDef>()

    @PublishedApi internal var executor: (CommandContext.() -> Unit)? = null

    @PublishedApi internal var tabProvider: (TabContext.() -> List<String>)? = null

    // Java-friendly setters
    fun setDescription(value: String): DaisyCommandBuilder {
        description = value
        return this
    }

    fun setUsage(value: String): DaisyCommandBuilder {
        usage = value
        return this
    }

    fun setPermission(value: String?): DaisyCommandBuilder {
        permission = value
        return this
    }

    fun setAliases(vararg names: String): DaisyCommandBuilder {
        aliases = names.toList().toTypedArray()
        return this
    }

    fun setPlayerOnly(value: Boolean): DaisyCommandBuilder {
        playerOnly = value
        return this
    }

    fun setCooldown(seconds: Int): DaisyCommandBuilder {
        cooldown = seconds
        return this
    }

    fun setCooldownMessage(value: String?): DaisyCommandBuilder {
        cooldownMessage = value
        return this
    }

    fun setCooldownBypassPermission(value: String?): DaisyCommandBuilder {
        cooldownBypassPermission = value
        return this
    }

    fun withAliases(vararg names: String) {
        aliases = names.toList().toTypedArray()
    }

    fun stringArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.StringArg(name, optional)
    }

    fun greedyStringArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.GreedyStringArg(name, optional)
    }

    fun intArgument(
        name: String,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
        optional: Boolean = false,
    ) {
        arguments +=
            ArgumentDef.IntArg(name, min, max, optional)
    }

    fun longArgument(
        name: String,
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
        optional: Boolean = false,
    ) {
        arguments +=
            ArgumentDef.LongArg(name, min, max, optional)
    }

    fun doubleArgument(
        name: String,
        min: Double = Double.MIN_VALUE,
        max: Double = Double.MAX_VALUE,
        optional: Boolean = false,
    ) {
        arguments +=
            ArgumentDef.DoubleArg(name, min, max, optional)
    }

    fun floatArgument(
        name: String,
        min: Float = Float.MIN_VALUE,
        max: Float = Float.MAX_VALUE,
        optional: Boolean = false,
    ) {
        arguments +=
            ArgumentDef.FloatArg(name, min, max, optional)
    }

    fun booleanArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.BooleanArg(name, optional)
    }

    fun playerArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.PlayerArg(name, optional)
    }

    fun offlinePlayerArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.OfflinePlayerArg(name, optional)
    }

    fun worldArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.WorldArg(name, optional)
    }

    fun materialArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.MaterialArg(name, optional)
    }

    fun gameModeArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.GameModeArg(name, optional)
    }

    fun entityTypeArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.EntityTypeArg(name, optional)
    }

    fun uuidArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.UUIDArg(name, optional)
    }

    fun durationArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments += ArgumentDef.DurationArg(name, optional)
    }

    fun choiceArgument(
        name: String,
        vararg choices: String,
        optional: Boolean = false,
    ) {
        arguments +=
            ArgumentDef.ChoiceArg(name, choices.toList(), optional)
    }

    inline fun <reified E : Enum<E>> enumArgument(
        name: String,
        optional: Boolean = false,
    ) {
        arguments +=
            ArgumentDef.EnumArg(name, E::class.java, optional)
    }

    fun <T> customArgument(
        name: String,
        parser: ArgParser<T>,
        optional: Boolean = false,
    ) {
        arguments +=
            ArgumentDef.CustomArg(name, parser, optional)
    }

    inline fun subcommand(
        subName: String,
        block: SubCommandBuilder.() -> Unit,
    ) {
        subcommands +=
            SubCommandBuilder(subName).apply(block).build()
    }

    fun onExecute(block: CommandContext.() -> Unit) {
        executor = block
    }

    fun playerExecutor(block: PlayerContext.() -> Unit) {
        playerOnly = true
        executor = { asPlayer()?.let { PlayerContext(it, args, namedArgs, label).block() } }
    }

    fun tabComplete(block: TabContext.() -> List<String>) {
        tabProvider = block
    }

    @PublishedApi internal fun build(): DaisyCommand {
        val cmd =
            DaisyCommand(
                name,
                description,
                usage,
                permission,
                aliases,
                playerOnly,
                cooldown,
                cooldownMessage,
                cooldownBypassPermission,
                arguments.toList(),
            )
        subcommands.forEach { data ->
            cmd.addSubcommand(
                data.name,
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
                ),
            )
        }
        executor?.let { cmd.onExecute(it) }
        tabProvider?.let { cmd.tabComplete(it) }
        return cmd
    }
}

// SUBCOMMAND BUILDER
class SubCommandBuilder
    @PublishedApi
    internal constructor(
        private val name: String,
    ) {
        var description: String = ""
        var permission: String? = null
        var playerOnly: Boolean = false
        var cooldown: Int = 0
        var cooldownMessage: String? = null
        var cooldownBypassPermission: String? = null
        var aliases: List<String> = emptyList()

        @PublishedApi internal val arguments = mutableListOf<ArgumentDef>()

        @PublishedApi internal val subcommands = mutableListOf<SubCommandData>()

        @PublishedApi internal var executor: (CommandContext.() -> Unit) = {}

        @PublishedApi internal var tabProvider: (TabContext.() -> List<String>)? = null

        fun stringArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.StringArg(name, optional)
        }

        fun greedyStringArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.GreedyStringArg(name, optional)
        }

        fun intArgument(
            name: String,
            min: Int = Int.MIN_VALUE,
            max: Int = Int.MAX_VALUE,
            optional: Boolean = false,
        ) {
            arguments +=
                ArgumentDef.IntArg(name, min, max, optional)
        }

        fun longArgument(
            name: String,
            min: Long = Long.MIN_VALUE,
            max: Long = Long.MAX_VALUE,
            optional: Boolean = false,
        ) {
            arguments +=
                ArgumentDef.LongArg(name, min, max, optional)
        }

        fun doubleArgument(
            name: String,
            min: Double = Double.MIN_VALUE,
            max: Double = Double.MAX_VALUE,
            optional: Boolean = false,
        ) {
            arguments +=
                ArgumentDef.DoubleArg(name, min, max, optional)
        }

        fun floatArgument(
            name: String,
            min: Float = Float.MIN_VALUE,
            max: Float = Float.MAX_VALUE,
            optional: Boolean = false,
        ) {
            arguments +=
                ArgumentDef.FloatArg(name, min, max, optional)
        }

        fun booleanArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.BooleanArg(name, optional)
        }

        fun playerArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.PlayerArg(name, optional)
        }

        fun offlinePlayerArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.OfflinePlayerArg(name, optional)
        }

        fun worldArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.WorldArg(name, optional)
        }

        fun materialArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.MaterialArg(name, optional)
        }

        fun gameModeArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.GameModeArg(name, optional)
        }

        fun entityTypeArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.EntityTypeArg(name, optional)
        }

        fun uuidArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.UUIDArg(name, optional)
        }

        fun durationArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments += ArgumentDef.DurationArg(name, optional)
        }

        fun choiceArgument(
            name: String,
            vararg choices: String,
            optional: Boolean = false,
        ) {
            arguments +=
                ArgumentDef.ChoiceArg(name, choices.toList(), optional)
        }

        inline fun <reified E : Enum<E>> enumArgument(
            name: String,
            optional: Boolean = false,
        ) {
            arguments +=
                ArgumentDef.EnumArg(name, E::class.java, optional)
        }

        fun <T> customArgument(
            name: String,
            parser: ArgParser<T>,
            optional: Boolean = false,
        ) {
            arguments +=
                ArgumentDef.CustomArg(name, parser, optional)
        }

        inline fun subcommand(
            subName: String,
            block: SubCommandBuilder.() -> Unit,
        ) {
            subcommands +=
                SubCommandBuilder(subName).apply(block).build()
        }

        fun onExecute(block: CommandContext.() -> Unit) {
            executor = block
        }

        fun playerExecutor(block: PlayerContext.() -> Unit) {
            playerOnly = true
            executor = { asPlayer()?.let { PlayerContext(it, args, namedArgs, label).block() } }
        }

        fun tabComplete(block: TabContext.() -> List<String>) {
            tabProvider = block
        }

        @PublishedApi internal fun build() =
            SubCommandData(
                name,
                description,
                permission,
                playerOnly,
                cooldown,
                cooldownMessage,
                cooldownBypassPermission,
                aliases,
                arguments.toList(),
                subcommands.toList(),
                executor,
                tabProvider,
            )
    }
