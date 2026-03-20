@file:Suppress("unused")

package cat.daisy.command.core

import cat.daisy.command.arguments.ArgumentKind
import cat.daisy.command.arguments.BukkitPlatform
import cat.daisy.command.arguments.CompiledArgument
import cat.daisy.command.arguments.DaisyPlatform
import cat.daisy.command.arguments.ParseContext
import cat.daisy.command.arguments.ParseResult
import cat.daisy.command.arguments.SuggestContext
import cat.daisy.command.arguments.ValidationContext
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

private val HELP_ALIASES = setOf("help", "?")
private val COMMAND_NAME_PATTERN = Regex("^[a-z0-9][a-z0-9_-]*$")

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

internal class RequirementSpec(
    val message: String?,
    val check: CommandContext.() -> Boolean,
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

internal data class CommandNodeSpec(
    val name: String,
    val description: String,
    val aliases: List<String>,
    val permission: String?,
    val senderConstraint: SenderConstraint,
    val cooldown: CooldownSpec?,
    val arguments: List<CompiledArgument>,
    val requirements: List<RequirementSpec>,
    val children: List<CommandNodeSpec>,
    val handler: HandlerSpec?,
)

class CommandSpec internal constructor(
    val name: String,
    val description: String,
    val aliases: List<String>,
    internal val permission: String?,
    internal val senderConstraint: SenderConstraint,
    internal val cooldown: CooldownSpec?,
    internal val arguments: List<CompiledArgument>,
    internal val requirements: List<RequirementSpec>,
    internal val children: List<CommandNodeSpec>,
    internal val handler: HandlerSpec?,
) {
    internal val compiled: CompiledCommand by lazy(LazyThreadSafetyMode.NONE) {
        CommandCompiler.compile(this)
    }
}

internal data class CommandRuntime(
    val logger: Logger,
    val config: DaisyConfig = DaisyConfig(),
    val platform: DaisyPlatform = BukkitPlatform,
)

internal data class CompiledRequirement(
    val message: String?,
    val check: CommandContext.() -> Boolean,
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
        if (!hasPermission(sender, root.permissions)) {
            sender.sendFramework(runtime.config.messages.noPermission, runtime)
            return
        }
        if (!satisfiesConstraint(sender, root.senderConstraint)) {
            sender.sendFramework(renderConstraintMessage(root.senderConstraint, runtime), runtime)
            return
        }

        val resolution = resolveExecutionNode(root, args)
        if (resolution.helpRequested) {
            sendHelp(sender, resolution.node, runtime)
            return
        }

        if (resolution.unknownSubcommand != null) {
            sender.sendFramework(
                runtime.config.messages.unknownSubcommand
                    .replace("{input}", resolution.unknownSubcommand),
                runtime,
            )
            sendHelp(sender, resolution.node, runtime)
            return
        }

        val node = resolution.node
        if (!hasPermission(sender, node.permissions)) {
            sender.sendFramework(runtime.config.messages.noPermission, runtime)
            return
        }
        if (!satisfiesConstraint(sender, node.senderConstraint)) {
            sender.sendFramework(renderConstraintMessage(node.senderConstraint, runtime), runtime)
            return
        }

        if (node.handler == null && node.children.isNotEmpty() && resolution.remainingArgs.isEmpty()) {
            sendHelp(sender, node, runtime)
            return
        }

        when (val parsed = parseArguments(node, resolution.remainingArgs, sender, runtime.platform)) {
            is ArgumentParse.Failure -> renderArgumentFailure(sender, node, parsed.message, runtime)
            is ArgumentParse.Success -> executeResolvedNode(sender, label, resolution, node, parsed.arguments, runtime)
        }
    }

    fun suggest(
        sender: CommandSender,
        args: List<String>,
        runtime: CommandRuntime,
    ): List<String> {
        if (!hasPermission(sender, root.permissions) || !satisfiesConstraint(sender, root.senderConstraint)) {
            return emptyList()
        }

        if (args.isEmpty()) {
            return distinctSuggestions(
                childSuggestions(root, "", sender) + suggestArguments(root, emptyList(), sender, runtime.platform),
            )
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

        val remaining = args.drop(consumed)
        return distinctSuggestions(
            childSuggestions(node, remaining.lastOrNull().orEmpty(), sender) +
                suggestArguments(node, remaining, sender, runtime.platform),
        )
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
    val positionals: List<CompiledArgument>,
    val options: List<CompiledArgument>,
    val optionByLong: Map<String, CompiledArgument>,
    val optionByShort: Map<String, CompiledArgument>,
    val requirements: List<CompiledRequirement>,
    val children: List<CompiledNode>,
    val childrenByKey: Map<String, CompiledNode>,
    val handler: HandlerSpec?,
    val pathSegments: List<String>,
    val valueCount: Int,
) {
    val pathString: String = pathSegments.joinToString(" ")
    val cooldownKey: String = pathString
}

private sealed interface ArgumentParse {
    data class Success(
        val arguments: ResolvedArguments,
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
                requirements = spec.requirements,
                children = spec.children,
                handler = spec.handler,
            )

        val root = compileNode(rootSpec, emptyList(), SenderConstraint.ANY, emptyList(), emptyList())
        return CompiledCommand(spec.name, spec.description, spec.aliases, root)
    }

    private fun compileNode(
        spec: CommandNodeSpec,
        parentPath: List<String>,
        parentConstraint: SenderConstraint,
        parentPermissions: List<String>,
        parentRequirements: List<CompiledRequirement>,
    ): CompiledNode {
        validateNode(spec)
        val effectiveConstraint = mergeConstraint(parentConstraint, spec.senderConstraint, spec.name)

        val permissions = ArrayList<String>(parentPermissions.size + 1)
        permissions += parentPermissions
        spec.permission?.let(permissions::add)

        val requirements = ArrayList<CompiledRequirement>(parentRequirements.size + spec.requirements.size)
        requirements += parentRequirements
        for (requirement in spec.requirements) {
            requirements += CompiledRequirement(requirement.message, requirement.check)
        }

        val pathSegments = parentPath + spec.name
        val children = ArrayList<CompiledNode>(spec.children.size)
        val childrenByKey = LinkedHashMap<String, CompiledNode>(spec.children.size * 2)
        for (childSpec in spec.children) {
            val child = compileNode(childSpec, pathSegments, effectiveConstraint, permissions, requirements)
            children += child
            registerChildKey(childrenByKey, child.name, child, spec.name)
            for (alias in child.aliases) {
                registerChildKey(childrenByKey, alias, child, spec.name)
            }
        }

        val positionals = ArrayList<CompiledArgument>()
        val options = ArrayList<CompiledArgument>()
        val optionByLong = LinkedHashMap<String, CompiledArgument>()
        val optionByShort = LinkedHashMap<String, CompiledArgument>()
        for (argument in spec.arguments) {
            when (argument.kind) {
                ArgumentKind.POSITIONAL -> {
                    positionals += argument
                }

                ArgumentKind.OPTION,
                ArgumentKind.FLAG,
                -> {
                    options += argument
                    val longName = requireNotNull(argument.longName)
                    require(optionByLong.putIfAbsent(longName.normalized(), argument) == null) {
                        "Duplicate option '--$longName' in '${spec.name}'."
                    }
                    argument.shortName?.let { shortName ->
                        require(optionByShort.putIfAbsent(shortName.normalized(), argument) == null) {
                            "Duplicate option '-$shortName' in '${spec.name}'."
                        }
                    }
                }
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
            positionals = positionals,
            options = options,
            optionByLong = optionByLong,
            optionByShort = optionByShort,
            requirements = requirements,
            children = children,
            childrenByKey = childrenByKey,
            handler = spec.handler,
            pathSegments = pathSegments,
            valueCount = spec.arguments.size,
        )
    }

    private fun validateNode(spec: CommandNodeSpec) {
        require(spec.name.matches(COMMAND_NAME_PATTERN)) {
            "Invalid command node name '${spec.name}'. Use lowercase letters, numbers, dashes, or underscores."
        }
        if (spec.cooldown != null) {
            require(!spec.cooldown.duration.isZero && !spec.cooldown.duration.isNegative) {
                "Cooldown for '${spec.name}' must be greater than zero."
            }
        }

        val argumentNames = HashSet<String>()
        var encounteredOptionalPositional = false
        var encounteredGreedy = false
        for (argument in spec.arguments) {
            check(argumentNames.add(argument.name.normalized())) {
                "Duplicate argument name '${argument.name}' in '${spec.name}'."
            }

            when (argument.kind) {
                ArgumentKind.POSITIONAL -> {
                    if (encounteredGreedy) {
                        error("Greedy argument '${argument.name}' in '${spec.name}' must be the last positional argument.")
                    }
                    if (!argument.optional && !argument.hasDefault && encounteredOptionalPositional) {
                        error("Required argument '${argument.name}' in '${spec.name}' cannot follow an optional or defaulted argument.")
                    }
                    if (argument.optional || argument.hasDefault) {
                        encounteredOptionalPositional = true
                    }
                    if (argument.parser.greedy) {
                        encounteredGreedy = true
                    }
                }

                ArgumentKind.OPTION -> {
                    require(!argument.parser.greedy) {
                        "Option '${argument.name}' in '${spec.name}' cannot use a greedy parser."
                    }
                    validateOptionNames(argument, spec.name)
                }

                ArgumentKind.FLAG -> {
                    validateOptionNames(argument, spec.name)
                }
            }
        }

        val childKeys = LinkedHashMap<String, String>()
        for (child in spec.children) {
            require(child.name.normalized() !in HELP_ALIASES) {
                "'${child.name}' is reserved by DaisyCommands for help."
            }
            registerAlias(childKeys, child.name, child.name, spec.name)
            for (alias in child.aliases) {
                require(alias.normalized() !in HELP_ALIASES) {
                    "'$alias' is reserved by DaisyCommands for help."
                }
                require(alias.matches(COMMAND_NAME_PATTERN)) {
                    "Invalid alias '$alias' in '${spec.name}'."
                }
                registerAlias(childKeys, alias, child.name, spec.name)
            }
        }

        require(spec.handler != null || spec.children.isNotEmpty()) {
            "Command node '${spec.name}' must define a handler or at least one subcommand."
        }
    }

    private fun validateOptionNames(
        argument: CompiledArgument,
        nodeName: String,
    ) {
        val longName = requireNotNull(argument.longName)
        require(longName.matches(COMMAND_NAME_PATTERN)) {
            "Invalid option name '$longName' in '$nodeName'."
        }
        argument.shortName?.let { shortName ->
            require(shortName.length == 1 && shortName[0].isLetterOrDigit()) {
                "Invalid short option name '$shortName' in '$nodeName'."
            }
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

private fun executeResolvedNode(
    sender: CommandSender,
    label: String,
    resolution: ExecutionResolution,
    node: CompiledNode,
    arguments: ResolvedArguments,
    runtime: CommandRuntime,
) {
    val context = createContext(sender, label, node, resolution.remainingArgs, arguments, runtime)
    for (requirement in node.requirements) {
        val passes =
            try {
                requirement.check(context)
            } catch (_: AbortExecution) {
                return
            }
        if (!passes) {
            context.sendFramework(requirement.message ?: runtime.config.messages.invalidState)
            return
        }
    }

    val cooldown = node.cooldown
    if (cooldown != null && sender is Player && !bypassesCooldown(sender, cooldown)) {
        val remaining = DaisyCooldowns.remaining(sender, node.cooldownKey, cooldown.duration)
        if (!remaining.isZero) {
            sender.sendFramework(renderCooldownMessage(node, cooldown, remaining, runtime), runtime)
            return
        }
    }

    try {
        invokeHandler(node.handler, context)
        if (cooldown != null && sender is Player && !bypassesCooldown(sender, cooldown) && context.wasSuccessful()) {
            DaisyCooldowns.set(sender, node.cooldownKey)
        }
    } catch (_: AbortExecution) {
        return
    } catch (throwable: Throwable) {
        sender.sendFramework(runtime.config.messages.exception, runtime)
        runtime.logger.severe("Failed to execute /$label ${resolution.remainingArgs.joinToString(" ")}")
        runtime.logger.severe(throwable.stackTraceToString())
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
        if (normalized in HELP_ALIASES && index == args.lastIndex) {
            return ExecutionResolution(node = node, remainingArgs = emptyList(), helpRequested = true)
        }

        val child = node.childrenByKey[normalized] ?: break
        node = child
        index++
    }

    val remaining = args.drop(index)
    val unknownSubcommand =
        if (remaining.isNotEmpty() && node.children.isNotEmpty() && node.positionals.isEmpty() && node.handler == null) {
            remaining.first()
        } else {
            null
        }

    return ExecutionResolution(node, remaining, unknownSubcommand, helpRequested = false)
}

private fun parseArguments(
    node: CompiledNode,
    args: List<String>,
    sender: CommandSender,
    platform: DaisyPlatform,
): ArgumentParse {
    if (node.valueCount == 0) {
        if (args.isNotEmpty()) {
            return ArgumentParse.Failure("Too many arguments were provided.")
        }
        return ArgumentParse.Success(ResolvedArguments(emptyArray(), emptyMap()))
    }

    val values = arrayOfNulls<Any?>(node.valueCount)
    val valuesByName = LinkedHashMap<String, Any?>(node.valueCount)
    val positionalTokens = ArrayList<String>(args.size)
    val seenOptions = HashSet<String>(node.options.size)
    var index = 0
    var optionParsing = true

    while (index < args.size) {
        val token = args[index]
        if (optionParsing && token == "--") {
            optionParsing = false
            index++
            continue
        }

        if (optionParsing && token.startsWith("--") && token.length > 2) {
            val rawName = token.substring(2)
            val option =
                node.optionByLong[rawName.normalized()]
                    ?: return ArgumentParse.Failure("Unknown option '--$rawName'.")
            if (!seenOptions.add(option.name.normalized())) {
                return ArgumentParse.Failure("Option '--${option.longName}' may only be provided once.")
            }
            if (option.kind == ArgumentKind.FLAG) {
                values[option.slot] = true
                valuesByName[option.name] = true
                index++
                continue
            }

            val rawValue =
                args.getOrNull(index + 1)
                    ?: return ArgumentParse.Failure("Option '--${option.longName}' requires a value.")
            if (rawValue == "--") {
                return ArgumentParse.Failure("Option '--${option.longName}' requires a value.")
            }
            when (val parsed = parseValue(option, rawValue, sender, platform, node.pathSegments, valuesByName)) {
                is ParseValueResult.Failure -> {
                    return ArgumentParse.Failure(parsed.message)
                }

                is ParseValueResult.Success -> {
                    values[option.slot] = parsed.value
                    valuesByName[option.name] = parsed.value
                }
            }
            index += 2
            continue
        }

        if (optionParsing && token.startsWith("-") && token.length == 2) {
            val rawName = token.substring(1)
            val option = node.optionByShort[rawName.normalized()]
            if (option != null) {
                if (!seenOptions.add(option.name.normalized())) {
                    return ArgumentParse.Failure("Option '--${option.longName}' may only be provided once.")
                }
                if (option.kind == ArgumentKind.FLAG) {
                    values[option.slot] = true
                    valuesByName[option.name] = true
                    index++
                    continue
                }

                val rawValue =
                    args.getOrNull(index + 1)
                        ?: return ArgumentParse.Failure("Option '-${option.shortName}' requires a value.")
                if (rawValue == "--") {
                    return ArgumentParse.Failure("Option '-${option.shortName}' requires a value.")
                }
                when (val parsed = parseValue(option, rawValue, sender, platform, node.pathSegments, valuesByName)) {
                    is ParseValueResult.Failure -> {
                        return ArgumentParse.Failure(parsed.message)
                    }

                    is ParseValueResult.Success -> {
                        values[option.slot] = parsed.value
                        valuesByName[option.name] = parsed.value
                    }
                }
                index += 2
                continue
            }
        }

        positionalTokens += token
        index++
    }

    for (option in node.options) {
        if (valuesByName.containsKey(option.name)) {
            continue
        }
        when {
            option.kind == ArgumentKind.FLAG -> {
                values[option.slot] = false
                valuesByName[option.name] = false
            }

            option.hasDefault -> {
                values[option.slot] = option.defaultValue
                valuesByName[option.name] = option.defaultValue
            }

            option.optional -> {
                valuesByName[option.name] = null
            }

            else -> {
                return ArgumentParse.Failure("Missing option '--${option.longName}'.")
            }
        }
    }

    var positionalIndex = 0
    var tokenIndex = 0
    while (positionalIndex < node.positionals.size) {
        val argument = node.positionals[positionalIndex]
        val rawValue =
            if (argument.parser.greedy) {
                if (tokenIndex < positionalTokens.size) {
                    positionalTokens.drop(tokenIndex).joinToString(" ")
                } else {
                    null
                }
            } else {
                positionalTokens.getOrNull(tokenIndex)
            }

        if (rawValue == null) {
            when {
                argument.hasDefault -> {
                    values[argument.slot] = argument.defaultValue
                    valuesByName[argument.name] = argument.defaultValue
                }

                argument.optional -> {
                    valuesByName[argument.name] = null
                }

                else -> {
                    return ArgumentParse.Failure("Missing argument <${argument.name}>.")
                }
            }
            positionalIndex++
            continue
        }

        when (val parsed = parseValue(argument, rawValue, sender, platform, node.pathSegments, valuesByName)) {
            is ParseValueResult.Failure -> {
                return ArgumentParse.Failure(parsed.message)
            }

            is ParseValueResult.Success -> {
                values[argument.slot] = parsed.value
                valuesByName[argument.name] = parsed.value
            }
        }

        if (argument.parser.greedy) {
            tokenIndex = positionalTokens.size
            positionalIndex++
            break
        }

        tokenIndex++
        positionalIndex++
    }

    if (tokenIndex < positionalTokens.size) {
        return ArgumentParse.Failure("Too many arguments were provided.")
    }

    for (argument in node.positionals) {
        if (!valuesByName.containsKey(argument.name)) {
            valuesByName[argument.name] = null
        }
    }

    return ArgumentParse.Success(ResolvedArguments(values, valuesByName))
}

private sealed interface ParseValueResult {
    data class Success(
        val value: Any?,
    ) : ParseValueResult

    data class Failure(
        val message: String,
    ) : ParseValueResult
}

private fun parseValue(
    argument: CompiledArgument,
    rawValue: String,
    sender: CommandSender,
    platform: DaisyPlatform,
    pathSegments: List<String>,
    previousArguments: Map<String, Any?>,
): ParseValueResult {
    val parsed =
        argument.parser.parse(
            rawValue,
            ParseContext(
                sender = sender,
                platform = platform,
                commandPath = pathSegments,
                argumentName = argument.name,
                previousArguments = previousArguments,
            ),
        )

    val value =
        when (parsed) {
            is ParseResult.Failure -> return ParseValueResult.Failure(parsed.error)
            is ParseResult.Success -> parsed.value
        }

    val validator = argument.validator
    if (validator != null) {
        val passes =
            validator(
                ValidationContext(
                    sender = sender,
                    platform = platform,
                    commandPath = pathSegments,
                    argumentName = argument.name,
                    value = value,
                    previousArguments = previousArguments,
                ),
            )
        if (!passes) {
            return ParseValueResult.Failure(argument.validatorMessage ?: "Invalid value for <${argument.name}>.")
        }
    }

    return ParseValueResult.Success(value)
}

private fun createContext(
    sender: CommandSender,
    label: String,
    node: CompiledNode,
    args: List<String>,
    arguments: ResolvedArguments,
    runtime: CommandRuntime,
): CommandContext =
    when (sender) {
        is Player -> PlayerCommandContext(sender, label, node.pathSegments, args, arguments, runtime)
        is ConsoleCommandSender -> ConsoleCommandContext(sender, label, node.pathSegments, args, arguments, runtime)
        else -> CommandContext(sender, label, node.pathSegments, args, arguments, runtime)
    }

private fun invokeHandler(
    handler: HandlerSpec?,
    context: CommandContext,
) {
    when (handler) {
        null -> context.fail("Usage: /${context.path.joinToString(" ")}")
        is AnyHandler -> handler.block(context)
        is PlayerHandler -> handler.block(context as PlayerCommandContext)
        is ConsoleHandler -> handler.block(context as ConsoleCommandContext)
    }
}

private fun suggestArguments(
    node: CompiledNode,
    nodeArgs: List<String>,
    sender: CommandSender,
    platform: DaisyPlatform,
): List<String> {
    val currentInput = nodeArgs.lastOrNull().orEmpty()
    val previousTokens = if (nodeArgs.isEmpty()) emptyList() else nodeArgs.dropLast(1)
    val state = analyzeSuggestionState(node, previousTokens, sender, platform) ?: return emptyList()

    state.pendingOption?.let {
        return suggestForArgument(it, currentInput, sender, platform, node.pathSegments, state.valuesByName)
    }

    if (state.optionParsing && currentInput.startsWith("-")) {
        return availableOptionSuggestions(node, currentInput, state.seenOptionNames)
    }

    val nextArgument = node.positionals.getOrNull(state.positionalIndex)
    val positionalSuggestions =
        if (nextArgument != null) {
            suggestForArgument(nextArgument, currentInput, sender, platform, node.pathSegments, state.valuesByName)
        } else {
            emptyList()
        }

    if (!state.optionParsing || (currentInput.isNotEmpty() && !currentInput.startsWith("-"))) {
        return positionalSuggestions
    }

    return positionalSuggestions + availableOptionSuggestions(node, currentInput, state.seenOptionNames)
}

private data class SuggestionState(
    val valuesByName: LinkedHashMap<String, Any?>,
    val seenOptionNames: HashSet<String>,
    val positionalIndex: Int,
    val optionParsing: Boolean,
    val pendingOption: CompiledArgument?,
)

private fun analyzeSuggestionState(
    node: CompiledNode,
    tokens: List<String>,
    sender: CommandSender,
    platform: DaisyPlatform,
): SuggestionState? {
    val valuesByName = LinkedHashMap<String, Any?>()
    val seenOptionNames = HashSet<String>()
    var positionalIndex = 0
    var optionParsing = true
    var pendingOption: CompiledArgument? = null
    var index = 0

    while (index < tokens.size) {
        val token = tokens[index]
        if (pendingOption != null) {
            when (val parsed = parseValue(pendingOption, token, sender, platform, node.pathSegments, valuesByName)) {
                is ParseValueResult.Failure -> {
                    return null
                }

                is ParseValueResult.Success -> {
                    valuesByName[pendingOption.name] = parsed.value
                    pendingOption = null
                    index++
                    continue
                }
            }
        }

        if (optionParsing && token == "--") {
            optionParsing = false
            index++
            continue
        }

        if (optionParsing && token.startsWith("--") && token.length > 2) {
            val option = node.optionByLong[token.substring(2).normalized()] ?: return null
            if (!seenOptionNames.add(option.name.normalized())) {
                return null
            }
            if (option.kind == ArgumentKind.FLAG) {
                valuesByName[option.name] = true
            } else {
                pendingOption = option
            }
            index++
            continue
        }

        if (optionParsing && token.startsWith("-") && token.length == 2) {
            val option = node.optionByShort[token.substring(1).normalized()]
            if (option != null) {
                if (!seenOptionNames.add(option.name.normalized())) {
                    return null
                }
                if (option.kind == ArgumentKind.FLAG) {
                    valuesByName[option.name] = true
                } else {
                    pendingOption = option
                }
                index++
                continue
            }
        }

        val positional = node.positionals.getOrNull(positionalIndex) ?: return null
        if (positional.parser.greedy) {
            val rawValue = tokens.drop(index).joinToString(" ")
            when (val parsed = parseValue(positional, rawValue, sender, platform, node.pathSegments, valuesByName)) {
                is ParseValueResult.Failure -> return null
                is ParseValueResult.Success -> valuesByName[positional.name] = parsed.value
            }
            positionalIndex++
            index = tokens.size
            continue
        }

        when (val parsed = parseValue(positional, token, sender, platform, node.pathSegments, valuesByName)) {
            is ParseValueResult.Failure -> return null
            is ParseValueResult.Success -> valuesByName[positional.name] = parsed.value
        }
        positionalIndex++
        index++
    }

    return SuggestionState(valuesByName, seenOptionNames, positionalIndex, optionParsing, pendingOption)
}

private fun suggestForArgument(
    argument: CompiledArgument,
    currentInput: String,
    sender: CommandSender,
    platform: DaisyPlatform,
    pathSegments: List<String>,
    previousArguments: Map<String, Any?>,
): List<String> {
    val context =
        SuggestContext(
            sender = sender,
            platform = platform,
            commandPath = pathSegments,
            argumentName = argument.name,
            currentInput = currentInput,
            previousArguments = previousArguments,
        )

    val suggestions = argument.suggestions?.invoke(context)?.toList() ?: argument.parser.suggest(context)
    return filterByPrefix(suggestions, currentInput)
}

private fun availableOptionSuggestions(
    node: CompiledNode,
    currentInput: String,
    seenOptionNames: Set<String>,
): List<String> {
    val suggestions = ArrayList<String>()
    for (option in node.options) {
        if (option.name.normalized() in seenOptionNames) {
            continue
        }
        val longName = "--${option.longName}"
        if (longName.startsWith(currentInput, ignoreCase = true)) {
            suggestions += longName
        }
        option.shortName?.let { shortName ->
            val shortForm = "-$shortName"
            if (shortForm.startsWith(currentInput, ignoreCase = true)) {
                suggestions += shortForm
            }
        }
    }
    return suggestions
}

private fun childSuggestions(
    node: CompiledNode,
    currentInput: String,
    sender: CommandSender,
): List<String> {
    if (node.children.isEmpty()) {
        return emptyList()
    }

    val suggestions = ArrayList<String>()
    for (child in node.children) {
        if (!canView(sender, child)) {
            continue
        }
        if (child.name.startsWith(currentInput, ignoreCase = true)) {
            suggestions += child.name
        }
        for (alias in child.aliases) {
            if (alias.startsWith(currentInput, ignoreCase = true)) {
                suggestions += alias
            }
        }
    }
    return suggestions
}

private fun sendHelp(
    sender: CommandSender,
    node: CompiledNode,
    runtime: CommandRuntime,
) {
    sender.sendRich(defaultHelpHeader(node, runtime), runtime)
    sender.sendRich(
        runtime.config.messages.usageLabel
            .replace("{usage}", usageFor(node)),
        runtime,
    )

    for (child in node.children) {
        if (!canView(sender, child)) {
            continue
        }
        val rendered =
            runtime.config.messages.helpEntryRenderer?.render(
                DaisyHelpEntryRenderContext(
                    parentPath = node.pathString,
                    childPath = child.pathString,
                    description = child.description,
                ),
                runtime.config.theme,
            ) ?: defaultHelpEntry(child, runtime)
        sender.sendRich(rendered, runtime)
    }

    runtime.config.messages.helpFooter?.takeIf(String::isNotBlank)?.let { footer ->
        sender.sendRich(footer, runtime)
    }
}

private fun usageFor(node: CompiledNode): String {
    val builder = StringBuilder("/").append(node.pathString)
    for (option in node.options) {
        builder.append(" [--").append(option.longName)
        if (option.kind == ArgumentKind.OPTION) {
            builder.append(" <").append(option.name).append(">")
        }
        builder.append(']')
    }
    for (argument in node.positionals) {
        val opening = if (argument.optional || argument.hasDefault) " [" else " <"
        val closing = if (argument.optional || argument.hasDefault) "]" else ">"
        builder.append(opening).append(argument.name)
        if (argument.parser.greedy) {
            builder.append("...")
        }
        builder.append(closing)
    }
    return builder.toString()
}

private fun defaultHelpHeader(
    node: CompiledNode,
    runtime: CommandRuntime,
): String {
    val theme = runtime.config.theme
    val descriptionSuffix =
        if (node.description.isBlank()) {
            ""
        } else {
            " <${theme.descriptionColor}>- ${node.description}</${theme.descriptionColor}>"
        }
    return "<${theme.commandColor}>/${node.pathString}</${theme.commandColor}>$descriptionSuffix"
}

private fun defaultHelpEntry(
    child: CompiledNode,
    runtime: CommandRuntime,
): String {
    val theme = runtime.config.theme
    val descriptionSuffix =
        if (child.description.isBlank()) {
            ""
        } else {
            " <${theme.descriptionColor}>- ${child.description}</${theme.descriptionColor}>"
        }
    return "<${theme.accentColor}>/${child.pathString}</${theme.accentColor}>$descriptionSuffix"
}

private fun renderArgumentFailure(
    sender: CommandSender,
    node: CompiledNode,
    message: String,
    runtime: CommandRuntime,
) {
    val rendered =
        runtime.config.messages.argumentErrorRenderer?.render(
            DaisyArgumentErrorRenderContext(
                usage = usageFor(node),
                message = message,
            ),
            runtime.config.theme,
        ) ?: runtime.config.messages.invalidArgument
            .replace("{message}", message)
    sender.sendRich(rendered, runtime)
    sender.sendRich(
        runtime.config.messages.usageLabel
            .replace("{usage}", usageFor(node)),
        runtime,
    )
}

private fun renderCooldownMessage(
    node: CompiledNode,
    cooldown: CooldownSpec,
    remaining: Duration,
    runtime: CommandRuntime,
): String {
    cooldown.message?.let { template ->
        return template.replace("{remaining}", DaisyCooldowns.format(remaining))
    }

    runtime.config.messages.cooldownRenderer?.let { renderer ->
        return renderer.render(
            DaisyCooldownRenderContext(
                commandPath = node.pathString,
                remaining = remaining,
                formattedRemaining = DaisyCooldowns.format(remaining),
            ),
            runtime.config.theme,
        )
    }

    return "<${runtime.config.theme.errorColor}>You must wait <${runtime.config.theme.accentColor}>${DaisyCooldowns.format(
        remaining,
    )}</${runtime.config.theme.accentColor}><${runtime.config.theme.errorColor}> before using this command again."
}

private fun renderConstraintMessage(
    constraint: SenderConstraint,
    runtime: CommandRuntime,
): String =
    when (constraint) {
        SenderConstraint.ANY -> runtime.config.messages.invalidState
        SenderConstraint.PLAYER_ONLY -> runtime.config.messages.playerOnly
        SenderConstraint.CONSOLE_ONLY -> runtime.config.messages.consoleOnly
    }

private fun canView(
    sender: CommandSender,
    node: CompiledNode,
): Boolean = hasPermission(sender, node.permissions) && satisfiesConstraint(sender, node.senderConstraint)

private fun hasPermission(
    sender: CommandSender,
    permissions: List<String>,
): Boolean {
    for (permission in permissions) {
        if (!sender.hasPermission(permission)) {
            return false
        }
    }
    return true
}

private fun satisfiesConstraint(
    sender: CommandSender,
    constraint: SenderConstraint,
): Boolean =
    when (constraint) {
        SenderConstraint.ANY -> true
        SenderConstraint.PLAYER_ONLY -> sender is Player
        SenderConstraint.CONSOLE_ONLY -> sender is ConsoleCommandSender
    }

private fun bypassesCooldown(
    sender: Player,
    cooldown: CooldownSpec,
): Boolean = cooldown.bypassPermission?.let(sender::hasPermission) == true

private fun distinctSuggestions(values: List<String>): List<String> {
    if (values.isEmpty()) {
        return emptyList()
    }

    val unique = LinkedHashMap<String, String>(values.size)
    for (value in values) {
        unique.putIfAbsent(value.lowercase(), value)
    }
    return unique.values.toList()
}

private fun filterByPrefix(
    values: List<String>,
    currentInput: String,
): List<String> {
    if (currentInput.isEmpty()) {
        return distinctSuggestions(values)
    }

    val filtered = ArrayList<String>(values.size)
    for (value in values) {
        if (value.startsWith(currentInput, ignoreCase = true)) {
            filtered += value
        }
    }
    return distinctSuggestions(filtered)
}

private fun CommandSender.sendFramework(
    message: String,
    runtime: CommandRuntime,
) {
    sendRich(message, runtime)
}

private fun CommandSender.sendRich(
    message: String,
    runtime: CommandRuntime,
) {
    sendMessage((runtime.config.messages.prefix + message).mm())
}

private fun String.normalized(): String = lowercase()
