package cat.daisy.command.core

import cat.daisy.command.arguments.DaisyParser
import cat.daisy.command.arguments.DaisyPlatform
import cat.daisy.command.arguments.ParseContext
import cat.daisy.command.arguments.ParseResult
import cat.daisy.command.arguments.SuggestContext
import cat.daisy.command.cooldown.DaisyCooldowns
import cat.daisy.command.dsl.command
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.Duration
import java.util.UUID
import java.util.logging.Logger

class DaisyCommandsTest {
    private val plainSerializer = PlainTextComponentSerializer.plainText()

    @AfterEach
    fun tearDown() {
        DaisyCooldowns.clearAll()
        messageStore.clear()
    }

    @Test
    fun `typed refs resolve positionals flags and options`() {
        val alice = player("Alice")
        val bob = player("Bob")
        val runtime = runtime(players = listOf(alice, bob))

        var invited = ""
        var silentInvite = false
        var expirySeconds = 0L

        val spec =
            command("island") {
                sub("invite") {
                    val target = player("target")
                    val silent = flag("silent", "s")
                    val expires = durationOption("expires", "e").default(Duration.ofMinutes(5))

                    executePlayer {
                        invited = target().name
                        silentInvite = silent()
                        expirySeconds = expires().seconds
                    }
                }
            }

        spec.compiled.execute(alice, "island", listOf("invite", "--expires", "30s", "-s", "Bob"), runtime)

        assertEquals("Bob", invited)
        assertTrue(silentInvite)
        assertEquals(30L, expirySeconds)
    }

    @Test
    fun `options can appear after positionals and sentinel stops option parsing`() {
        val alice = player("Alice")
        val runtime = runtime(players = listOf(alice))

        var rawMessage = ""
        var announce = false

        val spec =
            command("mail") {
                val target = string("target")
                val message = text("message")
                val broadcast = flag("broadcast", "b")

                executePlayer {
                    rawMessage = "${target()}:${message()}"
                    announce = broadcast()
                }
            }

        spec.compiled.execute(alice, "mail", listOf("Bob", "--", "--broadcast", "hello", "there", "-b"), runtime)
        assertEquals("Bob:--broadcast hello there -b", rawMessage)
        assertFalse(announce)

        spec.compiled.execute(alice, "mail", listOf("Bob", "hello", "--broadcast"), runtime)
        assertTrue(announce)
    }

    @Test
    fun `requirements are inherited and stop execution with custom message`() {
        val alice = player("Alice")
        val runtime = runtime(players = listOf(alice))
        var executed = false

        val spec =
            command("island") {
                playerOnly()
                requires("Own an island first.") { false }

                sub("home") {
                    executePlayer {
                        executed = true
                    }
                }
            }

        spec.compiled.execute(alice, "island", listOf("home"), runtime)

        assertFalse(executed)
        assertTrue(sentMessages(alice).any { it.contains("Own an island first.") })
    }

    @Test
    fun `help output hides children without permission and includes usage`() {
        val sender = sender("Console")
        val runtime = runtime()

        val spec =
            command("team") {
                description("Team management")

                sub("open") {
                    description("Open to everyone")
                    execute { }
                }

                sub("secret") {
                    description("Hidden command")
                    permission("team.secret")
                    execute { }
                }
            }

        spec.compiled.execute(sender, "team", emptyList(), runtime)

        val messages = sentMessages(sender)
        assertTrue(messages.any { it.contains("/team") })
        assertTrue(messages.any { it.contains("Usage: /team") })
        assertTrue(messages.any { it.contains("/team open") })
        assertFalse(messages.any { it.contains("/team secret") })
    }

    @Test
    fun `cooldown only applies after successful execution`() {
        val player = player("Alice")
        val runtime = runtime(players = listOf(player))

        var failedExecutions = 0
        val failing =
            command("heal") {
                cooldown(Duration.ofSeconds(30))
                executePlayer {
                    failedExecutions++
                    fail("Nope")
                }
            }

        failing.compiled.execute(player, "heal", emptyList(), runtime)
        failing.compiled.execute(player, "heal", emptyList(), runtime)
        assertEquals(2, failedExecutions)

        var successfulExecutions = 0
        val successful =
            command("warp") {
                cooldown(Duration.ofSeconds(30))
                executePlayer {
                    successfulExecutions++
                }
            }

        successful.compiled.execute(player, "warp", emptyList(), runtime)
        successful.compiled.execute(player, "warp", emptyList(), runtime)

        assertEquals(1, successfulExecutions)
        assertTrue(sentMessages(player).any { it.contains("wait", ignoreCase = true) })
    }

    @Test
    fun `suggestions include aliases option names and option values`() {
        val alice = player("Alice")
        val bob = player("Bob")
        val runtime = runtime(players = listOf(alice, bob))

        val spec =
            command("island") {
                sub("invite") {
                    aliases("add")
                    val target = player("target")
                    durationOption("expires", "e")
                    executePlayer {
                        target().name
                    }
                }
            }

        assertTrue(spec.compiled.suggest(alice, listOf("a"), runtime).contains("add"))
        assertTrue(spec.compiled.suggest(alice, listOf("invite", "--e"), runtime).contains("--expires"))
        assertTrue(spec.compiled.suggest(alice, listOf("invite", "--expires", ""), runtime).contains("30m"))
        assertTrue(spec.compiled.suggest(alice, listOf("invite", ""), runtime).contains("Alice"))
    }

    @Test
    fun `custom parser suggestions can use previous parsed arguments`() {
        val player = player("Alice")
        val runtime = runtime(players = listOf(player))

        val dynamicParser =
            object : DaisyParser<String> {
                override val displayName: String = "name"

                override fun parse(
                    input: String,
                    context: ParseContext,
                ): ParseResult<String> = ParseResult.success(input)

                override fun suggest(context: SuggestContext): List<String> =
                    when (context.previousArguments["scope"]) {
                        "public" -> listOf("spawn", "market")
                        "private" -> listOf("vault", "chest")
                        else -> emptyList()
                    }
            }

        val spec =
            command("warp") {
                sub("set") {
                    choice("scope", "public", "private")
                    argument("name", dynamicParser)
                    executePlayer { }
                }
            }

        assertEquals(listOf("spawn", "market"), spec.compiled.suggest(player, listOf("set", "public", ""), runtime))
    }

    @Test
    fun `custom message hooks are applied`() {
        val player = player("Alice")
        val runtime =
            runtime(
                players = listOf(player),
                config =
                    DaisyConfig(
                        messages =
                            DaisyMessages().apply {
                                prefix = ""
                                argumentErrorRenderer =
                                    DaisyArgumentErrorRenderer { context, _ ->
                                        "Custom error: ${context.message}"
                                    }
                            },
                    ),
            )

        val spec =
            command("coins") {
                int("amount")
                executePlayer { }
            }

        spec.compiled.execute(player, "coins", listOf("oops"), runtime)
        assertTrue(sentMessages(player).any { it.contains("Custom error:") })
    }

    @Test
    fun `paper adapter delegates execution and suggestions`() {
        val alice = player("Alice")
        val bob = player("Bob")
        val runtime = runtime(players = listOf(alice, bob))

        var targetName = ""
        val spec =
            command("island") {
                sub("invite") {
                    val target = player("target")
                    executePlayer {
                        targetName = target().name
                    }
                }
            }

        val adapter = PaperCommandAdapter(spec.compiled, runtime)
        val stack = mock(io.papermc.paper.command.brigadier.CommandSourceStack::class.java)
        `when`(stack.sender).thenReturn(alice)

        adapter.execute(stack, arrayOf("invite", "Bob"))
        val suggestions = adapter.suggest(stack, arrayOf("inv"))

        assertEquals("Bob", targetName)
        assertTrue(suggestions.contains("invite"))
    }

    @Test
    fun `invalid command structures fail fast`() {
        val duplicateAlias =
            command("root") {
                sub("create") {
                    execute { }
                }
                sub("make") {
                    aliases("create")
                    execute { }
                }
            }

        assertThrows<IllegalArgumentException> {
            duplicateAlias.compiled
        }

        val invalidArgs =
            command("bad") {
                text("message").optional()
                string("required")
                execute { }
            }

        assertThrows<IllegalStateException> {
            invalidArgs.compiled
        }

        val invalidOptions =
            command("opt") {
                stringOption("reason", "r")
                stringOption("rename", "r")
                execute { }
            }

        assertThrows<IllegalArgumentException> {
            invalidOptions.compiled
        }
    }

    @Test
    fun `readme style commands compile`() {
        val spec =
            command("island") {
                description("Island management")
                aliases("is")

                sub("create") {
                    playerOnly()
                    executePlayer {
                        reply("Island created for ${player.name}.")
                    }
                }

                sub("invite") {
                    permission("island.invite")
                    val target = player("target")
                    val silent = flag("silent", "s")
                    val note = textOption("note").optional()

                    executePlayer {
                        if (silent()) {
                            reply("Silent invite for ${target().name}: ${note() ?: "No note"}")
                        }
                    }
                }
            }

        assertEquals("island", spec.compiled.name)
        assertEquals(listOf("is"), spec.aliases)
    }

    private fun runtime(
        players: List<Player> = emptyList(),
        worlds: List<World> = emptyList(),
        offlinePlayers: List<OfflinePlayer> = emptyList(),
        config: DaisyConfig = DaisyConfig(messages = DaisyMessages().apply { prefix = "" }),
    ): CommandRuntime =
        CommandRuntime(
            logger = Logger.getLogger("DaisyCommandsTest"),
            config = config,
            platform =
                object : DaisyPlatform {
                    private val playerMap = players.associateBy { it.name.lowercase() }
                    private val worldMap = worlds.associateBy { it.name.lowercase() }
                    private val offlineMap = offlinePlayers.associateBy { (it.name ?: "").lowercase() }

                    override fun findPlayer(name: String): Player? = playerMap[name.lowercase()]

                    override fun onlinePlayers(): Collection<Player> = players

                    override fun findOfflinePlayer(name: String): OfflinePlayer? = offlineMap[name.lowercase()]

                    override fun findWorld(name: String): World? = worldMap[name.lowercase()]

                    override fun worlds(): Collection<World> = worlds
                },
        )

    private fun player(
        name: String,
        permissions: Set<String> = emptySet(),
    ): Player {
        val player = mock(Player::class.java)
        val messages = mutableListOf<String>()

        `when`(player.name).thenReturn(name)
        `when`(player.uniqueId).thenReturn(UUID.nameUUIDFromBytes(name.toByteArray()))
        `when`(player.hasPermission(any(String::class.java))).thenAnswer { invocation ->
            permissions.contains(invocation.getArgument(0))
        }

        doAnswer { invocation ->
            messages += plainSerializer.serialize(invocation.getArgument<Component>(0))
            null
        }.`when`(player).sendMessage(any(Component::class.java))

        messageStore[player] = messages
        return player
    }

    private fun sender(
        name: String,
        permissions: Set<String> = emptySet(),
    ): CommandSender {
        val sender = mock(CommandSender::class.java)
        val messages = mutableListOf<String>()

        `when`(sender.name).thenReturn(name)
        `when`(sender.hasPermission(any(String::class.java))).thenAnswer { invocation ->
            permissions.contains(invocation.getArgument(0))
        }
        doAnswer { invocation ->
            messages += plainSerializer.serialize(invocation.getArgument<Component>(0))
            null
        }.`when`(sender).sendMessage(any(Component::class.java))

        messageStore[sender] = messages
        return sender
    }

    private fun sentMessages(sender: Any): List<String> = messageStore[sender] ?: emptyList()

    companion object {
        private val messageStore = mutableMapOf<Any, MutableList<String>>()
    }
}
