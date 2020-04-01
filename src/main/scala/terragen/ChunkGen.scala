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

import scala.collection.mutable.ArrayStack

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class ChunkGen[C <: net.minecraft.world.gen.GenerationSettings](
    world: World,
    terr: Terrain,
    config: C
) extends ChunkGenerator[C](world, terr, config) {
  val LOGGER = LogManager.getLogger()

  override def func_222529_a(x: Int, z: Int, ty: Heightmap.Type): Int = {
    val h = world.getHeight(ty, x, z)
    if (h != 0) h else make_rock(x, z).length
  }

  override def generateSurface(chunkIn: IChunk): Unit = {
    val rand = new SharedSeedRandom()
    val pos = chunkIn.getPos()
    rand.setBaseChunkSeed(pos.x, pos.z)
    val biomes = chunkIn.getBiomes()

    val start_x = pos.getXStart()
    val start_z = pos.getZStart()

    for (x <- 0 until 16; z <- 0 until 16) {
      val h = chunkIn.getTopBlockY(Heightmap.Type.OCEAN_FLOOR_WG, x, z) + 1
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

  val COEFX = Array.fill(64){ terr.rand.nextDouble * 100 }
  val COEFZ = Array.fill(64){ terr.rand.nextDouble * 100 }

  // Returns a whole column of crust
  def make_rock(x: Int, z: Int): ArrayStack[BlockState] = {
    var arr = ArrayStack(Blocks.BEDROCK.getDefaultState)
    var y: Double = 1

    var i = 0
    def next(scale: Double): Double = {
      if (i > 63)
        LOGGER.fatal("RAN OUT OF COEFFICIENTS")
      val n = terr.fBm(x * scale * 0.00001 + COEFX(i), z * 0.00001 * scale + COEFZ(i), 6)
      i += 1
      n * 0.5 + 0.5
    }

    def place_rock(size: Double, rock: BlockState) {
      if (size <= 0)
        return
      // TODO
      y += size
      arr ++= Array.fill(size.toInt)(rock)
    }

    var (plate_dist, plate_height) = terr.plates(x, z)
    plate_dist += 0.05 * next(1)

    val continent_blend = plate_height.max(0)
    val ocean_blend = (1 - continent_blend).max(0)
    val red = terr.red_sand(x, z)

    val convergent_n = terr.gen.getValue(x * 0.0005 + 238.2341, z * 0.0005 + 38.2341)
    val hotspot_dist = terr.hotspot(x, z)
    // How long has this crust been here? New crust is created/exposed at divergent faults and hotspots
    val age = 1 - terr.smoothstep(0.99, 1.0, 1 - hotspot_dist) - (0.05 - plate_dist).max(0) * ocean_blend * (1 - convergent_n - 0.5) * 28.6

    // TODO mix strata of the same type+age with each other

    // Metamorphic rocks first
    for (rock <- Strata.METAMORPHIC) {
      place_rock(rock.getSizeAt(next(rock.scale), terr, x, arr.length, z, continent_blend, age), rock.getBlock(red))
    }

    // Then igneous intrusive
    for (rock <- Strata.IGNEOUS_I) {
      // Age doesn't matter here
      place_rock(rock.getSizeAt(next(rock.scale), terr, x, arr.length, z, continent_blend, 10), rock.getBlock(red))
    }

    // Then igneous extrusive
    for (rock <- Strata.IGNEOUS_E) {
      // The younger the rock, the less of it and the closer it is to plate boundaries/hotspots
      place_rock((0.5 * rock.age - plate_dist).max((0.05 - plate_dist)).max(0) * rock.getSizeAt(next(rock.scale), terr, x, arr.length, z, continent_blend, age + 0.2), rock.getBlock(red))
    }

    // Sedimentary rocks only form where there's sediment - flowing water or wind
    val sedimentary = terr.smoothstep(0.0, 0.5, (terr.rain_smooth(x, arr.length, z) - 0.5).abs)
    var a = true
    for (rock <- Strata.SEDIMENTARY) {
      // Age doesn't matter here
      if (a)
        place_rock(sedimentary * rock.getSizeAt(next(rock.scale), terr, x, arr.length, z, continent_blend, 10), rock.getBlock(red))
      a = false
    }

    // These are a little different - they're always one block replacing a random block we've already placed
    // Age corresponds directly to depth
    for (rock <- Strata.RANDOM) {
      if (terr.rand.nextDouble < rock.probability) {
        val idx = 100 - (rock.age * 100).toInt + terr.rand.nextInt(20) - 10
        if (idx > 0 && arr.length > idx)
          arr(arr.length-idx) = rock.getBlock(red)
      }
    }

    val erosion = (next(1) - 0.2) * 30
    arr.dropRight(erosion.toInt.max(0))

    // Surface
    var b = true
    var one = false
    // Ocean sand is hardcoded in
    if (arr.length < 70 && age > 0.3)
      place_rock(terr.smoothstep(0, 6, 70-arr.length) * (2 + next(1)) * age, if (red) Blocks.RED_SAND.getDefaultState else Blocks.SAND.getDefaultState)
    for (rock <- Strata.SURFACE) {
      var s = (rock.getSizeAt(next(rock.scale), terr, x, arr.length, z, continent_blend, age)) * age
      // Hack so grass doesn't get mixed with podzol and swamp grass on top of each other
      if (one && rock.max_size == 1)
        s = 0
      if (!one && rock.max_size == 1 && s >= 1)
        one = true

      // Smooth out by making the first surface layer (dirt or sand) fill gap
      //   between actual height and height with each stratum snapped to 1m
      if (b && s > 0) {
        b = false
        s += (y - arr.length) * age
      }
      place_rock(s, rock.getBlock(red))
    }
    // Smooth with sand if not anything else
    if (b && arr.length < 70 && age > 0.3)
      place_rock((y - arr.length) * age, if (red) Blocks.RED_SAND.getDefaultState else Blocks.SAND.getDefaultState)


    arr
  }

  override def makeBase(worldIn: IWorld, chunkIn: IChunk): Unit = {
    terr.rand.setFeatureSeed(worldIn.getSeed, chunkIn.getPos.x, chunkIn.getPos.z)

    val start_x = chunkIn.getPos().getXStart()
    val start_z = chunkIn.getPos().getZStart()

    for (x <- 0 until 16;
         z <- 0 until 16) {
      val arr = make_rock(start_x + x, start_z + z)

      for (y <- 1 to arr.length) {
        val pos = new BlockPos(x, y-1, z)

        val block = arr(arr.length - y)

        chunkIn.setBlockState(
          pos,
          block,
          false
        )
      }
      for (y <- arr.length until 64) {
        chunkIn.setBlockState(
          new BlockPos(x, y, z),
          config.getDefaultFluid(),
          false
        )
      }
    }
  }
}
