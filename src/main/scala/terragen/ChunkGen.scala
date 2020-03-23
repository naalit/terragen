package terragen

import net.minecraft.world.gen.ChunkGenerator
import net.minecraft.world.World
import net.minecraft.world.biome.provider.BiomeProvider
import net.minecraft.world.gen.Heightmap
import net.minecraft.world.chunk.IChunk
import net.minecraft.world.IWorld
import net.minecraft.util.SharedSeedRandom
import net.minecraft.util.math.BlockPos
import net.minecraft.block.BlockState

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class ChunkGen[C <: net.minecraft.world.gen.GenerationSettings](
    world: World,
    terr: Terrain,
    config: C
) extends ChunkGenerator[C](world, terr, config) {
  val LOGGER = LogManager.getLogger()

  override def func_222529_a(x: Int, z: Int, ty: Heightmap.Type): Int =
    terr.height(x, z)

  override def generateSurface(chunkIn: IChunk): Unit = {
    val rand = new SharedSeedRandom()
    val pos = chunkIn.getPos()
    rand.setBaseChunkSeed(pos.x, pos.z)
    val biomes = chunkIn.getBiomes()

    val start_x = pos.getXStart()
    val start_z = pos.getZStart()

    for (x <- 0 until 16; z <- 0 until 16) {
      val h = chunkIn.getTopBlockY(Heightmap.Type.WORLD_SURFACE_WG, x, z) + 1
      biomes(z * 16 + x).buildSurface(
        rand,
        chunkIn,
        start_x + x,
        start_z + z,
        h,
        // I'm not sure what to give for 'noise'
        terr.gen.getValue(x * 0.0625, z * 0.0625),
        config.getDefaultBlock(),
        config.getDefaultFluid(),
        64,
        world.getSeed()
      )
    }
  }

  override def getGroundHeight(): Int = 64

  override def makeBase(worldIn: IWorld, chunkIn: IChunk): Unit = {
    var heightmap = new Array[Int](16 * 16)

    val start_x = chunkIn.getPos().getXStart()
    val start_z = chunkIn.getPos().getZStart()

    for (x <- 0 until 16;
         z <- 0 until 16)
      heightmap(z * 16 + x) = terr.height(start_x + x, start_z + z)

    for (x <- 0 until 16;
         z <- 0 until 16) {
      val h = heightmap(z * 16 + x)
      for (y <- 0 until h) {
        val block =
          if (y == 0) net.minecraft.block.Blocks.BEDROCK.getDefaultState()
          else config.getDefaultBlock()

        chunkIn.setBlockState(
          new BlockPos(x, y, z),
          block,
          false
        )
      }
      for (y <- h until 64) {
        chunkIn.setBlockState(
          new BlockPos(x, y, z),
          config.getDefaultFluid(),
          false
        )
      }
    }

    for (x <- 0 until 16;
         z <- 0 until 16;
         y <- 0 until heightmap(z * 16 + x)) {
      val block =
        if (y == 0) net.minecraft.block.Blocks.BEDROCK.getDefaultState()
        else config.getDefaultBlock()

      chunkIn.setBlockState(
        new BlockPos(x, y, z),
        block,
        false
      )
    }
  }
}
