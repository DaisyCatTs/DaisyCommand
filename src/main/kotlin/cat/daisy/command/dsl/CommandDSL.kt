@file:Suppress("unused", "DEPRECATION", "UNCHECKED_CAST")

package cat.daisy.command.dsl

import cat.daisy.command.arguments.ArgumentRef
import cat.daisy.command.arguments.CompiledArgument
import cat.daisy.command.arguments.DaisyParser
import cat.daisy.command.arguments.MutableArgumentDefinition
import cat.daisy.command.arguments.Parsers
import cat.daisy.command.arguments.optional
import cat.daisy.command.context.CommandContext
import cat.daisy.command.context.ConsoleCommandContext
import cat.daisy.command.context.PlayerCommandContext
import cat.daisy.command.core.AnyHandler
import cat.daisy.command.core.CommandNodeSpec
import cat.daisy.command.core.CommandSpec
import cat.daisy.command.core.ConsoleHandler
import cat.daisy.command.core.CooldownSpec
import cat.daisy.command.core.DaisyCommands
import cat.daisy.command.core.HandlerSpec
import cat.daisy.command.core.PlayerHandler
import cat.daisy.command.core.SenderConstraint
import java.time.Duration
import java.util.function.Consumer

fun command(
    name: String,
    block: CommandBuilder.() -> Unit,
): CommandSpec = CommandBuilder(name, root = true).apply(block).build()

@Deprecated("Use command(name) { ... } and JavaPlugin.registerCommands(...) instead.")
fun daisyCommand(
    name: String,
    block: CommandBuilder.() -> Unit,
): CommandSpec =
    command(name, block).also {
        DaisyCommands.register(it)
    }

@Deprecated("Use command(name) { ... } and registerCommands(...) instead.")
fun buildCommand(
    name: String,
    block: CommandBuilder.() -> Unit,
): CommandSpec = command(name, block)

class CommandSetBuilder {
    private val commands = mutableListOf<CommandSpec>()

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

    internal fun build(): List<CommandSpec> = commands.toList()
}

class CommandBuilder internal constructor(
    private val name: String,
    private val root: Boolean,
) {
    @Deprecated("Use description(\"...\") instead.")
    var description: String = ""

    @Deprecated("Usage is generated automatically. Override only for compatibility.")
    var usage: String = ""

    @Deprecated("Use permission(\"...\") instead.")
    var permission: String? = null

    @Deprecated("Use aliases(\"...\") instead.")
    var aliases: Array<String> = emptyArray()

    @Deprecated("Use playerOnly() instead.")
    var playerOnly: Boolean = false

    var consoleOnly: Boolean = false

    @Deprecated("Use cooldown(Duration) instead.")
    var cooldown: Int = 0

    @Deprecated("Use cooldown(duration, message = ...) instead.")
    var cooldownMessage: String? = null

    @Deprecated("Use cooldown(duration, bypassPermission = ...) instead.")
    var cooldownBypassPermission: String? = null

    private val childBuilders = mutableListOf<CommandBuilder>()
    private val arguments = mutableListOf<MutableArgumentDefinition>()

    private var handler: HandlerSpec? = null
    private var cooldownDuration: Duration? = null
    private var explicitConstraint: SenderConstraint = SenderConstraint.ANY

    fun description(value: String) {
        description = value
    }

    fun permission(value: String) {
        permission = value
    }

    fun aliases(vararg values: String) {
        aliases = values.toList().toTypedArray()
    }

    @Deprecated("Use aliases(vararg) instead.")
    fun withAliases(vararg values: String) {
        aliases(*values)
    }

    fun playerOnly() {
        playerOnly = true
        consoleOnly = false
        explicitConstraint = SenderConstraint.PLAYER_ONLY
    }

    fun consoleOnly() {
        consoleOnly = true
        playerOnly = false
        explicitConstraint = SenderConstraint.CONSOLE_ONLY
    }

    fun cooldown(
        duration: Duration,
        bypassPermission: String? = null,
        message: String? = null,
    ) {
        cooldownDuration = duration
        cooldownBypassPermission = bypassPermission
        cooldownMessage = message
    }

    fun sub(
        name: String,
        block: CommandBuilder.() -> Unit,
    ) {
        childBuilders += CommandBuilder(name, root = false).apply(block)
    }

    @Deprecated("Use sub(name) { ... } instead.")
    fun subcommand(
        name: String,
        block: CommandBuilder.() -> Unit,
    ) {
        sub(name, block)
    }

    fun execute(block: CommandContext.() -> Unit) {
        setHandler(AnyHandler(block))
    }

    @Deprecated("Use execute { ... } instead.")
    fun onExecute(block: CommandContext.() -> Unit) {
        execute(block)
    }

    fun executePlayer(block: PlayerCommandContext.() -> Unit) {
        playerOnly()
        setHandler(PlayerHandler(block))
    }

    @Deprecated("Use executePlayer { ... } instead.")
    fun playerExecutor(block: PlayerCommandContext.() -> Unit) {
        executePlayer(block)
    }

    fun executeConsole(block: ConsoleCommandContext.() -> Unit) {
        consoleOnly()
        setHandler(ConsoleHandler(block))
    }

    fun string(name: String): ArgumentRef<String> = argument(name, Parsers.STRING)

    fun text(name: String): ArgumentRef<String> = argument(name, Parsers.TEXT)

    fun int(
        name: String,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
    ): ArgumentRef<Int> = argument(name, Parsers.int(min, max))

    fun long(
        name: String,
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
    ): ArgumentRef<Long> = argument(name, Parsers.long(min, max))

    fun double(
        name: String,
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
    ): ArgumentRef<Double> = argument(name, Parsers.double(min, max))

    fun float(
        name: String,
        min: Float = Float.NEGATIVE_INFINITY,
        max: Float = Float.POSITIVE_INFINITY,
    ): ArgumentRef<Float> = argument(name, Parsers.float(min, max))

    fun boolean(name: String): ArgumentRef<Boolean> = argument(name, Parsers.BOOLEAN)

    fun player(name: String): ArgumentRef<org.bukkit.entity.Player> = argument(name, Parsers.PLAYER)

    fun offlinePlayer(name: String): ArgumentRef<org.bukkit.OfflinePlayer> = argument(name, Parsers.OFFLINE_PLAYER)

    fun world(name: String): ArgumentRef<org.bukkit.World> = argument(name, Parsers.WORLD)

    fun material(name: String): ArgumentRef<org.bukkit.Material> = argument(name, Parsers.MATERIAL)

    fun gameMode(name: String): ArgumentRef<org.bukkit.GameMode> = argument(name, Parsers.GAME_MODE)

    fun entityType(name: String): ArgumentRef<org.bukkit.entity.EntityType> = argument(name, Parsers.ENTITY_TYPE)

    fun uuid(name: String): ArgumentRef<java.util.UUID> = argument(name, Parsers.UUID_PARSER)

    fun duration(name: String): ArgumentRef<Duration> = argument(name, Parsers.DURATION)

    fun choice(
        name: String,
        vararg options: String,
    ): ArgumentRef<String> = argument(name, Parsers.choice(*options))

    inline fun <reified E : Enum<E>> enum(name: String): ArgumentRef<E> = argument(name, Parsers.enum())

    fun <T> argument(
        name: String,
        parser: DaisyParser<T>,
    ): ArgumentRef<T> {
        val definition =
            MutableArgumentDefinition(
                slot = arguments.size,
                name = name,
                parser = parser as DaisyParser<Any?>,
            )
        arguments += definition
        return ArgumentRef(definition, name)
    }

    fun stringArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = string(name)
        if (optional) {
            ref.optional()
        }
    }

    fun greedyStringArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = text(name)
        if (optional) {
            ref.optional()
        }
    }

    fun intArgument(
        name: String,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE,
        optional: Boolean = false,
    ) {
        val ref = int(name, min, max)
        if (optional) {
            ref.optional()
        }
    }

    fun longArgument(
        name: String,
        min: Long = Long.MIN_VALUE,
        max: Long = Long.MAX_VALUE,
        optional: Boolean = false,
    ) {
        val ref = long(name, min, max)
        if (optional) {
            ref.optional()
        }
    }

    fun doubleArgument(
        name: String,
        min: Double = Double.NEGATIVE_INFINITY,
        max: Double = Double.POSITIVE_INFINITY,
        optional: Boolean = false,
    ) {
        val ref = double(name, min, max)
        if (optional) {
            ref.optional()
        }
    }

    fun floatArgument(
        name: String,
        min: Float = Float.NEGATIVE_INFINITY,
        max: Float = Float.POSITIVE_INFINITY,
        optional: Boolean = false,
    ) {
        val ref = float(name, min, max)
        if (optional) {
            ref.optional()
        }
    }

    fun booleanArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = boolean(name)
        if (optional) {
            ref.optional()
        }
    }

    fun playerArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = player(name)
        if (optional) {
            ref.optional()
        }
    }

    fun offlinePlayerArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = offlinePlayer(name)
        if (optional) {
            ref.optional()
        }
    }

    fun worldArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = world(name)
        if (optional) {
            ref.optional()
        }
    }

    fun materialArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = material(name)
        if (optional) {
            ref.optional()
        }
    }

    fun gameModeArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = gameMode(name)
        if (optional) {
            ref.optional()
        }
    }

    fun entityTypeArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = entityType(name)
        if (optional) {
            ref.optional()
        }
    }

    fun uuidArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = uuid(name)
        if (optional) {
            ref.optional()
        }
    }

    fun durationArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = duration(name)
        if (optional) {
            ref.optional()
        }
    }

    fun choiceArgument(
        name: String,
        vararg options: String,
        optional: Boolean = false,
    ) {
        val ref = choice(name, *options)
        if (optional) {
            ref.optional()
        }
    }

    inline fun <reified E : Enum<E>> enumArgument(
        name: String,
        optional: Boolean = false,
    ) {
        val ref = enum<E>(name)
        if (optional) {
            ref.optional()
        }
    }

    fun <T> customArgument(
        name: String,
        parser: DaisyParser<T>,
        optional: Boolean = false,
    ) {
        val ref = argument(name, parser)
        if (optional) {
            ref.optional()
        }
    }

    fun setDescription(value: String) = apply { description = value }

    fun setUsage(value: String) = apply { usage = value }

    fun setPermission(value: String?) = apply { permission = value }

    fun setAliases(vararg values: String) = apply { aliases = values.toList().toTypedArray() }

    fun setPlayerOnly(value: Boolean) =
        apply {
            playerOnly = value
            if (value) {
                explicitConstraint = SenderConstraint.PLAYER_ONLY
                consoleOnly = false
            } else if (!consoleOnly) {
                explicitConstraint = SenderConstraint.ANY
            }
        }

    fun setCooldown(seconds: Int) =
        apply {
            cooldown = seconds
            cooldownDuration = if (seconds > 0) Duration.ofSeconds(seconds.toLong()) else null
        }

    fun setCooldownMessage(value: String?) = apply { cooldownMessage = value }

    fun setCooldownBypassPermission(value: String?) = apply { cooldownBypassPermission = value }

    fun configure(configure: Consumer<CommandBuilder>) = apply { configure.accept(this) }

    internal fun build(): CommandSpec {
        check(root) { "Only root builders can build a CommandSpec." }
        return CommandSpec(
            name = name,
            description = description,
            aliases = aliases.toList(),
            permission = permission,
            senderConstraint = resolveConstraint(),
            cooldown = resolveCooldown(),
            arguments = compileArguments(),
            children = childBuilders.map { it.buildNode() },
            handler = handler,
            usageOverride = usage.takeIf(String::isNotBlank),
        )
    }

    private fun buildNode(): CommandNodeSpec =
        CommandNodeSpec(
            name = name,
            description = description,
            aliases = aliases.toList(),
            permission = permission,
            senderConstraint = resolveConstraint(),
            cooldown = resolveCooldown(),
            arguments = compileArguments(),
            children = childBuilders.map { it.buildNode() },
            handler = handler,
            usageOverride = usage.takeIf(String::isNotBlank),
        )

    private fun resolveConstraint(): SenderConstraint =
        when {
            playerOnly && consoleOnly -> error("Node '$name' cannot be both player-only and console-only.")
            playerOnly -> SenderConstraint.PLAYER_ONLY
            consoleOnly -> SenderConstraint.CONSOLE_ONLY
            else -> explicitConstraint
        }

    private fun resolveCooldown(): CooldownSpec? {
        val duration =
            cooldownDuration
                ?: if (cooldown > 0) {
                    Duration.ofSeconds(cooldown.toLong())
                } else {
                    null
                }
        return duration?.let {
            CooldownSpec(
                duration = it,
                bypassPermission = cooldownBypassPermission,
                message = cooldownMessage,
            )
        }
    }

    private fun compileArguments(): List<CompiledArgument> =
        arguments.map {
            CompiledArgument(
                slot = it.slot,
                name = it.name,
                parser = it.parser,
                optional = it.optional,
            )
        }

    private fun setHandler(value: HandlerSpec) {
        check(handler == null) { "Node '$name' already has an execution handler." }
        handler = value
    }
}
