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
      acc += gen.getValue(x * s, z * s) * a
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

  def mix(x: Double, y: Double, a: Double): Double = x*(1-a)+y*a

  // Returns (Rough distance to border, continental shelf blend)
  def voronoi(x: Double, z: Double): (Double, Double) = {
    // Smoothing factor
    val w = 0.2
    val nx = Math.floor(x)
    val nz = Math.floor(z)
    val fx = x - nx
    val fz = z - nz

    var min_d = 8.0
    var min_h = 0.0

    var mi = 0
    var mj = 0
    var mrx = 0.0
    var mrz = 0.0

    for (i <- -1 to 1; j <- -1 to 1) {
      val rx = i + hash(j + nz, i + nx, 1234) - fx
      val rz = j + hash(j + nz, i + nx, 9823) - fz
      val d  = Math.sqrt(rx * rx + rz * rz)

      var h = smoothstep(0.5, 0.5, hash(j + nz, i + nx, -3287))
      // Force a continent near spawn so you don't spawn in the middle of the sea
      // That's off temporarily

      var b = (0.5+0.5*(min_d-d)/w).min(1).max(0)
      var c = b*(1-b)*w/(1+3*w)

      if (d < min_d) {
        mi = i
        mj = j
        mrx = rx
        mrz = rz
      }

      min_d = mix(min_d, d, b) - c
      min_h = mix(min_h, h, b) - c
    }

    var min_d2 = 8.0
    // Second pass to find exact distances to edges - needed for plate borders
    for (i <- -2 to 2; j <- -2 to 2) {
      val rx = mi + i + hash(mj + j + nz, mi + i + nx, 1234) - fx
      val rz = mi + j + hash(mj + j + nz, mi + i + nx, 9823) - fz
      val d2 = Math.sqrt((mrx - rx) * (mrx - rx) + (mrz - rz) * (mrz - rz))

      if (d2 > 0.00001)
        min_d2 = min_d2.min(0.5*(mrx+rx)*(rx-mrx)/d2 + 0.5*(mrz+rz)*(rz-mrz)/d2)
    }

    (min_d2, smoothstep(0.5, 0.4, min_d)*min_h)
  }

  val SCALE = 0.0003

  def plates(x: Int, z: Int): (Double, Double) = {
    val px = x*SCALE
    val pz = z*SCALE

    voronoi(px * 2, pz * 2)
  }

  def red_sand(x: Int, z: Int): Boolean = gen.getValue(x * 0.0005 + 32.29487, z * 0.0005 - 932.2674) + rand.nextDouble() * 0.05 > 0.2

  val pole_cx = rand.nextDouble()
  val pole_cz = rand.nextDouble()

  def temp(x: Int, y:Int, z: Int): Double = {
    val px = x*SCALE
    val pz = z*SCALE

    val (plate_dist, plate_height) = voronoi(px * 2, pz * 2)
    // Fake poles with Voronoi - pole_dist is toward poles - away from cell edges
    val (pole_dist, _) = voronoi(px * 0.05 + pole_cx * 31.24, pz * 0.05 + pole_cz * 98.67)

    val h = y / 80

    // in Celsius
    // Formula: 15+\left(4-4\max\left(h-1,0\right)^{8}+70\left(z-0.3\right)+5x\right)\left(0.5+p\right)
    val t = (
      15 + // Base temperature
      (4-4*Math.pow((h-1).max(0), 8) // Colder higher up
       +70*(pole_dist-0.3) // Colder at poles
       +5*fBm(243.23+px*0.01, 987.96+pz*0.01, 6)) // Noise
      * (0.5+0.5*plate_dist*plate_height) // Water regulates the temperature
    )
    t + rand.nextDouble() * 2 - 1
  }

  def min(xs: Double*): Double = {
    xs.reduce((x, y) => x.min(y))
  }

  // Bad lakes & rivers
  def river(x: Int, z: Int): Double = {
    val RSC = 2.0
    val i_SC = 1/RSC

    val px = x*SCALE
    val pz = z*SCALE

    val rx0 = i_SC * (px*RSC).floor + 0.2*fBm(pz*0.01+(px*RSC).floor*2.1432, pz*0.01-(px*RSC).floor*1.8312, 6)
    val rx1 = i_SC * ((px*RSC).floor+1) + 0.2*fBm(pz*0.01+((px*RSC).floor+1)*2.1432, pz*0.01-((px*RSC).floor+1)*1.8312, 6)
    val rz0 = i_SC * (pz*RSC).floor + 0.2*fBm(px*0.01+(pz*RSC).floor*8.8324, px*0.01-(pz*RSC).floor*5.1312, 6)
    val rz1 = i_SC * ((pz*RSC).floor+1) + 0.2*fBm(px*0.01+((pz*RSC).floor+1)*8.8324, px*0.01-((pz*RSC).floor+1)*5.1312, 6)

    val r = 1.0-10*min( //smoothstep(0.010+gen.getValue(px+3024.24982347, pz-239847.289347)*0.005, 0.0, min(
      (px-rx0).abs,
      (px-rx1).abs,
      (pz-rz0).abs,
      (pz-rz1).abs
    ) //)
    // Lakes where two rivers intersect
    // Temporarily disabled
    // val l = smoothstep(0.003+gen.getValue(px+302.4982347, pz-23984.89347)*0.002, 0.0,
    //   min((px-rx0).abs, (px-rx1).abs) * min((pz-rz0).abs, (pz-rz1).abs) )

    r //.max(l)
  }

  // Without random blend
  def rain_smooth(x: Int, y: Int, z: Int): Double = {
    val px = x*SCALE
    val pz = z*SCALE

    val (plate_dist, plate_height) = voronoi(px * 2, pz * 2)

    val h = y / 80.0

    // Formula: 0.8+\left(1-\max\left(h,0.75\right)\right)\cdot0.2-1.2p+0.5x
    (
      0.8 + (1-h.max(0.75)) * 0.2 // Wetter at lower altitudes (above sea level)
      - 1.2 * 0.5 * plate_height * plate_dist // And closer to the sea
      + 0.4 * river(x, z) // Or near a river or lake
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
  override def hasStructure(structureIn: Structure[_]): Boolean = true
}
