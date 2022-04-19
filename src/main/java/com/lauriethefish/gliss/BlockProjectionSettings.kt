package com.lauriethefish.gliss

import org.bukkit.Location
import org.bukkit.entity.Player

data class BlockProjectionSettings(
        val player: Player,
        val fromCentreBlock: Location,
        val toCentreBlock: Location,
        val windowWidth: Int,
        val windowHeight: Int,
        val projectionWidth: Int,
        val projectionHeight: Int,
        val wideningAmount: Double
)
