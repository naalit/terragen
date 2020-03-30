package terragen

import net.minecraft.block._
import net.minecraft.block.material._
import net.minecraft.world.IBlockReader
import net.minecraft.util.math.BlockPos
import net.minecraft.util.BlockRenderLayer

class SimpleLeaf extends Block(Block.Properties.create(Material.LEAVES).hardnessAndResistance(0.2F).sound(SoundType.PLANT)) {
  override def getOpacity(state: BlockState, worldIn: IBlockReader, pos: BlockPos): Int = 1
  override def isSolid(state: BlockState): Boolean = false
  override def causesSuffocation(state: BlockState, worldIn: IBlockReader, pos: BlockPos): Boolean = false
  override def getRenderLayer(): BlockRenderLayer = BlockRenderLayer.CUTOUT_MIPPED
}
