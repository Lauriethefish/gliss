package com.lauriethefish.gliss

import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.PalettedContainer
import org.bukkit.Location
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld

class ChunkAccessor(lowPos: Location, highPos: Location) {
    private val chunks: Array<PalettedContainer<BlockState>?>

    private val xMultiplier: Int
    private val yMultiplier: Int
    private val zMultiplier: Int

    private val lX: Int
    private val lZ: Int
    private val lY: Int

    init {
        val nmsWorld = (lowPos.world as CraftWorld).handle

        lX = lowPos.blockX shr 4
        lZ = lowPos.blockZ shr 4
        lY = lowPos.blockY shr 4

        val hX = highPos.blockX shr 4
        val hZ = highPos.blockZ shr 4
        val hY = highPos.blockY shr 4

        zMultiplier = 1
        yMultiplier = ((hZ - lZ) + 1)
        xMultiplier = yMultiplier * ((hY - lY) + 1)
        chunks = Array(xMultiplier * ((hX - lX) + 1)) { null }

        var i = 0
        for(cX in lX..hX) {
            for(cY in lY..hY) {
                for(cZ in lZ..hZ) {
                    val chunk = nmsWorld.getChunk(cX, cZ)
                    val sectionIdx = chunk.getSectionIndex(cY shl 4)
                    val section = chunk.getSection(sectionIdx)

                    chunks[i] = section.states

                    i++
                }
            }
        }
    }

    fun getState(x: Int, y: Int, z: Int): BlockState {
        val cX = (x shr 4) - lX
        val cY = (y shr 4) - lY
        val cZ = (z shr 4) - lZ

        val chunkIdx = cX * xMultiplier + cY * yMultiplier + cZ * zMultiplier
        return chunks[chunkIdx]!!.get(x and 15, y and 15, z and 15)
    }
}