package terragen

import net.minecraft.world.biome.provider.BiomeProvider
import net.minecraft.world.biome.{Biome, Biomes}
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.gen.feature.structure.Structure
import net.minecraft.util.SharedSeedRandom
import scala.collection.JavaConversions._

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object ExtraBiomes {
  var COLD_DESERT: Biome = null

  def init() {
    if (COLD_DESERT == null)
      COLD_DESERT = new biomes.ColdDesert()
  }
}

class Terrain extends BiomeProvider {
  ExtraBiomes.init()

  val LOGGER = LogManager.getLogger()

  val rand = new SharedSeedRandom()

  val p = Array.fill(512) { rand.nextDouble }

  //var temp = Biome.BIOMES.toSeq.sortBy((x: Biome) => x.getDefaultTemperature)
  val surface = {
    var m = new java.util.HashSet[BlockState]
    for (biome <- Biome.BIOMES) {
      m.add(biome.getSurfaceBuilderConfig().getTop())
    }
    m
  }

  val gen = new net.minecraft.world.gen.SimplexNoiseGenerator(rand)

  def fBm(x: Double, z: Double, octaves: Int): Double = {
    var acc = 0.0
    val omega = 0.7
    var a = 1.0
    var s = 100.0

    for (_ <- 0 until octaves) {
      acc += gen.getValue(x * s, z * s) * a //hash(x * s, z * s, 19.08) * a
      a *= omega
      s *= 1.99
    }

    acc
  }

  def hash(x: Double, z: Double, m: Double): Double =
    p(((z.asInstanceOf[Int] * 16 + x.asInstanceOf[Int] + m).asInstanceOf[Int] % 512 + 512) % 512)

  def smoothstep(e0: Double, e1: Double, x: Double): Double = {
    val t = ((x - e0) / (e1 - e0)).min(1).max(0)
    t * t * (3 - 2 * t)
  }

  // Returns (distance to border, continent height)
  def voronoi(x: Double, z: Double): (Double, Double) = {
    val nx = Math.floor(x)
    val nz = Math.floor(z)
    val fx = x - nx
    val fz = z - nz

    var mbx = 0
    var mbz = 0
    var mrx = 0.0
    var mrz = 0.0

    var min_d2 = 1000.0

    var mh = 0.0

    for (i <- -1 to 1; j <- -1 to 1) {
      val rx = i + hash(j + nz, i + nx, 1234) - fx
      val rz = j + hash(j + nz, i + nx, 9823) - fz
      val d2 = rx * rx + rz * rz

      // Continent with random width
      val h_base = hash(j + nz, i + nx, -3287) + 0.15
      val h = smoothstep(h_base, h_base + 0.3, 1 - Math.sqrt(d2))

      mh = mh.max(h)
      if (d2 < min_d2) {
        min_d2 = d2
        mbx = i
        mbz = j
        mrx = rx
        mrz = rz
      }
    }

    for (i <- -2 to 2; j <- -2 to 2) {
      val bx = mbx + i
      val bz = mbz + j
      val rx = bx + hash(bz + nz, bx + nx, 1234) - fx
      val rz = bz + hash(bz + nz, bx + nx, 9823) - fz

      val d_point = Math.sqrt(Math.pow(rx - mrx, 2) + Math.pow(rz - mrz, 2))
      val d2_line = 0.5 * ((mrx + rx) * (rx - mrx) / d_point + (mrz + rz) * (rz - mrz) / d_point)

      if (Math.pow(mrx - rx, 2) + Math.pow(mrz - rz, 2) > 0.000001)
        min_d2 = min_d2.min(d2_line)
    }

    (min_d2, mh)
  }

  def height(x: Int, z: Int): Int = {
    val px = x*0.0001
    val pz = z*0.0001

    val (plate_dist, plate_height) = voronoi(px * 2, pz * 2)

    val h = (1.2 * smoothstep(-1.0, 0.8, fBm(px * 0.1, pz * 0.1, 6)) - 0.2) * 0.7 * smoothstep(
      0.8,
      1.0,
      1 - plate_dist
    ) + plate_height + 0.5 * fBm(px * 0.05, pz * 0.05, 6)
    //val h = 0.6 + 0.5 * fBm(px * 0.05, pz * 0.05, 6)
    (h * 80).max(3).asInstanceOf[Int]
  }

  val pole_cx = rand.nextDouble()
  val pole_cz = rand.nextDouble()

  def temp(x: Int, z: Int): Double = {
    val px = x*0.0001
    val pz = z*0.0001

    val (plate_dist, plate_height) = voronoi(px * 2, pz * 2)
    // Fake poles with Voronoi - pole_dist is toward poles - away from cell edges
    val (pole_dist, _) = voronoi(px * 0.05 + pole_cx * 31.24, pz * 0.05 + pole_cz * 98.67)

    val h = (1.2 * smoothstep(-1.0, 0.8, fBm(px * 0.1, pz * 0.1, 6)) - 0.2) * 0.7 * smoothstep(
      0.8,
      1.0,
      1 - plate_dist
    ) + plate_height + 0.5 * fBm(px * 0.05, pz * 0.05, 6)

    // Roughly in Celsius
    val t = (
      70 +
      + plate_height * plate_dist * 20 // Hotter further from the sea
      - Math.pow(pole_dist, 0.125) * 75 // And further from the poles
      - h * 8 // Colder at high altitudes
      + fBm(243.23+px*0.01, 987.96+pz*0.01, 6) * 10 // Add some noise
    )
    t + rand.nextDouble() * 8 - 4
  }

  def rain(x: Int, z: Int): Double = {
    val px = x*0.0001
    val pz = z*0.0001

    val (plate_dist, plate_height) = voronoi(px * 2, pz * 2)

    val h = (1.2 * smoothstep(-1.0, 0.8, fBm(px * 0.1, pz * 0.1, 6)) - 0.2) * 0.7 * smoothstep(
      0.8,
      1.0,
      1 - plate_dist
    ) + plate_height + 0.5 * fBm(px * 0.05, pz * 0.05, 6)

    val r = (
      (1-h) // Wetter at lower altitudes
      * (0.2 + 0.5 * (1-plate_height*plate_dist)) // And closer to the sea
      + (0.4 + 0.2 * fBm(432.32 - px*0.01, -987.62 + pz*0.01, 6)) // Add some noise
    )
    r + rand.nextDouble() * 0.1 - 0.05
  }

  override def findBiomePosition(x: Int, z: Int, range: Int, biomes: java.util.List[Biome], random: java.util.Random): BlockPos = null
  override def getBiome(x: Int, z: Int): Biome = {
    val h = height(x, z) + rand.nextDouble() * 6 - 3
    val t = temp(x, z)
    val r = rain(x, z)
    val mush_noise = gen.getValue(x * 0.0000005 - 137.7, z * 0.0000005 + 5874.2)

    if (h < 40)
      // Deep Ocean
      if (t < 0.0)
        Biomes.DEEP_FROZEN_OCEAN
      else if (t < 5.0)
        Biomes.DEEP_COLD_OCEAN
      else if (t < 10.0)
        Biomes.DEEP_OCEAN
      else if (t < 15.0)
        Biomes.DEEP_LUKEWARM_OCEAN
      else
        Biomes.DEEP_WARM_OCEAN
    else if (mush_noise > 0.85)
      // Mushroom fields (TODO: more mushroom biomes)
      if (h < 70)
        Biomes.MUSHROOM_FIELD_SHORE
      else
        Biomes.MUSHROOM_FIELDS
    else if (h < 64)
      // Ocean (TODO: rivers)
      if (t < 0.0)
        Biomes.FROZEN_OCEAN
      else if (t < 5.0)
        Biomes.COLD_OCEAN
      else if (t < 10.0)
        Biomes.OCEAN
      else if (t < 15.0)
        Biomes.LUKEWARM_OCEAN
      else
        Biomes.WARM_OCEAN
    else if (h < 70) {
      // Beach
      val n = gen.getValue(x * 0.000005 + 938.1, z * 0.000005 - 396.1)
      if (t < 6)
        Biomes.SNOWY_BEACH
      else if (t < 10 && n > 0.5)
        Biomes.STONE_SHORE
      else
        Biomes.BEACH
    }
    else if (h > 100) {
      // Mountains
      val n = gen.getValue(x * 0.000005 + 938.1, z * 0.000005 - 396.1)
      if (t < 0)
        Biomes.SNOWY_MOUNTAINS
      else if (t < 6)
        Biomes.SNOWY_TAIGA_MOUNTAINS
      else if (n > 0.6)
        Biomes.TAIGA_MOUNTAINS
      else if (n > -0.2)
        Biomes.WOODED_MOUNTAINS
      else if (n > -0.25)
        Biomes.GRAVELLY_MOUNTAINS
      else
        Biomes.MOUNTAINS
    } else
      // Normal land
      if (r < 0.3)
        if (t < 5) {
          ExtraBiomes.COLD_DESERT.getDefaultTemperature()
          ExtraBiomes.COLD_DESERT
        } else
          Biomes.DESERT
      else if (t > 20 && r > 0.6)
        // Tropics
        if (gen.getValue(x * 0.000005 - 983.6, z * 0.000005 - 234.8) > 0.7)
          Biomes.BAMBOO_JUNGLE
        else
          Biomes.JUNGLE
      else if (r > 0.6)
        Biomes.SWAMP
      else if (t > 14)
        // Temperate savannah
        if (gen.getValue(x * 0.000005 + 683.6, z * 0.000005 - 834.8) > 0.7)
          Biomes.SAVANNA
        else if (gen.getValue(x * 0.000005 + 683.6, z * 0.000005 - 834.8) > 0.5)
          Biomes.SUNFLOWER_PLAINS
        else
          Biomes.PLAINS
      else if (t > 6) {
        // Temperate forest
        val n = gen.getValue(x * 0.000005 + 938.1, z * 0.000005 - 396.1)
        if (n > 0.9)
          Biomes.GIANT_SPRUCE_TAIGA
        else if (n > 0.6)
          Biomes.TAIGA
        else if (n > 0.2)
          Biomes.TALL_BIRCH_FOREST
        else if (n > 0.0)
          Biomes.BIRCH_FOREST
        else if (n > -0.4)
          Biomes.DARK_FOREST
        else if (n > -0.6)
          Biomes.FLOWER_FOREST
        else
          Biomes.FOREST
      }
      else if (t < 0) {
        // Too cold for trees
        val n = gen.getValue(x * 0.000005 - 28.1, z * 0.000005 + 96.1)
        if (n > 0.7)
          Biomes.ICE_SPIKES
        else
          Biomes.SNOWY_TUNDRA
      }
      else
        // Cold but not too cold for trees; we need more snowy forests!
        Biomes.SNOWY_TAIGA
  }
  override def getBiomes(startX: Int, startZ: Int, width: Int, length: Int, cacheFlag: Boolean): Array[Biome] = {
    (0 until length) flatMap (x => (0 until width) map (z => (x, z))) map ({ case (x, z) => getBiome(startX + x, startZ + z) }) toArray
  }
  override def getBiomesInSquare(centerX: Int, centerZ: Int, sideLength: Int): java.util.Set[Biome] = {
    var x = new java.util.HashSet[Biome]()
    x.addAll(getBiomes(centerX - sideLength / 2, centerZ - sideLength / 2, sideLength, sideLength, false).toList)
    x
  }
  override def getSurfaceBlocks(): java.util.Set[BlockState] = surface
  override def hasStructure(structureIn: Structure[_]): Boolean = false
}
