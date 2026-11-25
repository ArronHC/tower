package com.jzb.tower.core.service

import com.jzb.tower.core.contract.TowerResourceProvider
import com.jzb.tower.core.model.WorldDotMapData

class WorldDotMapLoader(private val resources: TowerResourceProvider) {
    fun load(): WorldDotMapData {
        fun grid(file: String) = requireNotNull(resources.readText("WorldMap/$file")) { "Missing $file" }
            .lineSequence().map(String::trim).filter { it.isNotEmpty() && !it.startsWith("# ") }.toList()
        val land = grid("WorldDotMap.txt")
        val ownership = grid("WorldDotCountries.txt")
        val size = land.first().split(' ')
        require(size.size == 3 && size[0] == "size" && land.first() == ownership.first())
        val columns = size[1].toInt()
        val rows = size[2].toInt()
        val landRows = land.drop(1)
        val countries = mutableMapOf<String, MutableList<Int>>()
        require(landRows.size == rows && landRows.all { it.length == columns } && ownership.size == rows + 1)
        ownership.drop(1).forEachIndexed { y, row ->
            val cells = row.split(' ')
            require(cells.size == columns)
            cells.forEachIndexed { x, code ->
                if (code != "..") countries.getOrPut(code) { mutableListOf() }.add(y * columns + x)
            }
        }
        return WorldDotMapData(columns, rows, landRows, countries.mapValues { it.value.toIntArray() })
    }
}
