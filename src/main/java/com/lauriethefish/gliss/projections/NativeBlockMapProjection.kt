package com.lauriethefish.gliss.projections

import com.lauriethefish.gliss.BlockProjection
import com.lauriethefish.gliss.BlockProjectionSettings
import com.lauriethefish.gliss.ChunkAccessor
import net.minecraft.core.BlockPos
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket
import net.minecraft.world.level.block.state.BlockState
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer
import org.bukkit.util.Vector
import java.lang.Math.abs

class NativeBlockMapProjection(private val settings: BlockProjectionSettings) : BlockProjection {
    private val planeNormal: Vector = Vector(1, 0, 0)
    private val planeCentre: Vector = Vector(0.0, 0.0, 0.0)
    private val lateralMaxDeviation = (settings.windowWidth / 2.0) + settings.wideningAmount
    private val verticalMaxDeviation = (settings.windowHeight / 2.0) + settings.wideningAmount

    private val totalProjectionWidth = settings.projectionWidth * 2 + 1
    private val totalProjectionHeight = settings.projectionHeight * 2 + 1

    private val zMultiplier = 1
    private val yMultiplier = totalProjectionWidth
    private val xMultiplier = yMultiplier * totalProjectionHeight

    private val totalStateCount = totalProjectionWidth * totalProjectionWidth * totalProjectionHeight
    private val mappedStates: Array<BlockState?> = Array(totalStateCount) { null }
    private val viewedStates: Array<BlockState?> = Array(totalStateCount) { null }
    private val mappedIndices: IntArray = IntArray(totalStateCount) { 0 }
    private var mappedCount = 0

    private val fromAccess: ChunkAccessor
    private val toAccess: ChunkAccessor
    private var firstUpdate = true

    init {
        val sizeVector = Vector(settings.projectionWidth, settings.projectionHeight, settings.projectionWidth)
        fromAccess = ChunkAccessor(settings.fromCentreBlock.clone().subtract(sizeVector), settings.fromCentreBlock.clone().add(sizeVector))
        toAccess = ChunkAccessor(settings.toCentreBlock.clone().subtract(sizeVector), settings.toCentreBlock.clone().add(sizeVector))
    }

    private fun getMappedStatesIndex(x: Int, y: Int, z: Int): Int {
        return (z + settings.projectionWidth) * zMultiplier + (y + settings.projectionHeight) * yMultiplier + (x + settings.projectionWidth * xMultiplier)
    }

    private fun generateMapFrom(idx: Int) {
        val stack = IntArray(totalStateCount)
        stack[0] = idx

        val offsets = IntArray(6) { 0 }
        offsets[0] = xMultiplier
        offsets[1] = -xMultiplier
        offsets[2] = yMultiplier
        offsets[3] = -yMultiplier
        offsets[4] = zMultiplier
        offsets[5] = -zMultiplier

        var stackPos = 0
        while(stackPos >= 0) {
            val originalPositionInt = stack[stackPos]
            stackPos--
            var positionInt = originalPositionInt

            val zAmount = positionInt % yMultiplier
            val relZ = zAmount - settings.projectionWidth
            positionInt -= zAmount
            val yAmount = (positionInt % xMultiplier)
            positionInt -= yAmount
            val relY = (yAmount / yMultiplier) - settings.projectionHeight
            val relX = (positionInt / xMultiplier) - settings.projectionWidth
            val destState = toAccess.getState(relX + settings.toCentreBlock.blockX, relY + settings.toCentreBlock.blockY, relZ + settings.toCentreBlock.blockZ)
            mappedStates[originalPositionInt] = destState

            if(relX == settings.projectionWidth || relX == -settings.projectionWidth ||
                    relY == settings.projectionHeight || relY == -settings.projectionHeight ||
                    relZ == settings.projectionWidth || relZ == -settings.projectionWidth
                    || destState.isOpaque) {
                continue
            }

            for(offset in offsets) {
                val newPos = originalPositionInt + offset
                if(mappedStates[newPos] == null) {
                    mappedIndices[mappedCount] = newPos
                    mappedCount++

                    stackPos++
                    stack[stackPos] = newPos
                }
            }
        }
    }

    override fun update() {
        if(firstUpdate) {
            generateMapFrom(getMappedStatesIndex(0, 0, 0))
            firstUpdate = false
        }

        // Find the position of the player, relative to the centres of the blocks at the origin
        val playerRelativePos = settings.player
                .eyeLocation // Not feet location, eye location
                .toVector()
                // Offset by half a block so that we are relative to the centres of the blocks
                .subtract(settings.fromCentreBlock.clone().add(0.5, 0.5, 0.5).toVector())

        val connection = (settings.player as CraftPlayer).handle.connection

        for(idxIdx in 0..mappedCount - 1) {
            val originalPositionInt = mappedIndices[idxIdx]
            var positionInt = originalPositionInt
            val zAmount = positionInt % yMultiplier
            val relZ = zAmount - settings.projectionWidth
            positionInt -= zAmount
            val yAmount = (positionInt % xMultiplier)
            positionInt -= yAmount
            val relY = (yAmount / yMultiplier) - settings.projectionHeight
            val relX = (positionInt / xMultiplier) - settings.projectionWidth

            val relativePos = Vector(relX, relY, relZ)

            val originBlockPos = BlockPos(relX + settings.fromCentreBlock.blockX,
                    relY + settings.fromCentreBlock.blockY,
                    relZ + settings.fromCentreBlock.blockZ)

            // Find if the line between the player's viewpoint and the block intersects the portal view-plane
            val renderedState: BlockState = if (intersects(playerRelativePos, relativePos)) {
                // If it does, then this block should be projected from the other side of the portal
                val state = toAccess.getState(relX + settings.toCentreBlock.blockX,
                        relY + settings.toCentreBlock.blockY,
                        relZ + settings.toCentreBlock.blockZ)

                if(mappedStates[originalPositionInt] != state) {
                    generateMapFrom(positionInt)
                }

                state
            } else {
                // Otherwise the block should be that of the origin of the portal
                fromAccess.getState(originBlockPos.x, originBlockPos.y, originBlockPos.z)
            }

            val existing = viewedStates[originalPositionInt]
            if (renderedState != existing) { // Only send an update packet if the block actually changes
                connection.send(ClientboundBlockUpdatePacket(originBlockPos, renderedState))
                viewedStates[originalPositionInt] = renderedState
            }
        }
    }

    private fun intersects(playerPos: Vector, blockPos: Vector): Boolean {
        val direction: Vector = blockPos.clone().subtract(playerPos).normalize()

        // Find if we intersect the plane, and where
        val denominator: Double = planeNormal.dot(direction)
        if (abs(denominator) > 0.0001) {
            val difference: Vector = planeCentre.clone().subtract(playerPos)
            val t = difference.dot(planeNormal) / denominator

            // If the block was before the portal, return false
            if (playerPos.distance(blockPos) < t) {
                return false
            }
            if (t > 0.0001) {
                val portalIntersectPoint: Vector = playerPos.clone().add(direction.multiply(t))
                val distCenter = portalIntersectPoint.subtract(planeCentre)

                // Return true if the intersection point was close enough to the portal window
                return abs(distCenter.x) <= 0.001 && abs(distCenter.y) <= verticalMaxDeviation && abs(distCenter.z) <= lateralMaxDeviation
            }
        }

        return false
    }
}