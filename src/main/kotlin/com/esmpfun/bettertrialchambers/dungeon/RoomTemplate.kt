package com.esmpfun.bettertrialchambers.dungeon

import com.esmpfun.bettertrialchambers.models.BlockSnapshot
import java.io.Serializable

/**
 * A captured room piece: its footprint, the blocks that make it up (relative
 * coords, jigsaw cells already swapped to a wall block so unconnected doors
 * stay walls), its [Connector]s, and role [tags]. Persisted compressed on disk.
 *
 * Air cells are omitted (parity with chamber snapshots) — dungeons generate
 * into empty space.
 */
data class RoomTemplate(
    val id: String,
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val blocks: Map<Triple<Int, Int, Int>, BlockSnapshot>,
    val connectors: List<Connector>,
    val tags: Set<String>,
) : Serializable {

    fun shape(): RoomShape = RoomShape(id, sizeX, sizeY, sizeZ, connectors, tags)

    companion object {
        private const val serialVersionUID = 1L
    }
}
