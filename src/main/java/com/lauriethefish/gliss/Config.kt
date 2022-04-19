package com.lauriethefish.gliss

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration

fun ConfigurationSection.getSection(sect: String): ConfigurationSection {
    return this.getConfigurationSection(sect) ?: throw InvalidConfigurationException("No $sect section")
}

fun ConfigurationSection.getPosition(sectPath: String): Location {
    val sect = getSection(sectPath)
    val worldName = sect.getString("world") ?: throw InvalidConfigurationException("No world given for location")
    val world = Bukkit.getWorld(worldName) ?: throw InvalidConfigurationException("World $worldName does not exist")

    return Location(
            world,
            sect.getDouble("x"),
            sect.getDouble("y"),
            sect.getDouble("z")
    )
}

class Config(private val cfg: FileConfiguration) {
    private var bMarkSect = cfg.getConfigurationSection("benchmark") ?: throw InvalidConfigurationException("No benchmark section")

    val originCenterPos = bMarkSect.getPosition("originPortal")
    val destCenterPos = bMarkSect.getPosition("destPortal")
    val portalWidth = bMarkSect.getInt("width")
    val portalHeight = bMarkSect.getInt("height")
    val projectionWidth = bMarkSect.getInt("projectionWidth")
    val projectionHeight = bMarkSect.getInt("projectionHeight")
    val wideningAmount = bMarkSect.getDouble("wideningAmount")
    val ticksPerRotation = bMarkSect.getInt("ticksPerRotation")
    val rotationRadius = bMarkSect.getDouble("rotationRadius")
    val ticksPerBenchmark = bMarkSect.getInt("ticksPerBenchmark")
}