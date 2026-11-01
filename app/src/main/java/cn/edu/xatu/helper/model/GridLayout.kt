package cn.edu.xatu.helper.model

data class BlockPlacement(val block: CourseBlock, val lane: Int, val count: Int)
/** Split overlapping connected groups into lanes so that every course remains tappable. */
fun placeBlocks(blocks: List<CourseBlock>): List<BlockPlacement> = blocks.groupBy { it.day }.values.flatMap { day ->
    val groups = mutableListOf<MutableList<CourseBlock>>()
    day.sortedWith(compareBy({ it.start }, { it.end })).forEach { block ->
        val last = groups.lastOrNull()
        if (last != null && block.start <= last.maxOf { it.end }) last.add(block) else groups.add(mutableListOf(block))
    }
    groups.flatMap { group ->
        val ends = mutableListOf<Int>()
        val assigned = group.map { block ->
            val free = ends.indexOfFirst { it < block.start }
            val lane = if (free < 0) { ends.add(block.end); ends.lastIndex } else { ends[free] = block.end; free }
            block to lane
        }
        assigned.map { (block, lane) -> BlockPlacement(block, lane, ends.size) }
    }
}
