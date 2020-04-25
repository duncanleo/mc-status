package me.duncanleo.mc_status

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.event.Listener

class App : JavaPlugin(), Listener {
  override fun onEnable() {
    logger.info("Hello there!")

    server.pluginManager.registerEvents(this, this)
  }
}
