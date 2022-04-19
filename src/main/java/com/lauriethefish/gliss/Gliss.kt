package com.lauriethefish.gliss

import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.plugin.java.JavaPlugin

class Gliss : JavaPlugin(), Listener {
    override fun onEnable() {
        logger.info("gliss!")
        saveDefaultConfig()

        server.pluginManager.registerEvents(this, this)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(label != "benchmark") {
            return false
        }

        if(sender !is Player) {
            sender.sendMessage("You must be a player to use this command")
            return false
        }

        sender.sendMessage("Starting benchmark")
        BenchmarkRunner(this, logger, Config(config), sender).run()
        return true
    }
}