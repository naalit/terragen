package terragen

import net.minecraft.block.{BlockState, Blocks}
import net.minecraftforge.registries.ForgeRegistryEntry
import scala.collection.mutable.ArrayStack

object RockType extends Enumeration {
  type RockType = Value
  val
    // Extrusive igneous rocks are formed from lava that cools on the surface, exposed to the air. E.g. basalt, andesite
    IGNEOUS_EXTRUSIVE,
    // Intrusive igneous rocks are formed from magma that cools while underground. E.g. granite, gabbro
    IGNEOUS_INTRUSIVE,
    // Sedimentary rocks are formed from small pieces of things like shells or other rocks that stick together, coalesced by wind or water. E.g. sandstone, limestone
    SEDIMENTARY,
    // Metamorphic rocks form when other rocks are deep underground for a long time. E.g. marble, gneiss
    METAMORPHIC,
    // Generate in random columns (low down) without messing with other strata
    // These aren't really a thing, but I'm using it for things like diamonds which aren't normally found in the crust
    RANDOM_ORE,
    // Lets the stratum system generate surface as well, even though they're not rocks. E.g. grass, podzol
    // These don't generate under water
    SURFACE
      = Value
}

import RockType._

// Probability 1 means it's present in every column, 0 means none
// Sizes are y-sizes
// Continent and ocean refer to whether the rock occurs on that type of plate. Both types include land and water.
class Stratum(rock: BlockState, val probability: Double, max_size: Double, val ty: RockType) extends ForgeRegistryEntry[Stratum] with Ordered[Stratum] {
  var age = 0.5
  var min_size: Double = 1
  var in_ocean = true
  var in_continent = true
  var makes_cliffs = false
  var conditions = CRange(Temp(-1000), Temp(1000), Rain(-1000), Rain(1000))
  var red: BlockState = null
  var max_age = 20.0

  // Sort oldest first
  def compare(that: Stratum) = -age.compare(that.age)

  def getSizeAt(noise: Double, terr: Terrain, x: Int, y: Int, z: Int, continent_blend: Double, _age: Double): Double = {
    if ((probability > 1 && noise < (1 - probability))
      || _age < age || _age > max_age
      || (conditions.has_r && !conditions.check(Rain(terr.rain(x, y, z))))
      || (conditions.has_t && !conditions.check(Temp(terr.temp(x, y, z))))
      || !conditions.check(y)
      || (ty == RockType.SURFACE && y <= 64)
      )
      return 0

    var plate_fac = if (in_ocean && !in_continent) 1 - continent_blend else if (!in_ocean && in_continent) continent_blend else 1
    if (makes_cliffs)
      plate_fac = terr.smoothstep(0.8, 0.82, plate_fac)
    if (ty == RockType.SURFACE)
      plate_fac *= terr.smoothstep(64, 70, y)

    if (probability >= 1)
      plate_fac * (min_size + noise.max(0) * (max_size - min_size))
    else
      plate_fac * (noise - (1 - probability)) / (1 - probability) * max_size
  }

  def getBlock(_red: Boolean): BlockState = if (red == null) rock else if (_red) red else rock

  def cliff: Stratum = {
    makes_cliffs = true
    this
  }

  // Register a red variant, for sand and sandstone
  def red_variant(v: BlockState): Stratum = {
    red = v
    this
  }

  // Age is a modifier to change distribution within rock types. For example, basalt's age is 0.3 but andesite's is 0.7
  def age(x: Double): Stratum = {
    age = x
    this
  }

  // Marks that this rock is only present in crust younger than the specified age
  def max_age(x: Double): Stratum = {
    max_age = x
    this
  }

  // For when probability=1
  def min_size(x: Double): Stratum = {
    min_size = x
    this
  }

  def ocean: Stratum = {
    in_ocean = true
    in_continent = false
    this
  }

  def continent: Stratum = {
    in_ocean = false
    in_continent = true
    this
  }

  def range(r: HRange): Stratum = {
    conditions = r
    this
  }
}

object Strata {
  var TO_REGISTER = Array(
    // Currently our one and only metamorphic rock
    new Stratum(TBlocks.MARBLE.getDefaultState, 0.3, 20, RockType.METAMORPHIC).continent.age(0.6).setRegistryName("marble"),

    new Stratum(Blocks.GRANITE.getDefaultState, 1, 50, RockType.IGNEOUS_INTRUSIVE).min_size(20).continent.age(0.9).setRegistryName("granite"),
    new Stratum(Blocks.REDSTONE_ORE.getDefaultState, 0.35, 4, RockType.IGNEOUS_INTRUSIVE).continent.age(0.9).setRegistryName("copper"),
    new Stratum(Blocks.STONE.getDefaultState, 1, 50, RockType.IGNEOUS_INTRUSIVE).age(0.4).min_size(20).continent.setRegistryName("stone"),
    new Stratum(Blocks.IRON_ORE.getDefaultState, 0.3, 7, RockType.IGNEOUS_INTRUSIVE).setRegistryName("iron"),
    new Stratum(Blocks.LAPIS_ORE.getDefaultState, 0.1, 4, RockType.IGNEOUS_INTRUSIVE).setRegistryName("lapis"),
    new Stratum(Blocks.COAL_ORE.getDefaultState, 0.45, 10, RockType.SEDIMENTARY).age(0.7).setRegistryName("coal"),
    new Stratum(Blocks.GOLD_ORE.getDefaultState, 0.2, 6, RockType.IGNEOUS_INTRUSIVE).setRegistryName("gold"),
    new Stratum(Blocks.ANDESITE.getDefaultState, 0.4, 20, RockType.IGNEOUS_EXTRUSIVE).age(0.7).setRegistryName("andesite"),
    new Stratum(TBlocks.BASALT.getDefaultState, 0.4, 20, RockType.IGNEOUS_EXTRUSIVE).age(0.3).setRegistryName("basalt"),
    new Stratum(Blocks.OBSIDIAN.getDefaultState, 0.4, 5, RockType.IGNEOUS_EXTRUSIVE).age(0.1).setRegistryName("obsidian"),
    // I'm pretending this is gabbro too - they're pretty similar
    new Stratum(Blocks.DIORITE.getDefaultState, 1, 20, RockType.IGNEOUS_INTRUSIVE).age(0.2).min_size(5).ocean.setRegistryName("gabbro"),

    new Stratum(TBlocks.LIMESTONE.getDefaultState, 0.5, 40, RockType.SEDIMENTARY).continent.cliff.setRegistryName("limestone"),
    new Stratum(Blocks.SANDSTONE.getDefaultState, 0.4, 10, RockType.SEDIMENTARY).age(0.3).red_variant(Blocks.RED_SANDSTONE.getDefaultState).setRegistryName("sandstone"),
    new Stratum(Blocks.CLAY.getDefaultState, 0.25, 6, RockType.SEDIMENTARY).range(CRange(Temp(-1000), Temp(1000), R.DAMP, R.ANY)).setRegistryName("clay"),

    new Stratum(Blocks.DIAMOND_ORE.getDefaultState, 0.005, 1, RockType.RANDOM_ORE).age(0.9).setRegistryName("diamond"),
    new Stratum(Blocks.EMERALD_ORE.getDefaultState, 0.002, 1, RockType.RANDOM_ORE).age(0.84).setRegistryName("emerald"),

    new Stratum(Blocks.DIRT.getDefaultState, 1, 4, RockType.SURFACE).range(CRange(Temp(-1000), Temp(1000), R.DRY, R.ANY)).age(0.7).setRegistryName("dirt"),
    new Stratum(Blocks.SAND.getDefaultState, 1, 4, RockType.SURFACE).range(CRange(Temp(-1000), Temp(1000), R.DESERT, R.NORMAL)).age(0.6).red_variant(Blocks.RED_SAND.getDefaultState).setRegistryName("sand_surface"),
    new Stratum(Blocks.GRASS_BLOCK.getDefaultState, 1, 1, RockType.SURFACE).range(CRange(T.SUBPOLAR, T.ANY, R.DRY, R.WET)).setRegistryName("grass"),
    // Swamp grass
    new Stratum(Blocks.GRASS_BLOCK.getDefaultState, 1, 1, RockType.SURFACE).range(CRange(T.SUBPOLAR, T.SUBTROPIC, R.WET, R.ANY)).setRegistryName("swamp_grass"),
    new Stratum(Blocks.PODZOL.getDefaultState, 1, 1, RockType.SURFACE).range(CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY)).setRegistryName("podzol"),
    new Stratum(Blocks.COARSE_DIRT.getDefaultState, 1, 1, RockType.SURFACE).age(0.2).max_age(0.5).setRegistryName("coarse_dirt")
  )

  // These are sorted by age, oldest to youngest
  var METAMORPHIC: Array[Stratum] = null
  var IGNEOUS_E: Array[Stratum] = null
  var IGNEOUS_I: Array[Stratum] = null
  var SEDIMENTARY: Array[Stratum] = null
  var SURFACE: Array[Stratum] = null
  var RANDOM: Array[Stratum] = null
}
