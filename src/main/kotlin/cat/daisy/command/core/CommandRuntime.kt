@file:Suppress("unused")

package cat.daisy.command.core

import cat.daisy.command.arguments.BukkitPlatform
import cat.daisy.command.arguments.CompiledArgument
import cat.daisy.command.arguments.DaisyParser
import cat.daisy.command.arguments.DaisyPlatform
import cat.daisy.command.arguments.MutableArgumentDefinition
import cat.daisy.command.arguments.ParseContext
import cat.daisy.command.arguments.ParseResult
import cat.daisy.command.arguments.SuggestContext
import cat.daisy.command.context.AbortExecution
import cat.daisy.command.context.CommandContext
import cat.daisy.command.context.ConsoleCommandContext
import cat.daisy.command.context.PlayerCommandContext
import cat.daisy.command.context.ResolvedArguments
import cat.daisy.command.cooldown.DaisyCooldowns
import cat.daisy.command.text.DaisyText.mm
import org.bukkit.command.CommandSender
import org.bukkit.command.ConsoleCommandSender
import org.bukkit.entity.Player
import java.time.Duration
import java.util.LinkedHashMap
import java.util.logging.Logger
import kotlin.ConsistentCopyVisibility

private val HELP_ALIASES = setOf("help", "?")

enum class SenderConstraint {
    ANY,
    PLAYER_ONLY,
    CONSOLE_ONLY,
}

data class CooldownSpec(
    val duration: Duration,
    val bypassPermission: String? = null,
    val message: String? = null,
)

internal sealed interface HandlerSpec

internal class AnyHandler(
    val block: CommandContext.() -> Unit,
) : HandlerSpec

internal class PlayerHandler(
    val block: PlayerCommandContext.() -> Unit,
) : HandlerSpec

internal class ConsoleHandler(
    val block: ConsoleCommandContext.() -> Unit,
) : HandlerSpec

@ConsistentCopyVisibility
internal data class CommandNodeSpec internal constructor(
    val name: String,
    val description: String,
    val aliases: List<String>,
    val permission: String?,
    val senderConstraint: SenderConstraint,
    val cooldown: CooldownSpec?,
    val arguments: List<CompiledArgument>,
    val children: List<CommandNodeSpec>,
    val handler: HandlerSpec?,
    val usageOverride: String?,
)

class CommandSpec internal constructor(
    val name: String,
    val description: String,
    val aliases: List<String>,
    internal val permission: String?,
    internal val senderConstraint: SenderConstraint,
    internal val cooldown: CooldownSpec?,
    internal val arguments: List<CompiledArgument>,
    internal val children: List<CommandNodeSpec>,
    internal val handler: HandlerSpec?,
    internal val usageOverride: String?,
) {
    internal val compiled: CompiledCommand by lazy(LazyThreadSafetyMode.NONE) {
        CommandCompiler.compile(this)
    }
}

internal data class CommandRuntime(
    val logger: Logger,
    val platform: DaisyPlatform = BukkitPlatform,
)

internal data class CompiledCommand(
    val name: String,
    val description: String,
    val aliases: List<String>,
    val root: CompiledNode,
) {
    fun execute(
        sender: CommandSender,
        label: String,
        args: List<String>,
        runtime: CommandRuntime,
    ) {
        if (!isAccessibleRoot(sender, root)) {
            return
        }

        val resolution = resolveExecutionNode(root, args)
        if (resolution.helpRequested) {
            sendHelp(sender, resolution.node)
            return
        }

        val node = resolution.node
        if (!hasPermission(sender, node)) {
            sender.sendRich("<red>You do not have permission to use this command.")
            return
        }
        when (node.senderConstraint) {
            SenderConstraint.PLAYER_ONLY -> {
                if (sender !is Player) {
                    sender.sendRich("<red>This command can only be used by a player.")
                    return
                }
            }

            SenderConstraint.CONSOLE_ONLY -> {
                if (sender !is ConsoleCommandSender) {
                    sender.sendRich("<red>This command can only be used from the console.")
                    return
                }
            }

            SenderConstraint.ANY -> {
                Unit
            }
        }

        if (resolution.unknownSubcommand != null && node.children.isNotEmpty() && node.arguments.isEmpty()) {
            sender.sendRich("<red>Unknown subcommand '${resolution.unknownSubcommand}'.")
            sendHelp(sender, node)
            return
        }

        if (node.handler == null && node.children.isNotEmpty() && resolution.remainingArgs.isEmpty()) {
            sendHelp(sender, node)
            return
        }

        val parsed = parseArguments(node, resolution.remainingArgs, sender, runtime.platform)
        when (parsed) {
            is ArgumentParse.Success -> {
                val cooldown = node.cooldown
                if (cooldown != null && sender is Player && !bypassesCooldown(sender, cooldown)) {
                    val remaining = DaisyCooldowns.remaining(sender, node.cooldownKey, cooldown.duration)
                    if (!remaining.isZero) {
                        sender.sendRich(renderCooldownMessage(cooldown, remaining))
                        return
                    }
                }

                val context =
                    createContext(
                        sender = sender,
                        label = label,
                        node = node,
                        args = resolution.remainingArgs,
                        resolved = parsed.resolvedArguments,
                        logger = runtime.logger,
                    )

                try {
                    invokeHandler(node.handler, context)
                    if (cooldown != null && sender is Player && !bypassesCooldown(sender, cooldown) && context.wasSuccessful()) {
                        DaisyCooldowns.set(sender, node.cooldownKey)
                    }
                } catch (_: AbortExecution) {
                    return
                } catch (throwable: Throwable) {
                    sender.sendRich("<red>An internal error occurred while executing this command.")
                    runtime.logger.severe("Failed to execute /$label ${resolution.remainingArgs.joinToString(" ")}")
                    runtime.logger.severe(throwable.stackTraceToString())
                }
            }

            is ArgumentParse.Failure -> {
                sender.sendRich("<red>${parsed.message}")
                sender.sendRich("<gray>Usage: <white>${usageFor(node)}")
            }
        }
    }

    fun suggest(
        sender: CommandSender,
        args: List<String>,
        runtime: CommandRuntime,
    ): List<String> {
        if (!isAccessibleRoot(sender, root)) {
            return emptyList()
        }

        if (args.isEmpty()) {
            return rootSuggestions(root, "", sender, runtime.platform)
        }

        var node = root
        var consumed = 0
        val currentIndex = args.lastIndex

        while (consumed < currentIndex) {
            val child = node.childrenByKey[args[consumed].normalized()] ?: break
            if (!canView(sender, child)) {
                return emptyList()
            }
            node = child
            consumed++
        }

        val currentInput = args.last()
        val nodeArgs = args.drop(consumed)
        val childSuggestions = rootSuggestions(node, currentInput, sender, runtime.platform)
        val argumentSuggestions = suggestArguments(node, nodeArgs, sender, runtime.platform)
        return (childSuggestions + argumentSuggestions).distinct()
    }
}

internal data class CompiledNode(
    val name: String,
    val description: String,
    val aliases: List<String>,
    val ownPermission: String?,
    val permissions: List<String>,
    val senderConstraint: SenderConstraint,
    val cooldown: CooldownSpec?,
    val arguments: List<CompiledArgument>,
    val children: List<CompiledNode>,
    val childrenByKey: Map<String, CompiledNode>,
    val handler: HandlerSpec?,
    val usageOverride: String?,
    val pathSegments: List<String>,
) {
    val pathString: String = pathSegments.joinToString(" ")
    val cooldownKey: String = pathString
}

private sealed interface ArgumentParse {
    data class Success(
        val resolvedArguments: ResolvedArguments,
    ) : ArgumentParse

    data class Failure(
        val message: String,
    ) : ArgumentParse
}

private data class ExecutionResolution(
    val node: CompiledNode,
    val remainingArgs: List<String>,
    val unknownSubcommand: String? = null,
    val helpRequested: Boolean = false,
)

private object CommandCompiler {
    fun compile(spec: CommandSpec): CompiledCommand {
        val rootSpec =
            CommandNodeSpec(
                name = spec.name,
                description = spec.description,
                aliases = spec.aliases,
                permission = spec.permission,
                senderConstraint = spec.senderConstraint,
                cooldown = spec.cooldown,
                arguments = spec.arguments,
                children = spec.children,
                handler = spec.handler,
                usageOverride = spec.usageOverride,
            )

        val root = compileNode(rootSpec, emptyList(), SenderConstraint.ANY, emptyList())
        return CompiledCommand(spec.name, spec.description, spec.aliases, root)
    }

    private fun compileNode(
        spec: CommandNodeSpec,
        parentPath: List<String>,
        parentConstraint: SenderConstraint,
        parentPermissions: List<String>,
    ): CompiledNode {
        validateNode(spec)
        val effectiveConstraint = mergeConstraint(parentConstraint, spec.senderConstraint, spec.name)
        val permissions = ArrayList<String>(parentPermissions.size + 1)
        permissions += parentPermissions
        spec.permission?.let { permissions += it }

        val path = parentPath + spec.name
        val childNodes = ArrayList<CompiledNode>(spec.children.size)
        val childrenByKey = LinkedHashMap<String, CompiledNode>()

        for (childSpec in spec.children) {
            val child = compileNode(childSpec, path, effectiveConstraint, permissions)
            childNodes += child
            registerChildKey(childrenByKey, child.name, child, spec.name)
            for (alias in child.aliases) {
                registerChildKey(childrenByKey, alias, child, spec.name)
            }
        }

        return CompiledNode(
            name = spec.name,
            description = spec.description,
            aliases = spec.aliases,
            ownPermission = spec.permission,
            permissions = permissions,
            senderConstraint = effectiveConstraint,
            cooldown = spec.cooldown,
            arguments = spec.arguments,
            children = childNodes,
            childrenByKey = childrenByKey,
            handler = spec.handler,
            usageOverride = spec.usageOverride,
            pathSegments = path,
        )
    }

    private fun validateNode(spec: CommandNodeSpec) {
        require(spec.name.isNotBlank()) { "Command node names cannot be blank." }
        if (spec.cooldown != null) {
            require(!spec.cooldown.duration.isZero && !spec.cooldown.duration.isNegative) {
                "Cooldown for '${spec.name}' must be greater than zero."
            }
        }

        var encounteredOptional = false
        var encounteredGreedy = false
        val argumentNames = HashSet<String>()
        for (argument in spec.arguments) {
            check(argumentNames.add(argument.name.normalized())) {
                "Duplicate argument name '${argument.name}' in '${spec.name}'."
            }
            if (encounteredGreedy) {
                error("Greedy argument '${argument.name}' in '${spec.name}' must be the last argument.")
            }
            if (!argument.optional && encounteredOptional) {
                error("Required argument '${argument.name}' in '${spec.name}' cannot follow an optional argument.")
            }
            if (argument.optional) {
                encounteredOptional = true
            }
            if (argument.parser.greedy) {
                encounteredGreedy = true
            }
        }

        val children = LinkedHashMap<String, String>()
        for (child in spec.children) {
            require(child.name.normalized() !in HELP_ALIASES) {
                "'${child.name}' is reserved by DaisyCommands for help."
            }
            registerAlias(children, child.name, child.name, spec.name)
            for (alias in child.aliases) {
                require(alias.normalized() !in HELP_ALIASES) {
                    "'$alias' is reserved by DaisyCommands for help."
                }
                registerAlias(children, alias, child.name, spec.name)
            }
        }

        require(spec.handler != null || spec.children.isNotEmpty()) {
            "Command node '${spec.name}' must define a handler or at least one subcommand."
        }
    }

    private fun mergeConstraint(
        parent: SenderConstraint,
        child: SenderConstraint,
        name: String,
    ): SenderConstraint =
        when {
            parent == SenderConstraint.ANY -> child
            child == SenderConstraint.ANY -> parent
            parent == child -> parent
            else -> error("Command node '$name' has conflicting sender constraints.")
        }

    private fun registerChildKey(
        childrenByKey: MutableMap<String, CompiledNode>,
        rawKey: String,
        child: CompiledNode,
        parentName: String,
    ) {
        val key = rawKey.normalized()
        require(childrenByKey.putIfAbsent(key, child) == null) {
            "Duplicate child key '$rawKey' in '$parentName'."
        }
    }

    private fun registerAlias(
        registry: MutableMap<String, String>,
        rawKey: String,
        owner: String,
        parentName: String,
    ) {
        val key = rawKey.normalized()
        require(registry.putIfAbsent(key, owner) == null) {
            "Duplicate child key '$rawKey' in '$parentName'."
        }
    }
}

private fun resolveExecutionNode(
    root: CompiledNode,
    args: List<String>,
): ExecutionResolution {
    var node = root
    var index = 0

    while (index < args.size) {
        val token = args[index]
        val normalized = token.normalized()

        if (normalized in HELP_ALIASES && node.children.isNotEmpty() && index == args.lastIndex) {
            return ExecutionResolution(node = node, remainingArgs = emptyList(), helpRequested = true)
        }

        val child = node.childrenByKey[normalized] ?: break
        node = child
        index++
    }

    val remaining = args.drop(index)
    val unknownSubcommand =
        if (remaining.isNotEmpty() && node.children.isNotEmpty() && node.arguments.isEmpty() && node.handler == null) {
            remaining.first()
        } else {
            null
        }

    return ExecutionResolution(node = node, remainingArgs = remaining, unknownSubcommand = unknownSubcommand)
}

private fun parseArguments(
    node: CompiledNode,
    args: List<String>,
    sender: CommandSender,
    platform: DaisyPlatform,
): ArgumentParse {
    val values = arrayOfNulls<Any?>(node.arguments.size)
    val valuesByName = LinkedHashMap<String, Any?>(node.arguments.size)
    var tokenIndex = 0

    for (argument in node.arguments) {
        val rawValue =
            if (argument.parser.greedy) {
                if (tokenIndex < args.size) {
                    args.drop(tokenIndex).joinToString(" ")
                } else {
                    null
                }
            } else {
                args.getOrNull(tokenIndex)
            }

        if (rawValue == null) {
            if (argument.optional) {
                valuesByName[argument.name] = null
                continue
            }
            return ArgumentParse.Failure("Missing argument <${argument.name}>.")
        }

        val parseResult =
            argument.parser.parse(
                rawValue,
                ParseContext(
                    sender = sender,
                    platform = platform,
                    commandPath = node.pathSegments,
                    argumentName = argument.name,
                ),
            )
        when (parseResult) {
            is ParseResult.Success -> {
                values[argument.slot] = parseResult.value
                valuesByName[argument.name] = parseResult.value
            }

            is ParseResult.Failure -> {
                return ArgumentParse.Failure(parseResult.error)
            }
        }

        if (argument.parser.greedy) {
            tokenIndex = args.size
            break
        }

        tokenIndex++
    }

    if (tokenIndex < args.size) {
        return ArgumentParse.Failure("Too many arguments were provided.")
    }

    for (argument in node.arguments) {
        if (!valuesByName.containsKey(argument.name)) {
            valuesByName[argument.name] = null
        }
    }

    return ArgumentParse.Success(ResolvedArguments(values, valuesByName))
}

private fun createContext(
    sender: CommandSender,
    label: String,
    node: CompiledNode,
    args: List<String>,
    resolved: ResolvedArguments,
    logger: Logger,
): CommandContext =
    when (sender) {
        is Player -> {
            PlayerCommandContext(
                player = sender,
                label = label,
                path = node.pathSegments,
                args = args,
                resolvedArguments = resolved,
                logger = logger,
            )
        }

        is ConsoleCommandSender -> {
            ConsoleCommandContext(
                console = sender,
                label = label,
                path = node.pathSegments,
                args = args,
                resolvedArguments = resolved,
                logger = logger,
            )
        }

        else -> {
            CommandContext(sender, label, node.pathSegments, args, resolved, logger)
        }
    }

private fun invokeHandler(
    handler: HandlerSpec?,
    context: CommandContext,
) {
    when (handler) {
        null -> {
            context.fail("Usage: ${context.path.joinToString(" ")}")
        }

        is AnyHandler -> {
            handler.block(context)
        }

        is PlayerHandler -> {
            val playerContext =
                context as? PlayerCommandContext ?: context.fail("This command can only be used by a player.")
            handler.block(playerContext)
        }

        is ConsoleHandler -> {
            val consoleContext =
                context as? ConsoleCommandContext ?: context.fail("This command can only be used from the console.")
            handler.block(consoleContext)
        }
    }
}

private fun suggestArguments(
    node: CompiledNode,
    nodeArgs: List<String>,
    sender: CommandSender,
    platform: DaisyPlatform,
): List<String> {
    if (node.arguments.isEmpty()) {
        return emptyList()
    }

    val currentTokenIndex = if (nodeArgs.isEmpty()) 0 else nodeArgs.lastIndex
    val valuesByName = LinkedHashMap<String, Any?>(node.arguments.size)
    var tokenIndex = 0

    for (argument in node.arguments) {
        if (argument.parser.greedy) {
            val currentInput = if (nodeArgs.isEmpty()) "" else nodeArgs.drop(tokenIndex).joinToString(" ")
            return argument.parser.suggest(
                SuggestContext(
                    sender = sender,
                    platform = platform,
                    commandPath = node.pathSegments,
                    argumentIndex = argument.slot,
                    currentInput = currentInput,
                    previousArguments = valuesByName,
                ),
            )
        }

        if (tokenIndex == currentTokenIndex) {
            val currentInput = nodeArgs.getOrNull(tokenIndex).orEmpty()
            return argument.parser.suggest(
                SuggestContext(
                    sender = sender,
                    platform = platform,
                    commandPath = node.pathSegments,
                    argumentIndex = argument.slot,
                    currentInput = currentInput,
                    previousArguments = valuesByName,
                ),
            )
        }

        val rawValue = nodeArgs.getOrNull(tokenIndex)
        if (rawValue == null) {
            if (argument.optional) {
                valuesByName[argument.name] = null
                continue
            }
            return emptyList()
        }

        val parseResult =
            argument.parser.parse(
                rawValue,
                ParseContext(
                    sender = sender,
                    platform = platform,
                    commandPath = node.pathSegments,
                    argumentName = argument.name,
                ),
            )
        val parsedValue = (parseResult as? ParseResult.Success)?.value ?: return emptyList()
        valuesByName[argument.name] = parsedValue
        tokenIndex++
    }

    return emptyList()
}

private fun rootSuggestions(
    node: CompiledNode,
    currentInput: String,
    sender: CommandSender,
    platform: DaisyPlatform,
): List<String> {
    if (node.children.isEmpty()) {
        return emptyList()
    }

    val suggestions = ArrayList<String>(node.children.size)
    for (child in node.children) {
        if (!canView(sender, child)) {
            continue
        }
        if (child.name.startsWith(currentInput, ignoreCase = true)) {
            suggestions += child.name
        }
    }
    return suggestions
}

private fun sendHelp(
    sender: CommandSender,
    node: CompiledNode,
) {
    sender.sendRich("<gold>/${node.pathString}</gold>${if (node.description.isNotBlank()) " <gray>- ${node.description}" else ""}")

    if (node.handler != null) {
        sender.sendRich("<gray>Usage: <white>${usageFor(node)}")
    }

    if (node.children.isNotEmpty()) {
        for (child in node.children) {
            if (!canView(sender, child)) {
                continue
            }
            sender.sendRich(
                "<yellow>/${child.pathString}</yellow>${if (child.description.isNotBlank()) " <gray>- ${child.description}" else ""}",
            )
        }
    }
}

private fun usageFor(node: CompiledNode): String {
    node.usageOverride?.takeIf(String::isNotBlank)?.let { return it }
    val builder = StringBuilder("/").append(node.pathString)
    for (argument in node.arguments) {
        if (argument.optional) {
            builder.append(" [").append(argument.name)
        } else {
            builder.append(" <").append(argument.name)
        }
        if (argument.parser.greedy) {
            builder.append("...")
        }
        builder.append(if (argument.optional) "]" else ">")
    }
    return builder.toString()
}

private fun canView(
    sender: CommandSender,
    node: CompiledNode,
): Boolean = hasPermission(sender, node) && satisfiesConstraint(sender, node.senderConstraint)

private fun hasPermission(
    sender: CommandSender,
    node: CompiledNode,
): Boolean {
    for (permission in node.permissions) {
        if (!sender.hasPermission(permission)) {
            return false
        }
    }
    return true
}

private fun isAccessibleRoot(
    sender: CommandSender,
    node: CompiledNode,
): Boolean = hasPermission(sender, node) && satisfiesConstraint(sender, node.senderConstraint)

private fun satisfiesConstraint(
    sender: CommandSender,
    constraint: SenderConstraint,
): Boolean =
    when (constraint) {
        SenderConstraint.ANY -> true
        SenderConstraint.PLAYER_ONLY -> sender is Player
        SenderConstraint.CONSOLE_ONLY -> sender is ConsoleCommandSender
    }

private fun renderCooldownMessage(
    cooldown: CooldownSpec,
    remaining: Duration,
): String {
    val formatted = DaisyCooldowns.format(remaining)
    val template = cooldown.message ?: "<red>You must wait <white>{remaining}</white><red> before using this command again."
    return template.replace("{remaining}", formatted)
}

private fun bypassesCooldown(
    sender: Player,
    cooldown: CooldownSpec,
): Boolean = cooldown.bypassPermission?.let(sender::hasPermission) == true

private fun CommandSender.sendRich(message: String) {
    sendMessage(message.mm())
}

private fun String.normalized(): String = lowercase()
