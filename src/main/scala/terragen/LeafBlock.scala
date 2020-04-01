package terragen

import net.minecraft.block._
import net.minecraft.state._
import net.minecraft.block.material._
import net.minecraft.tags.BlockTags
import net.minecraft.world.{IBlockReader, World}
import net.minecraft.util.math.BlockPos
import net.minecraft.util.{BlockRenderLayer, Direction}
import net.minecraftforge.api.distmarker.{OnlyIn, Dist}
import java.util.Random
import net.minecraft.particles.ParticleTypes
import net.minecraft.entity.EntityType

object LeafBlock {
  val DISTANCE = IntegerProperty.create("distance", 0, 31)
  val PERSISTENT = properties.BlockStateProperties.PERSISTENT
}

import LeafBlock._

// Like a vanilla LeavesBlock, but:
// - Distance goes up to 31, allowing larger canopies
// - Distance is only updated on random ticks and doesn't immediately chain, so decay takes longer but is much less performance hungry
class LeafBlock extends Block(Block.Properties.create(Material.LEAVES).hardnessAndResistance(0.2F).sound(SoundType.PLANT).tickRandomly()) {
  var max_dist = 31

  setDefaultState(stateContainer.getBaseState.`with`[Integer, Integer](DISTANCE, 0).`with`[java.lang.Boolean, java.lang.Boolean](PERSISTENT, false))

  override def getStateForPlacement(context: net.minecraft.item.BlockItemUseContext): BlockState = getDefaultState.`with`[java.lang.Boolean, java.lang.Boolean](PERSISTENT, true)

  override def ticksRandomly(state: BlockState): Boolean = !state.get(PERSISTENT)

  override def randomTick(state: BlockState, world: World, pos: BlockPos, rand: Random) = {
    var d = max_dist //state.get(DISTANCE)
    for (dir <- Direction.values) {
      val s = world.getBlockState(pos.offset(dir, 1))
      if (s.getBlock().isInstanceOf[LeafBlock] && !s.get(PERSISTENT)) {
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
    } else if (d != state.get(DISTANCE))
      // We don't send the new state to clients because that lags the client too much, and they have no reason to need it
      world.setBlockState(pos, state.`with`[Integer, Integer](DISTANCE, d), 1)
  }

  @OnlyIn(Dist.CLIENT)
  override def animateTick(stateIn: BlockState, worldIn: World, pos: BlockPos, rand: Random): Unit = {
    if (worldIn.isRainingAt(pos.up()) && rand.nextInt(15) == 1) {
      val blockpos: BlockPos = pos.down()
      val blockstate: BlockState = worldIn.getBlockState(blockpos)
      if (!blockstate.isSolid ||
          !blockstate.func_224755_d(worldIn, blockpos, Direction.UP)) {
        val d0 = (pos.getX.toFloat + rand.nextFloat()).toDouble
        val d1 = pos.getY.toDouble - 0.05
        val d2 = (pos.getZ.toFloat + rand.nextFloat()).toDouble
        worldIn.addParticle(ParticleTypes.DRIPPING_WATER, d0, d1, d2, 0, 0, 0)
      }
    }
  }

  override def canEntitySpawn(state: BlockState, worldIn: IBlockReader, pos: BlockPos, ty: EntityType[_]) =
    ty == EntityType.OCELOT || ty == EntityType.PARROT

  override def fillStateContainer(builder: StateContainer.Builder[Block, BlockState]) = {
    builder.add(DISTANCE, PERSISTENT)
  }

  override def getOpacity(state: BlockState, worldIn: IBlockReader, pos: BlockPos): Int = 1
  override def isSolid(state: BlockState): Boolean = false
  override def causesSuffocation(state: BlockState, worldIn: IBlockReader, pos: BlockPos): Boolean = false
  // Switch between fancy and fast graphics by stealing it from a LeavesBlock, because Minecraft only sets it there
  override def getRenderLayer(): BlockRenderLayer = Blocks.OAK_LEAVES.getRenderLayer
}
