package terragen

import net.minecraft.block._
import net.minecraft.block.material._
import net.minecraft.state._
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{IBlockReader, World}
import java.util.Random
import net.minecraft.util.math.shapes.ISelectionContext
import net.minecraftforge.event.ForgeEventFactory

object Sapling {
  val STAGE = IntegerProperty.create("stage", 0, 3)
  val SHAPE = Block.makeCuboidShape(2, 0, 2, 14, 12, 14)
}

import Sapling._

class Sapling(trees: Array[Tree]) extends BushBlock(Block.Properties.create(Material.ORGANIC).tickRandomly()) with IGrowable {
  assert(trees.length > 0, "Cannot create sapling with no trees")
  setDefaultState(stateContainer.getBaseState.`with`[Integer, Integer](STAGE, 0))

  override def ticksRandomly(state: BlockState) = true

  override def getShape(state: BlockState, worldIn: IBlockReader, pos: BlockPos, context: ISelectionContext) = SHAPE

  override def fillStateContainer(builder: StateContainer.Builder[Block, BlockState]) = builder.add(STAGE)

  override def canGrow(world: IBlockReader, pos: BlockPos, state: BlockState, isClient: Boolean) = true

  override def tick(state: BlockState, world: World, pos: BlockPos, rand: Random) =
    if (world.isAreaLoaded(pos, 1) && world.getLight(pos.up()) >= 9 && rand.nextInt(7) == 0)
        grow(world, rand, pos, state)

  override def canUseBonemeal(world: World, rand: Random, pos: BlockPos, state: BlockState): Boolean = rand.nextDouble < 0.45
  override def grow(world: World, rand: Random, pos: BlockPos, state: BlockState): Unit = {
    val stage = state.get(STAGE)
    if (stage == 3) {
      // Let mods stop it from growing like a normal tree
      if (!ForgeEventFactory.saplingGrowTree(world, rand, pos))
        return

      world.removeBlock(pos, true)
      trees(rand.nextInt(trees.length)).place(pos, world, rand)
    } else {
      world.setBlockState(pos, state.`with`[Integer, Integer](STAGE, stage+1), 4)
    }
  }
}
