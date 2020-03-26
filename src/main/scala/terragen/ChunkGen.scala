package terragen

import net.minecraft.world.gen.ChunkGenerator
import net.minecraft.world.World
import net.minecraft.world.biome.provider.BiomeProvider
import net.minecraft.world.gen.Heightmap
import net.minecraft.world.chunk.IChunk
import net.minecraft.world.IWorld
import net.minecraft.util.SharedSeedRandom
import net.minecraft.util.math.BlockPos
import net.minecraft.block.{Block, Blocks, BlockState}

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
      //val h = chunkIn.getTopBlockY(Heightmap.Type.WORLD_SURFACE_WG, x, z) + 1
      val h = terr.height(start_x + x, start_z + z) + 1 // chunkIn.getTopBlockY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) + 1
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

  // Returns a whole column of crust
  def make_rock(x: Int, z: Int, h: Int, rain: Double): Array[BlockState] = {
    var arr = Array.fill(h)(Blocks.STONE.getDefaultState())
    // for (y <- h until h+10)
    //   arr(y) = Blocks.AIR.getDefaultState()

    val continent_blend = terr.smoothstep(60, 70, h)
    val ocean_blend = 1 - continent_blend

    // Allow outcrops
    // I disabled outcrops actually because they weren't working very well
    var next_stratum = h // + (terr.fBm(x * 0.005 + 32.61, z * 0.005 + 43.92, 3) * 60 - 54).max(0).min(6).toInt
    def place_rock(depth: Double, rock: Block) {
      if (depth <= 0 || next_stratum <= 0)
        return
      for (y <- (next_stratum - depth.toInt).max(0) until next_stratum)
        arr(y) = rock.getDefaultState()
      next_stratum -= depth.toInt
    }

    if (rain < 0.2) {
      val sandstone_depth = 20 + 15 * terr.gen.getValue(x * 0.005 - 173.2487, z * 0.005 + 983.829374)
      place_rock(sandstone_depth.toInt, if (terr.red_sand(x, z)) Blocks.RED_SANDSTONE else Blocks.SANDSTONE)
    }

    // Magma cools rapidly in the ocean; place some obsidian at divergent faults
    // I'd do basalt but we don't have it
    val fault_dist = terr.fault_dist(x, z) + 0.05 * terr.gen.getValue(x * 0.05 - 173.2487, z * 0.05 + 983.829374)
    val obsidian_n = terr.gen.getValue(x * 0.005 - 63.247, z * 0.005 + 62.627)
    if (fault_dist < 0.05)
      place_rock(ocean_blend * (obsidian_n - 0.6) * 8, Blocks.OBSIDIAN)

    // I'm pretending diorite is gabbro and putting it in the ocean
    val gabbro_n = terr.gen.getValue(x * 0.005 + 6.2861, z * 0.005 - 61.816)
    place_rock(ocean_blend * (gabbro_n + 0.8) * 30, Blocks.DIORITE)

    // Andesite forms at convergent faults
    val andesite_n = terr.gen.getValue(x * 0.005 - 643.247, z * 0.005 + 624.627)
    if (fault_dist < 0.2)
      place_rock((andesite_n - 0.2) * (10 + continent_blend * 20), Blocks.ANDESITE)

    // Place some normal stone
    var stone_n = terr.gen.getValue(x * 0.005 - 98.765, z * 0.005 + 34.621)
    place_rock(continent_blend * (20 + stone_n * 10), Blocks.STONE)

    // Gold. It's generally newer than coal (but less common)
    val gold_n = terr.gen.getValue(x * 0.005 + 673.924, z * 0.005 + 613.222)
    place_rock((gold_n - 0.6) * 7, Blocks.GOLD_ORE)

    // Coal seams
    val coal_n = terr.gen.getValue(x * 0.005 - 81.67, z * 0.005 - 6.62)
    // Coal blocks would be more realistic looking but not play as well
    place_rock((coal_n - 0.1) * 20, Blocks.COAL_ORE)

    // More stone
    stone_n = terr.gen.getValue(x * 0.005 - 98.765, z * 0.005 + 34.621)
    place_rock(20 + stone_n * 10, Blocks.STONE)

    // Iron. Not sure about the location
    val iron_n = terr.gen.getValue(x * 0.005 + 63.8, z * 0.005 + 63.721)
    place_rock((iron_n - 0.4) * 10, Blocks.IRON_ORE)

    // Diamonds aren't usually found in the crust, so I just stuck in some random ones
    // Note that this way they might not be the same between worlds with the same seed
    if (terr.rand.nextDouble() > 0.99)
      place_rock(1, Blocks.DIAMOND_ORE)
    // Same with emeralds, they're a little rarer in Minecraft
    if (terr.rand.nextDouble() > 0.994)
      place_rock(1, Blocks.EMERALD_ORE)

    // I can't find much about where lapis lazuli occurs, so I'll make small, rare veins here
    val lapis_n = terr.gen.getValue(x * 0.005 + 63.8, z * 0.005 - 3.721)
    place_rock((iron_n - 0.8) * 10, Blocks.LAPIS_ORE)

    // Granite occurs all over the place, generally low down
    // But not in the ocean
    val granite_n = terr.gen.getValue(x * 0.005 + 623.829, z * 0.005 + 328.9205)
    place_rock(continent_blend * (granite_n - 0.1) * 20, Blocks.GRANITE)

    // Redstone doesn't exist, but it's kind of like copper
    val copper_n = terr.gen.getValue(x * 0.005 + 8.888, z * 0.005 - 61.7261)
    place_rock(continent_blend * (copper_n - 0.3) * 6, Blocks.REDSTONE_ORE)

    // More granite below the copper, down to bedrock
    place_rock(continent_blend * 1000, Blocks.GRANITE)

    // Uplift and erosion
    val uplift = continent_blend * terr.gen.getValue(x * 0.003 + 63.1111, z * 0.003 - 222.2222) * 50 - 20
    if (uplift > 0)
      arr = Array.fill(uplift.toInt)(Blocks.GRANITE.getDefaultState()) ++ arr.dropRight(uplift.toInt)

    arr(0) = Blocks.BEDROCK.getDefaultState()

    arr
  }

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
      val r = terr.rain(start_x + x, start_z + z)

      val arr = make_rock(start_x + x, start_z + z, h, r)

      for (y <- 0 until arr.length) {
        val pos = new BlockPos(x, y, z)

        val block = arr(y)

        chunkIn.setBlockState(
          pos,
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
  }
}
