package me.duncanleo.mc_status

import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.httpPost
import com.squareup.moshi.Moshi
import me.duncanleo.mc_status.model.discord.DiscordWebhookPayload
import me.duncanleo.mc_status.model.discord.Embed
import me.duncanleo.mc_status.model.discord.EmbedField
import me.duncanleo.mc_status.util.TPSUtil
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import org.bukkit.event.raid.RaidTriggerEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.DisplaySlot
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.roundToInt
import kotlin.time.DurationUnit
import kotlin.time.ExperimentalTime
import kotlin.time.seconds

const val TIME_NIGHT = 13000
const val CONFIG_KEY_WEBHOOK_URL = "webhook_url"

class App : JavaPlugin(), Listener {
  val playerLocationMap = mutableMapOf<UUID, Location>()

  override fun onEnable() {
    logger.info("Hello there!")

    server.pluginManager.registerEvents(this, this)
    TPSUtil.registerTask(this)

    saveDefaultConfig()

    object : BukkitRunnable() {
      override fun run() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
          return
        }

        val scoreboardManager = Bukkit.getScoreboardManager()
        val tps = TPSUtil.tps

        val freeMemory = Runtime.getRuntime().freeMemory()
        val totalMemory = Runtime.getRuntime().maxMemory()

        Bukkit.getOnlinePlayers().forEach {
          val scoreboard = scoreboardManager?.newScoreboard

          var objective = scoreboard?.getObjective("Status")
          if (objective == null) {
            objective = scoreboard?.registerNewObjective("Status", "dummy", "Status")
          }
          objective?.displaySlot = DisplaySlot.SIDEBAR

          // TPS score
          val tpsScore = objective?.getScore("${ChatColor.DARK_GRAY}Server TPS")
          tpsScore?.score = tps.toInt()

          // RAM usage score
          val ramUsageScore = objective?.getScore("${ChatColor.DARK_GRAY}Server RAM Usage (%)")
          ramUsageScore?.score = ((freeMemory.toDouble() / totalMemory.toDouble()) * 100.0).toInt()

          // Time to day/night
          val isDay = it.world.time < TIME_NIGHT
          val entryToSet = "${ChatColor.DARK_PURPLE}Time to ${ChatColor.LIGHT_PURPLE}${if (isDay) "Night" else "Day"} (s)"
          val entryToRemove = "${ChatColor.DARK_PURPLE}Time to ${ChatColor.LIGHT_PURPLE}${if (isDay) "Day" else "Night"} (s)"
          val duration = if (isDay) TIME_NIGHT - it.world.time else 24000 - it.world.time + 1000L
          scoreboard?.resetScores(entryToRemove)

          val timeScore = objective?.getScore(entryToSet)
          timeScore?.score = (duration / 20L).toInt()

          // Ping score
          val pingScore = objective?.getScore("${ChatColor.AQUA}Ping (ms)")
          pingScore?.score = it.ping

          val expScore = objective?.getScore("${ChatColor.GREEN}Exp ${ChatColor.DARK_GREEN}to next level")
          expScore?.score = it.expToLevel

          // SCOREBOARD!
          if (scoreboard != null) {
            it.scoreboard = scoreboard
          }

          // Check location
          if (playerLocationMap[it.uniqueId]?.block?.biome != it.location.block.biome) {
            it.sendMessage("${ChatColor.DARK_AQUA}Welcome to the ${ChatColor.AQUA}${it.location.block.biome.name.capitalizeBukkitEnumName()} ${ChatColor.DARK_AQUA}")
          }
          playerLocationMap[it.uniqueId] = it.location
        }
      }
    }.runTaskTimer(this, 20 * 3, 20 * 5)

    object: BukkitRunnable() {
      override fun run() {
        val dateFormat = SimpleDateFormat("h:mm a")
        val currentTime = dateFormat.format(Date())
        Bukkit.broadcastMessage("${ChatColor.DARK_PURPLE}It is now ${ChatColor.LIGHT_PURPLE}$currentTime ${ChatColor.DARK_PURPLE} in Singapore")
      }
    }.runTaskTimer(this, 20 * 4, 20 * 60 * 30)
  }

  @EventHandler
  fun playerJoined(event: PlayerJoinEvent) {
    if (config.get(CONFIG_KEY_WEBHOOK_URL) == null) {
      return
    }

    val payload = DiscordWebhookPayload(
            username = "Minecraft",
            embeds = arrayOf(
                    Embed(
                            title = "A player joined the server",
                            description = ChatColor.stripColor(event.player.displayName) ?: event.player.displayName,
                            fields = arrayOf(
                                    EmbedField(
                                            name = "Current no. of players",
                                            value = Bukkit.getOnlinePlayers().size.toString()
                                    )
                            )
                    )
            )
    )

    object : BukkitRunnable() {
      override fun run() {
        publishDiscordWebhook(webhookURL = config.get(CONFIG_KEY_WEBHOOK_URL) as String, payload = payload)
      }
    }.run()
  }

  @EventHandler
  fun playerLeft(event: PlayerQuitEvent) {
    playerLocationMap.remove(event.player.uniqueId)
    if (config.get(CONFIG_KEY_WEBHOOK_URL) == null) {
      return
    }

    val payload = DiscordWebhookPayload(
            username = "Minecraft",
            embeds = arrayOf(
                    Embed(
                            title = "A player left the server",
                            description = ChatColor.stripColor(event.player.displayName) ?: event.player.displayName,
                            fields = arrayOf(
                                    EmbedField(
                                            name = "Current no. of players",
                                            value = (Bukkit.getOnlinePlayers().size - 1).toString()
                                    )
                            )
                    )
            )
    )

    object : BukkitRunnable() {
      override fun run() {
        publishDiscordWebhook(webhookURL = config.get(CONFIG_KEY_WEBHOOK_URL) as String, payload = payload)
      }
    }.run()
  }

  @EventHandler
  fun playerChangedWorld(event: PlayerChangedWorldEvent) {
    Bukkit.broadcastMessage("${ChatColor.GOLD}${event.player.displayName} ${ChatColor.YELLOW}went to ${ChatColor.GOLD}${event.player.location.world?.name?.capitalizeBukkitEnumName()}")
  }

  @EventHandler
  fun entityTarget(event: EntityTargetEvent) {
    if (event.target !is Player) {
      return
    }

    val player = event.target as Player

    when (event.entity) {
      is ExperienceOrb -> {
        return
      }
      is Monster -> {
        val monster = (event.entity as Monster)
        monster.displayHealth()
      }
    }

    when (event.reason) {
      EntityTargetEvent.TargetReason.TARGET_DIED,
      EntityTargetEvent.TargetReason.TEMPT -> {
        return
      }
      EntityTargetEvent.TargetReason.FORGOT_TARGET -> {
        player.sendMessage("${ChatColor.DARK_AQUA}You are no longer being targeted by a ${ChatColor.AQUA}${event.entity.type.name.capitalizeBukkitEnumName()}.")
      }
      else -> {
        player.sendMessage("${ChatColor.DARK_AQUA}You are being ${ChatColor.RED}targeted ${ChatColor.DARK_AQUA}by a ${ChatColor.AQUA}${event.entity.type.name.capitalizeBukkitEnumName()}. ${ChatColor.DARK_AQUA}Reason: ${ChatColor.AQUA}${event.reason.name.capitalizeBukkitEnumName()}")
      }
    }
  }

  @EventHandler
  fun entityDamageByOtherEntity(event: EntityDamageEvent) {
    if (event.entity !is Monster) {
      return
    }
    val monster = (event.entity as Monster)
    monster.displayHealth()
  }

  @EventHandler
  fun entityRightClick(event: PlayerInteractEntityEvent) {
    if (event.player.inventory.itemInMainHand.type != Material.AIR || event.rightClicked !is LivingEntity || event.hand != EquipmentSlot.HAND) {
      return
    }
    val rightClicked = event.rightClicked
    val sb = StringBuilder()
    sb.appendln("${ChatColor.DARK_GREEN}===============")
    sb.appendln("${ChatColor.DARK_GREEN}  Entity Info  ")
    sb.appendln("${ChatColor.DARK_GREEN}===============")
    sb.appendln("${ChatColor.DARK_GREEN}Type: ${ChatColor.GREEN}${rightClicked.type.name.capitalizeBukkitEnumName()}")

    if (rightClicked is LivingEntity) {
      val maxHealth = rightClicked.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 0.0
      sb.appendln("${ChatColor.DARK_GREEN}Health: ${ChatColor.GREEN}${(rightClicked.health / maxHealth * 100.0).roundToInt()}%")
    }

    if (rightClicked.name.isNotEmpty() && rightClicked.name != rightClicked.type.name) {
      sb.appendln("${ChatColor.DARK_GREEN}Name: ${ChatColor.GREEN}${rightClicked.name}")
    }

    if (rightClicked is Tameable) {
      val isTamed = rightClicked.isTamed
      val ownerName = rightClicked.owner?.name
      sb.appendln("${ChatColor.DARK_GREEN}Tamed: ${if (isTamed) "by ${ChatColor.GREEN}$ownerName" else "no"}")
    }

    if (rightClicked is Ageable) {
      sb.appendln("${ChatColor.DARK_GREEN}Age: ${rightClicked.age}")
      sb.appendln("${ChatColor.DARK_GREEN}Adult: ${rightClicked.isAdult}")
      sb.appendln("${ChatColor.DARK_GREEN}Can Breed: ${rightClicked.canBreed()}")
    }

    when (rightClicked) {
      is Cat -> {
        sb.appendln("${ChatColor.DARK_GREEN}Type: ${rightClicked.catType.name.capitalizeBukkitEnumName()}")
      }
      is Ocelot -> {
        sb.appendln("${ChatColor.DARK_GREEN}Type: ${rightClicked.catType.name.capitalizeBukkitEnumName()}")
      }
      is Fox -> {
        sb.appendln("${ChatColor.DARK_GREEN}Type: ${rightClicked.foxType.name.capitalizeBukkitEnumName()}")
      }
      is Parrot -> {
        sb.appendln("${ChatColor.DARK_GREEN}Variant: ${rightClicked.variant.name.capitalizeBukkitEnumName()}")
      }
      is Panda -> {
        sb.appendln("${ChatColor.DARK_GREEN}Main Gene: ${rightClicked.mainGene.name.capitalizeBukkitEnumName()}")
        sb.appendln("${ChatColor.DARK_GREEN}Hidden Gene: ${rightClicked.hiddenGene.name.capitalizeBukkitEnumName()}")
      }
      is Wolf -> {
        sb.appendln("${ChatColor.DARK_GREEN}Collar Colour: ${rightClicked.collarColor.name.capitalizeBukkitEnumName()}")
      }
      is Horse -> {
        sb.appendln("${ChatColor.DARK_GREEN}Colour: ${rightClicked.color.name.capitalizeBukkitEnumName()}")
        sb.appendln("${ChatColor.DARK_GREEN}Style: ${rightClicked.style.name.capitalizeBukkitEnumName()}")
      }
      is Llama -> {
        sb.appendln("${ChatColor.DARK_GREEN}Colour: ${rightClicked.color.name.capitalizeBukkitEnumName()}")
        sb.appendln("${ChatColor.DARK_GREEN}Strength: ${rightClicked.strength}")
        sb.appendln("${ChatColor.DARK_GREEN}Jump Strength: ${rightClicked.jumpStrength}")
      }
    }

    event.player.sendMessage(sb.toString())
  }

  @EventHandler
  fun breedEntity(event: EntityBreedEvent) {
    if (event.breeder !is Player) {
      return
    }
    val player = event.breeder as Player
    Bukkit.broadcastMessage("${ChatColor.GREEN}${player.displayName} ${ChatColor.DARK_GREEN}just bred a ${ChatColor.GREEN}${event.entityType.name.capitalizeBukkitEnumName()} ${ChatColor.DARK_GREEN}and earned ${ChatColor.GREEN}${event.experience} exp")
  }

  @EventHandler
  fun tameEntity(event: EntityTameEvent) {
    val player = event.owner as Player
    Bukkit.broadcastMessage("${ChatColor.GREEN}${player.displayName} ${ChatColor.DARK_GREEN}just tamed a ${ChatColor.GREEN}${event.entityType.name.capitalizeBukkitEnumName()}")
  }

  @EventHandler
  fun exp(event: PlayerExpChangeEvent) {
    if (event.player.expToLevel <= event.amount) {
      Bukkit.broadcastMessage("${ChatColor.GOLD}${event.player.displayName} ${ChatColor.YELLOW}just levelled up to ${ChatColor.GOLD}${event.player.level + 2}")
    }
  }

  @EventHandler
  fun triggerRaid(event: RaidTriggerEvent) {
    Bukkit.broadcastMessage("${ChatColor.RED}${event.player.displayName} ${ChatColor.DARK_RED}triggered a raid on a village!")
  }

  @EventHandler
  fun enchantItem(event: EnchantItemEvent) {
    // Get a copy of existing enchantments
    val completeEnchantments = HashMap(event.item.enchantments)
    // Add all the new enchantments
    completeEnchantments.putAll(event.enchantsToAdd)
    val enchantments = completeEnchantments.map { "${it.key} ${it.value.toRomanNumeral()}" }.joinToString(", ")
    Bukkit.broadcastMessage("${ChatColor.GOLD}${event.enchanter.displayName} ${ChatColor.YELLOW}just enchanted a ${ChatColor.GOLD}${event.item.type.name.capitalizeBukkitEnumName()} ($enchantments) ${ChatColor.YELLOW} at the cost of ${ChatColor.GOLD}${event.whichButton() + 1} levels ${ChatColor.YELLOW}and ${ChatColor.GOLD}${event.expLevelCost} exp")
  }

  @ExperimentalTime
  @EventHandler
  fun entityPotionEffect(event: EntityPotionEffectEvent) {
    if (event.entity !is Player || event.newEffect == null) {
      return
    }
    val durationTicks = event.newEffect?.duration?.div(20) ?: 0
    val duration = durationTicks.seconds
    event.entity.sendMessage("${ChatColor.DARK_GREEN}You have received the potion effect ${ChatColor.GREEN}${event.newEffect?.type?.name?.capitalizeBukkitEnumName()} for ${duration.toString(DurationUnit.MINUTES)}")
  }

  private fun publishDiscordWebhook(webhookURL: String, payload: DiscordWebhookPayload) {
    val moshi = Moshi.Builder().build()
    val payloadEncoded = moshi
            .adapter(DiscordWebhookPayload::class.java)
            .toJson(payload)

    webhookURL
            .httpPost()
            .jsonBody(payloadEncoded)
            .header("User-Agent", "MCStatus ${description.version}")
            .also {
              logger.info(it.toString())
            }
            .response { result ->
              logger.info(result.toString())
            }
  }

  /**
   * Get a player's ping
   */
  val Player.ping: Int
    get() {
      try {
        val entityPlayer = this.javaClass.getMethod("getHandle").invoke(this)
        val ping = entityPlayer.javaClass.getField("ping").get(entityPlayer) as? Int

        if (ping != null) {
          return ping
        }
      } catch (e: Exception) {
        logger.warning(e.message)
      }

      return -1
    }

  private fun String.capitalizeBukkitEnumName(): String {
    return this
            .toLowerCase()
            .replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.capitalize() }
  }

  /**
   * Convert a number to roman numeral
   * https://kodejava.org/how-do-i-convert-number-into-roman-numerals/
   */
  private fun Int.toRomanNumeral(): String {
    return String(CharArray(this)).replace('\u0000', 'I')
            .replace("IIIII", "V")
            .replace("IIII", "IV")
            .replace("VV", "X")
            .replace("VIV", "IX")
            .replace("XXXXX", "L")
            .replace("XXXX", "XL")
            .replace("LL", "C")
            .replace("LXL", "XC")
            .replace("CCCCC", "D")
            .replace("CCCC", "CD")
            .replace("DD", "M")
            .replace("DCD", "CM")
  }

  private fun Monster.displayHealth() {
    val maxHealth = this.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 0.0
    this.customName = "${ChatColor.RED}${(this.health / maxHealth * 100.0).roundToInt()}%"
    this.isCustomNameVisible = true
  }
}
