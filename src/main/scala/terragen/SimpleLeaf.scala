package terragen

import net.minecraft.block._
import net.minecraft.state._
import net.minecraft.block.material._
import net.minecraft.tags.BlockTags
import net.minecraft.world.{IBlockReader, World}
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{BlockRenderLayer, Direction}
import java.util.Random

object LeafBlock {
  val DISTANCE = IntegerProperty.create("distance", 0, 31)
  val PERSISTENT = properties.BlockStateProperties.PERSISTENT
}

import LeafBlock._

// Like a vanilla LeavesBlock, but:
// - Distance goes up to 31, allowing larger canopies
// - Distance is only updated on random ticks and doesn't immediately chain, so decay takes longer but is much less performance hungry
class SimpleLeaf extends Block(Block.Properties.create(Material.LEAVES).hardnessAndResistance(0.2F).sound(SoundType.PLANT).tickRandomly()) {
  var max_dist = 31

  setDefaultState(stateContainer.getBaseState.`with`[Integer, Integer](DISTANCE, 0).`with`[java.lang.Boolean, java.lang.Boolean](PERSISTENT, false))

  override def getStateForPlacement(context: net.minecraft.item.BlockItemUseContext): BlockState = getDefaultState.`with`[java.lang.Boolean, java.lang.Boolean](PERSISTENT, false)

  override def ticksRandomly(state: BlockState): Boolean = !state.get(PERSISTENT)

  override def randomTick(state: BlockState, world: World, pos: BlockPos, rand: Random) = {
    var d = max_dist //state.get(DISTANCE)
    for (dir <- Direction.values) {
      val s = world.getBlockState(pos.offset(dir, 1))
      if (s.getBlock().isInstanceOf[SimpleLeaf] && !s.get(PERSISTENT)) {
        // We know we're at least nd+1 blocks away
        val nd = s.get(DISTANCE)
        if (nd + 1 < d)
          d = nd + 1
      } else if (BlockTags.LOGS.contains(s.getBlock())) {
        d = 0
      }
    }

    // We're far away
    if (d == max_dist) {
      Block.spawnDrops(state, world, pos)
      world.removeBlock(pos, false)
    } else
      world.setBlockState(pos, state.`with`[Integer, Integer](DISTANCE, d), 3)
  }

  override def fillStateContainer(builder: StateContainer.Builder[Block, BlockState]) = {
    builder.add(DISTANCE, PERSISTENT)
  }

  override def getOpacity(state: BlockState, worldIn: IBlockReader, pos: BlockPos): Int = 1
  override def isSolid(state: BlockState): Boolean = false
  override def causesSuffocation(state: BlockState, worldIn: IBlockReader, pos: BlockPos): Boolean = false
  override def getRenderLayer(): BlockRenderLayer = BlockRenderLayer.CUTOUT_MIPPED
}
