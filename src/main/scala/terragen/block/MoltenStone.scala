package terragen.block

import net.minecraft.block._
import net.minecraft.block.material._
import net.minecraft.world._
import net.minecraft.util.math.BlockPos
import net.minecraft.item._
import net.minecraft.entity._
import java.util.Random

class MoltenStone extends Block(Block.Properties.create(Material.ROCK, MaterialColor.OBSIDIAN).lightValue(7).hardnessAndResistance(0.5f, 2.0f).tickRandomly()) with BlockRegistrizer {
  // 40 items: 40% of a lava bucket, 2 dried kelp blocks, half a block of coal
  // You could just place it, wait for it to melt, and put it in a bucket to more than double burn time
  //   but that's a lot of work so it's fine
  fuel(8000)

  override def ticksRandomly(state: BlockState) = true
  override def randomTick(state: BlockState, world: World, pos: BlockPos, rand: Random) = {
    world.setBlockState(pos, Blocks.LAVA.getDefaultState, 3)
  }
}
