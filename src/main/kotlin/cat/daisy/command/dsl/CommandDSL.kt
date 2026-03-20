@file:Suppress("unused", "UNCHECKED_CAST")

package cat.daisy.command.dsl

import cat.daisy.command.arguments.ArgumentKind
import cat.daisy.command.arguments.ArgumentRef
import cat.daisy.command.arguments.CompiledArgument
import cat.daisy.command.arguments.DaisyParser
import cat.daisy.command.arguments.MutableArgumentDefinition
import cat.daisy.command.arguments.Parsers
import cat.daisy.command.context.CommandContext
import cat.daisy.command.context.ConsoleCommandContext
import cat.daisy.command.context.PlayerCommandContext
import cat.daisy.command.core.AnyHandler
import cat.daisy.command.core.CommandNodeSpec
import cat.daisy.command.core.CommandSpec
import cat.daisy.command.core.ConsoleHandler
import cat.daisy.command.core.CooldownSpec
import cat.daisy.command.core.DaisyConfig
import cat.daisy.command.core.DaisyConfigBuilder
import cat.daisy.command.core.HandlerSpec
import cat.daisy.command.core.PlayerHandler
import cat.daisy.command.core.RequirementSpec
import cat.daisy.command.core.SenderConstraint
import java.time.Duration

fun command(
    name: String,
    block: CommandBuilder.() -> Unit,
): CommandSpec = CommandBuilder(name, root = true).apply(block).build()

class CommandSetBuilder {
    private val commands = mutableListOf<CommandSpec>()
    private val configBuilder = DaisyConfigBuilder()

    fun command(
        name: String,
        block: CommandBuilder.() -> Unit,
    ) {
        commands +=
            cat.daisy.command.dsl
                .command(name, block)
    }

    fun add(command: CommandSpec) {
        commands += command
    }

    fun config(block: DaisyConfigBuilder.() -> Unit) {
        configBuilder.apply(block)
    }

    internal fun build(): BuiltCommandSet = BuiltCommandSet(commands.toList(), configBuilder.build())
}

internal data class BuiltCommandSet(
    val commands: List<CommandSpec>,
    val config: DaisyConfig,
)

class CommandBuilder internal constructor(
    private val name: String,
    private val root: Boolean,
) {
    private var description: String = ""
    private var permission: String? = null
    private var aliases: MutableList<String> = mutableListOf()
    private var senderConstraint: SenderConstraint = SenderConstraint.ANY
    private var cooldown: CooldownSpec? = null
    private var handler: HandlerSpec? = null
    private val requirements = mutableListOf<RequirementSpec>()
    private val children = mutableListOf<CommandBuilder>()
    private val arguments = mutableListOf<MutableArgumentDefinition>()

    fun description(text: String) {
        description = text
    }

    fun aliases(vararg values: String) {
        aliases = values.toMutableList()
    }

    fun permission(nodePermission: String) {
        permission = nodePermission
    }

    fun playerOnly() {
        senderConstraint = SenderConstraint.PLAYER_ONLY
    }

    fun consoleOnly() {
        senderConstraint = SenderConstraint.CONSOLE_ONLY
    }

    fun cooldown(
        duration: Duration,
        bypassPermission: String? = null,
        message: String? = null,
    ) {
        cooldown = CooldownSpec(duration, bypassPermission, message)
    }

    fun requires(
        message: String? = null,
        predicate: CommandContext.() -> Boolean,
    ) {
        requirements += RequirementSpec(message, predicate)
    }

    fun sub(
        name: String,
        block: CommandBuilder.() -> Unit,
    ) {
        children += CommandBuilder(name, root = false).apply(block)
    }

    fun execute(block: CommandContext.() -> Unit) {
        setHandler(AnyHandler(block))
    }

    fun executePlayer(block: PlayerCommandContext.() -> Unit) {
        playerOnly()
        setHandler(PlayerHandler(block))
    }

    fun executeConsole(block: ConsoleCommandContext.() -> Unit) {
        consoleOnly()
        setHandler(ConsoleHandler(block))
    }

    fun string(
        name: String,
        block: ArgumentRef<String>.() -> Unit = {},
    ): ArgumentRef<String> = positional(name, Parsers.STRING, block)

    fun text(
        name: String,
        block: ArgumentRef<String>.() -> Unit = {},
    ): ArgumentRef<String> = positional(name, Parsers.TEXT, block)

    fun int(
        name: String,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
        block: ArgumentRef<Int>.() -> Unit = {},
    ): ArgumentRef<Int> = positional(name, Parsers.int(min, max), block)

    fun long(
        name: String,
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
        block: ArgumentRef<Long>.() -> Unit = {},
    ): ArgumentRef<Long> = positional(name, Parsers.long(min, max), block)

    fun double(
        name: String,
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
        block: ArgumentRef<Double>.() -> Unit = {},
    ): ArgumentRef<Double> = positional(name, Parsers.double(min, max), block)

    fun float(
        name: String,
        min: Float = Float.NEGATIVE_INFINITY,
        max: Float = Float.POSITIVE_INFINITY,
        block: ArgumentRef<Float>.() -> Unit = {},
    ): ArgumentRef<Float> = positional(name, Parsers.float(min, max), block)

    fun boolean(
        name: String,
        block: ArgumentRef<Boolean>.() -> Unit = {},
    ): ArgumentRef<Boolean> = positional(name, Parsers.BOOLEAN, block)

    fun player(
        name: String,
        block: ArgumentRef<org.bukkit.entity.Player>.() -> Unit = {},
    ): ArgumentRef<org.bukkit.entity.Player> = positional(name, Parsers.PLAYER, block)

    fun offlinePlayer(
        name: String,
        block: ArgumentRef<org.bukkit.OfflinePlayer>.() -> Unit = {},
    ): ArgumentRef<org.bukkit.OfflinePlayer> = positional(name, Parsers.OFFLINE_PLAYER, block)

    fun world(
        name: String,
        block: ArgumentRef<org.bukkit.World>.() -> Unit = {},
    ): ArgumentRef<org.bukkit.World> = positional(name, Parsers.WORLD, block)

    fun material(
        name: String,
        block: ArgumentRef<org.bukkit.Material>.() -> Unit = {},
    ): ArgumentRef<org.bukkit.Material> = positional(name, Parsers.MATERIAL, block)

    fun gameMode(
        name: String,
        block: ArgumentRef<org.bukkit.GameMode>.() -> Unit = {},
    ): ArgumentRef<org.bukkit.GameMode> = positional(name, Parsers.GAME_MODE, block)

    fun entityType(
        name: String,
        block: ArgumentRef<org.bukkit.entity.EntityType>.() -> Unit = {},
    ): ArgumentRef<org.bukkit.entity.EntityType> = positional(name, Parsers.ENTITY_TYPE, block)

    fun uuid(
        name: String,
        block: ArgumentRef<java.util.UUID>.() -> Unit = {},
    ): ArgumentRef<java.util.UUID> = positional(name, Parsers.UUID_PARSER, block)

    fun duration(
        name: String,
        block: ArgumentRef<Duration>.() -> Unit = {},
    ): ArgumentRef<Duration> = positional(name, Parsers.DURATION, block)

    fun choice(
        name: String,
        vararg options: String,
        block: ArgumentRef<String>.() -> Unit = {},
    ): ArgumentRef<String> = positional(name, Parsers.choice(*options), block)

    inline fun <reified E : Enum<E>> enum(
        name: String,
        noinline block: ArgumentRef<E>.() -> Unit = {},
    ): ArgumentRef<E> = positionalPublished(name, Parsers.enum(), block)

    fun <T> argument(
        name: String,
        parser: DaisyParser<T>,
        block: ArgumentRef<T>.() -> Unit = {},
    ): ArgumentRef<T> = positional(name, parser, block)

    fun flag(
        longName: String,
        shortName: String? = null,
        block: ArgumentRef<Boolean>.() -> Unit = {},
    ): ArgumentRef<Boolean> = optionInternal(longName, Parsers.BOOLEAN, shortName, ArgumentKind.FLAG, block)

    fun stringOption(
        longName: String,
        shortName: String? = null,
        block: ArgumentRef<String>.() -> Unit = {},
    ): ArgumentRef<String> = optionInternal(longName, Parsers.STRING, shortName, ArgumentKind.OPTION, block)

    fun textOption(
        longName: String,
        shortName: String? = null,
        block: ArgumentRef<String>.() -> Unit = {},
    ): ArgumentRef<String> = optionInternal(longName, Parsers.OPTION_TEXT, shortName, ArgumentKind.OPTION, block)

    fun intOption(
        longName: String,
        shortName: String? = null,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
        block: ArgumentRef<Int>.() -> Unit = {},
    ): ArgumentRef<Int> = optionInternal(longName, Parsers.int(min, max), shortName, ArgumentKind.OPTION, block)

    fun longOption(
        longName: String,
        shortName: String? = null,
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
        block: ArgumentRef<Long>.() -> Unit = {},
    ): ArgumentRef<Long> = optionInternal(longName, Parsers.long(min, max), shortName, ArgumentKind.OPTION, block)

    fun doubleOption(
        longName: String,
        shortName: String? = null,
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
        block: ArgumentRef<Double>.() -> Unit = {},
    ): ArgumentRef<Double> = optionInternal(longName, Parsers.double(min, max), shortName, ArgumentKind.OPTION, block)

    fun floatOption(
        longName: String,
        shortName: String? = null,
        min: Float = Float.NEGATIVE_INFINITY,
        max: Float = Float.POSITIVE_INFINITY,
        block: ArgumentRef<Float>.() -> Unit = {},
    ): ArgumentRef<Float> = optionInternal(longName, Parsers.float(min, max), shortName, ArgumentKind.OPTION, block)

    fun booleanOption(
        longName: String,
        shortName: String? = null,
        block: ArgumentRef<Boolean>.() -> Unit = {},
    ): ArgumentRef<Boolean> = optionInternal(longName, Parsers.BOOLEAN, shortName, ArgumentKind.OPTION, block)

    fun playerOption(
        longName: String,
        shortName: String? = null,
        block: ArgumentRef<org.bukkit.entity.Player>.() -> Unit = {},
    ): ArgumentRef<org.bukkit.entity.Player> = optionInternal(longName, Parsers.PLAYER, shortName, ArgumentKind.OPTION, block)

    fun durationOption(
        longName: String,
        shortName: String? = null,
        block: ArgumentRef<Duration>.() -> Unit = {},
    ): ArgumentRef<Duration> = optionInternal(longName, Parsers.DURATION, shortName, ArgumentKind.OPTION, block)

    fun <T> option(
        longName: String,
        parser: DaisyParser<T>,
        shortName: String? = null,
        block: ArgumentRef<T>.() -> Unit = {},
    ): ArgumentRef<T> = optionInternal(longName, parser, shortName, ArgumentKind.OPTION, block)

    internal fun build(): CommandSpec {
        check(root) { "Only root builders can build a CommandSpec." }
        return CommandSpec(
            name = name,
            description = description,
            aliases = aliases.toList(),
            permission = permission,
            senderConstraint = senderConstraint,
            cooldown = cooldown,
            arguments = compileArguments(),
            requirements = requirements.toList(),
            children = children.map { it.buildNode() },
            handler = handler,
        )
    }

    private fun buildNode(): CommandNodeSpec =
        CommandNodeSpec(
            name = name,
            description = description,
            aliases = aliases.toList(),
            permission = permission,
            senderConstraint = senderConstraint,
            cooldown = cooldown,
            arguments = compileArguments(),
            requirements = requirements.toList(),
            children = children.map { it.buildNode() },
            handler = handler,
        )

    private fun compileArguments(): List<CompiledArgument> =
        arguments.map {
            CompiledArgument(
                slot = it.slot,
                name = it.name,
                parser = it.parser,
                kind = it.kind,
                longName = it.longName,
                shortName = it.shortName,
                optional = it.optional,
                hasDefault = it.defaultValue !== cat.daisy.command.arguments.NoDefaultValue,
                defaultValue = it.defaultValue,
                description = it.description,
                suggestions = it.suggestions,
                validatorMessage = it.validatorMessage,
                validator = it.validator,
            )
        }

    private fun setHandler(value: HandlerSpec) {
        check(handler == null) { "Node '$name' already has an execution handler." }
        handler = value
    }

    private fun <T> positional(
        name: String,
        parser: DaisyParser<T>,
        block: ArgumentRef<T>.() -> Unit,
    ): ArgumentRef<T> =
        createArgument(
            name = name,
            parser = parser,
            kind = ArgumentKind.POSITIONAL,
            longName = null,
            shortName = null,
            block = block,
        )

    @PublishedApi
    internal fun <T> positionalPublished(
        name: String,
        parser: DaisyParser<T>,
        block: ArgumentRef<T>.() -> Unit,
    ): ArgumentRef<T> = positional(name, parser, block)

    private fun <T> optionInternal(
        longName: String,
        parser: DaisyParser<T>,
        shortName: String?,
        kind: ArgumentKind,
        block: ArgumentRef<T>.() -> Unit,
    ): ArgumentRef<T> =
        createArgument(
            name = longName,
            parser = parser,
            kind = kind,
            longName = longName,
            shortName = shortName,
            block = block,
        )

    private fun <T> createArgument(
        name: String,
        parser: DaisyParser<T>,
        kind: ArgumentKind,
        longName: String?,
        shortName: String?,
        block: ArgumentRef<T>.() -> Unit,
    ): ArgumentRef<T> {
        val definition =
            MutableArgumentDefinition(
                slot = arguments.size,
                name = name,
                parser = parser as DaisyParser<Any?>,
                kind = kind,
                longName = longName,
                shortName = shortName,
            )
        val ref = ArgumentRef<T>(definition, name)
        arguments += definition
        ref.apply(block)
        return ref
    }
}
