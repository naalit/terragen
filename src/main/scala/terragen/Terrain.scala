package terragen

import net.minecraft.world.biome.provider.BiomeProvider
import net.minecraft.world.biome.{Biome, Biomes}
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.world.gen.feature.structure.Structure
import net.minecraft.util.SharedSeedRandom
import scala.collection.JavaConverters._

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object ExtraBiomes {
  var COLD_DESERT: Biome = null
  var NULL_BIOME: Biome = null

  def init(terr: Terrain) {
    if (COLD_DESERT == null)
      COLD_DESERT = new biomes.ColdDesert()
    if (NULL_BIOME == null)
      NULL_BIOME = new biomes.NullBiome(terr)
  }
}

class Terrain extends BiomeProvider {
  ExtraBiomes.init(this)

  val LOGGER = LogManager.getLogger()

  val rand = new SharedSeedRandom()

  def reseed(seed: Long): Unit = {
    rand.setSeed(seed)
    gen = new net.minecraft.world.gen.SimplexNoiseGenerator(rand)
    p = Array.fill(512) { rand.nextDouble }
  }

  var p = Array.fill(512) { rand.nextDouble }

  val surface = {
    var m = new java.util.HashSet[BlockState]
    for (biome <- Biome.BIOMES.asScala) {
      m.add(biome.getSurfaceBuilderConfig().getTop())
    }
    m
  }

  var gen = new net.minecraft.world.gen.SimplexNoiseGenerator(rand)

  def fBm(x: Double, z: Double, octaves: Int): Double = {
    var acc = 0.0
    val omega = 0.5
    var a = 1.0
    var s = 130.0

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

  def hotspot(x: Double, z: Double): Double = {
    val nx = Math.floor(x * 0.00001)
    val nz = Math.floor(z * 0.00001)
    val fx = x * 0.00001 - nx
    val fz = z * 0.00001 - nz

    var min_d2 = 1000.0
    for (i <- -1 to 1; j <- -1 to 1) {
      val rx = i + hash(j + nz, i + nx, 8234) - fx
      val rz = j + hash(j + nz, i + nx, 3333) - fz
      val d2 = rx * rx + rz * rz

      if (d2 < min_d2) {
        min_d2 = d2
      }
    }

    min_d2
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
      var h_base = hash(j + nz, i + nx, -3287) + 0.15
      // Force a continent near spawn so you don't spawn in the middle of the sea
      if (i + nx == 0 && j + nz == 0)
        h_base -= 0.5
      val h = smoothstep(h_base, h_base + 0.06, 1 - Math.sqrt(d2))

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

  def plates(x: Int, z: Int): (Double, Double) = {
    val px = x*0.0001
    val pz = z*0.0001

    voronoi(px * 2, pz * 2)
  }

  def red_sand(x: Int, z: Int): Boolean = gen.getValue(x * 0.0005 + 32.29487, z * 0.0005 - 932.2674) + rand.nextDouble() * 0.05 > 0.2

  val pole_cx = rand.nextDouble()
  val pole_cz = rand.nextDouble()

  def temp(x: Int, y:Int, z: Int): Double = {
    val px = x*0.0001
    val pz = z*0.0001

    val (plate_dist, plate_height) = voronoi(px * 2, pz * 2)
    // Fake poles with Voronoi - pole_dist is toward poles - away from cell edges
    val (pole_dist, _) = voronoi(px * 0.05 + pole_cx * 31.24, pz * 0.05 + pole_cz * 98.67)

    val h = y / 80

    // in Celsius
    // Formula: 15+\left(4-4h^{8}+70\left(l-0.3\right)+5x\right)\left(0.5+dp\right)
    val t = (
      15 + // Base temperature
      (4-4*Math.pow(h, 8) // Colder higher up
       +70*(pole_dist-0.3) // Colder at poles
       +5*fBm(243.23+px*0.01, 987.96+pz*0.01, 6)) // Noise
      * (0.5+plate_dist*plate_height) // Water regulates the temperature
    )
    t + rand.nextDouble() * 4 - 2
  }

  // Without random blend
  def rain_smooth(x: Int, y: Int, z: Int): Double = {
    val px = x*0.0001
    val pz = z*0.0001

    val (plate_dist, plate_height) = voronoi(px * 2, pz * 2)

    val h = y / 80

    // Formula: 0.8+\left(1-h\right)\cdot0.2-1.2pd+0.5x
    (
      0.8 + (1-h) * 0.2 // Wetter at lower altitudes
      - 1.2 * plate_height * plate_dist // And closer to the sea
      + 0.5 * fBm(432.32 - px*0.005, -987.62 + pz*0.005, 6) // Add some noise
    )
  }

  def rain(x: Int, y: Int, z: Int): Double = {
    rain_smooth(x, y, z) + rand.nextDouble() * 0.1 - 0.05
  }

  override def findBiomePosition(x: Int, z: Int, range: Int, biomes: java.util.List[Biome], random: java.util.Random): BlockPos = null
  override def getBiome(x: Int, z: Int): Biome = ExtraBiomes.NULL_BIOME
  override def getBiomes(startX: Int, startZ: Int, width: Int, length: Int, cacheFlag: Boolean): Array[Biome] = {
    (0 until length) flatMap (x => (0 until width) map (z => (x, z))) map ({ case (x, z) => getBiome(startX + x, startZ + z) }) toArray
  }
  override def getBiomesInSquare(centerX: Int, centerZ: Int, sideLength: Int): java.util.Set[Biome] = {
    var x = new java.util.HashSet[Biome]()
    x.add(ExtraBiomes.NULL_BIOME)
    //x.addAll(getBiomes(centerX - sideLength / 2, centerZ - sideLength / 2, sideLength, sideLength, false).toList)
    x
  }
  override def getSurfaceBlocks(): java.util.Set[BlockState] = surface
  override def hasStructure(structureIn: Structure[_]): Boolean = false
}
