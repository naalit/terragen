package terragen

import terragen.block._

import com.google.common.collect.ImmutableSet
import java.util.BitSet
import java.util.Random
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Function
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.fluid.Fluids
import net.minecraft.util.math.BlockPos
import net.minecraft.world.chunk.IChunk
import net.minecraft.world.gen.carver._
import net.minecraft.util.Direction


class TCaveCarver(fun: Function[com.mojang.datafixers.Dynamic[_], net.minecraft.world.gen.feature.ProbabilityConfig]) extends CaveWorldCarver(fun, 256) {
  carvableBlocks = ImmutableSet.of(Blocks.STONE, Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE, Blocks.DIRT, Blocks.COARSE_DIRT, TBlocks.MARBLE, TBlocks.LIMESTONE, TBlocks.BASALT, Blocks.DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.PODZOL, Blocks.GRASS_BLOCK, Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA, Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.LIME_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA, Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.BLUE_TERRACOTTA, Blocks.BROWN_TERRACOTTA, Blocks.GREEN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA, Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.MYCELIUM, Blocks.SNOW, Blocks.PACKED_ICE)

  // Somewhat bigger than normal caves, better change for large rooms
  override def generateCaveRadius(rand: Random): Float = {
    var f = rand.nextFloat() * 3.0F + rand.nextFloat()
    if (rand.nextInt(5) == 0)
       f *= rand.nextFloat() * rand.nextFloat() * 6.0F + 3.0F
    f
  }

  // I copied the function from WorldCarver but changed it to not generate lava
  // I'm not 100% sure what some of the parameters are for
  override def carveBlock(chunk: IChunk, carvingMask: BitSet, rand: Random, p1: BlockPos.MutableBlockPos, p2: BlockPos.MutableBlockPos, p3: BlockPos.MutableBlockPos, _i1: Int, _i2: Int, _i3: Int, x: Int, z: Int, mi: Int, y: Int, mi2: Int, is_surface: AtomicBoolean): Boolean = {
     val i = mi | mi2 << 4 | y << 8
     if (carvingMask.get(i)) {
        return false;
     } else {
        carvingMask.set(i);
        p1.setPos(x, y, z);
        val blockstate = chunk.getBlockState(p1);
        val blockstate1 = chunk.getBlockState(p2.setPos(p1).move(Direction.UP));
        if (blockstate.getBlock() == Blocks.GRASS_BLOCK || blockstate.getBlock() == Blocks.MYCELIUM) {
           is_surface.set(true);
        }

        if (!this.canCarveBlock(blockstate, blockstate1)) {
           return false;
        } else {
            chunk.setBlockState(p1, Blocks.CAVE_AIR.getDefaultState, false);
            if (is_surface.get()) {
               p3.setPos(p1).move(Direction.DOWN);
               if (chunk.getBlockState(p3).getBlock() == Blocks.DIRT) {
                  chunk.setBlockState(p3, chunk.getBiome(p1).getSurfaceBuilderConfig().getTop(), false);
               }
            }

           return true;
        }
     }
  }

}
