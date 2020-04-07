package terragen

import net.minecraft.world.gen.ChunkGenerator
import net.minecraft.world.World
import net.minecraft.world.biome.provider.BiomeProvider
import net.minecraft.world.biome.Biome
import net.minecraft.world.gen.{WorldGenRegion, Heightmap}
import net.minecraft.world.spawner.WorldEntitySpawner
import net.minecraft.world.chunk.IChunk
import net.minecraft.world.IWorld
import net.minecraft.util.SharedSeedRandom
import net.minecraft.util.math.BlockPos
import net.minecraft.block.{Block, Blocks, BlockState}
import net.minecraft.world.gen.feature.structure._
import net.minecraft.world.gen.feature._
import net.minecraft.world.gen.feature.template._
import net.minecraft.entity.EntityClassification
import net.minecraft.world.server.ServerWorld
import net.minecraft.village.VillageSiege
import net.minecraft.world.spawner.CatSpawner
import net.minecraft.world.spawner.PatrolSpawner
import net.minecraft.world.spawner.PhantomSpawner
import net.minecraft.util.math.ChunkPos

import scala.collection.mutable.ArrayStack
import scala.collection.JavaConverters._

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class ChunkGen[C <: net.minecraft.world.gen.GenerationSettings](
    world: World,
    terr: Terrain,
    config: C
) extends ChunkGenerator[C](world, terr, config) {
  val LOGGER = LogManager.getLogger()

  val phantomSpawner = new PhantomSpawner()
  val patrolSpawner = new PatrolSpawner()
  val catSpawner = new CatSpawner()
  val siegeSpawner = new VillageSiege()

  // Spawn special mobs
  override def spawnMobs(worldIn: ServerWorld, spawnHostileMobs: Boolean, spawnPeacefulMobs: Boolean) {
     phantomSpawner.tick(worldIn, spawnHostileMobs, spawnPeacefulMobs)
     patrolSpawner.tick(worldIn, spawnHostileMobs, spawnPeacefulMobs)
     catSpawner.tick(worldIn, spawnHostileMobs, spawnPeacefulMobs)
     siegeSpawner.func_225477_a(worldIn, spawnHostileMobs, spawnPeacefulMobs)
  }

  // Spawn passive mobs during worldgen
  override def spawnMobs(region: WorldGenRegion): Unit = {
    val i = region.getMainChunkX
    val j = region.getMainChunkZ
    val biome = region.getChunk(i, j).getBiomes()(0)
    val sharedseedrandom: SharedSeedRandom = new SharedSeedRandom()
    sharedseedrandom.setDecorationSeed(region.getSeed, i << 4, j << 4)
    WorldEntitySpawner.performWorldGenSpawning(region, biome, i, j, sharedseedrandom)
  }

  // Make sure structure-specific mobs can spawn
  override def getPossibleCreatures(creatureType: EntityClassification, pos: BlockPos): java.util.List[Biome.SpawnListEntry] = {
    if (Feature.SWAMP_HUT.func_202383_b(world, pos)) {
      if (creatureType == EntityClassification.MONSTER) {
        return Feature.SWAMP_HUT.getSpawnList
      }
      if (creatureType == EntityClassification.CREATURE) {
        return Feature.SWAMP_HUT.getCreatureSpawnList
      }
    } else if (creatureType == EntityClassification.MONSTER) {
      if (Feature.PILLAGER_OUTPOST.isPositionInStructure(world, pos)) {
        return Feature.PILLAGER_OUTPOST.getSpawnList
      }
      if (Feature.OCEAN_MONUMENT.isPositionInStructure(world, pos)) {
        return Feature.OCEAN_MONUMENT.getSpawnList
      }
    }
    super.getPossibleCreatures(creatureType, pos)
  }

  override def func_222529_a(x: Int, z: Int, ty: Heightmap.Type): Int = {
    val h = world.getHeight(ty, x, z)
    val r = if (h != 0) h else make_rock(x, z).length
    LOGGER.debug("Getting height, returning " + r)
    r
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
  def make_rock(x: Int, z: Int, do_subduction: Boolean = true): ArrayStack[BlockState] = {
    var arr = ArrayStack(Blocks.BEDROCK.getDefaultState)
    var y: Double = 1

    var i = 0
    def next(scale: Double): Double = {
      if (i > 63)
        LOGGER.fatal("RAN OUT OF COEFFICIENTS")
      val n = terr.fBm(x * scale * 0.00003 + COEFX(i), z * 0.00003 * scale + COEFZ(i), 6)
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

    // If we're at a plate boundary, is it convergent (=1) or divergent (=-1)?
    val convergent_n = terr.gen.getValue(x * 0.000005 + 238.2341, z * 0.000005 + 38.2341)
    val hotspot_dist = terr.hotspot(x, z)
    // How long has this crust been here? New crust is created/exposed at divergent faults and hotspots
    val age = 1 - terr.smoothstep(0.99, 1.0, 1 - hotspot_dist) - (plate_dist - 0.4).max(0).min(1) * ocean_blend * (1 - convergent_n - 0.5) * 28.6

    // TODO mix strata of the same type+age with each other

    // Before everything else, do subduction at convergent boundaries
    val subd = convergent_n * terr.smoothstep(0, 0.1, 0.5-plate_dist) * (0.8 + 0.2 * next(1))
    if (do_subduction && subd > 0) {
      val below = make_rock(x, z, false)
      // This is confusing because of the order of ArrayStack operations. Scala 2.13 fixes it all.
      arr ++= below.reverse.takeRight((subd * below.length).toInt)
    }

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
      place_rock((0.5 * rock.age - plate_dist.min(hotspot_dist)).max(0) * rock.getSizeAt(next(rock.scale), terr, x, arr.length, z, continent_blend, age + 0.2), rock.getBlock(red))
    }

    // Kind of volcanoes in young rock
    val lava_height = (0.5 - age * 0.5 - 0.4) / 0.1 * (1 + next(1)) * 60
    for (i <- 2 to lava_height.toInt.min(arr.length-3))
      arr(arr.length-i) = Blocks.LAVA.getDefaultState

    // Sedimentary rocks only form where there's sediment - flowing water or wind
    val sedimentary = (Math.pow(2 * (terr.rain_smooth(x, arr.length, z) - 0.5).abs, 0.5) - 0.2) / 0.8
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
        if (idx > 1 && arr.length > idx)
          arr(arr.length-idx) = rock.getBlock(red)
      }
    }

    val erosion = (next(1) - 0.2) * 30
    arr.drop(erosion.toInt.max(0))

    // Skip surface if this layer is being subducted
    if (!do_subduction)
      return arr

    // Surface
    var b = true
    var one = false
    // Ocean sand is hardcoded in
    if (arr.length < 70 && age > 0.3)
      place_rock(terr.smoothstep(0, 6, 70-arr.length) * (2 + next(1)) * terr.smoothstep(0.3, 1, age), if (red) Blocks.RED_SAND.getDefaultState else Blocks.SAND.getDefaultState)
    for (rock <- Strata.SURFACE) {
      var s = (rock.getSizeAt(next(rock.scale), terr, x, arr.length, z, continent_blend, Math.pow(age, 0.2))) * terr.smoothstep(rock.age, 1, age)
      s = s.ceil
      // Hack so grass doesn't get mixed with podzol and swamp grass on top of each other
      if (one && rock.max_size == 1)
        s = 0
      if (!one && rock.max_size == 1 && s >= 1)
        one = true

      // Smooth out by making the first surface layer (dirt or sand) fill gap
      //   between actual height and height with each stratum snapped to 1m
      if (b && s > 0) {
        b = false
        s += (y - arr.length) * terr.smoothstep(rock.age, 1, age)
      }
      place_rock(s, rock.getBlock(red))
    }
    // Smooth with sand if not anything else
    if (b && arr.length < 70 && age > 0.3)
      place_rock((y - arr.length) * terr.smoothstep(0.3, 1, age), if (red) Blocks.RED_SAND.getDefaultState else Blocks.SAND.getDefaultState)


    arr
  }

  val PLAINS_VILLAGE_CONFIG = new VillageConfig("village/plains/town_centers", 6)
  val MULGA_VILLAGE_CONFIG = new VillageConfig("village/savanna/town_centers", 6)
  val SNOW_VILLAGE_CONFIG = new VillageConfig("village/snowy/town_centers", 6)
  val DESERT_VILLAGE_CONFIG = new VillageConfig("village/desert/town_centers", 6)
  val TAIGA_VILLAGE_CONFIG = new VillageConfig("village/taiga/town_centers", 6)
  var VILLAGE_CONFIG = PLAINS_VILLAGE_CONFIG

  val OUTPOST_CONFIG = new PillagerOutpostConfig(0.004D)
  val MINESHAFT_CONFIG = new MineshaftConfig(0.004D, MineshaftStructure.Type.NORMAL)
  val SHIPWRECK_CONFIG = new ShipwreckConfig(false)
  val RUIN_CONFIG = new OceanRuinConfig(OceanRuinStructure.Type.COLD, 0.3F, 0.9F)
  val TREASURE_CONFIG = new BuriedTreasureConfig(0.01F)

  override def getStructureConfig[C <: IFeatureConfig](biome: Biome, structure: Structure[C]): C = structure match {
    // TODO change based on conditions
    case Feature.VILLAGE => {
      LOGGER.warn("Getting village config, passing " + VILLAGE_CONFIG.startPool)
      VILLAGE_CONFIG.asInstanceOf[C]
    }
    case Feature.PILLAGER_OUTPOST => OUTPOST_CONFIG.asInstanceOf[C]
    case Feature.MINESHAFT => MINESHAFT_CONFIG.asInstanceOf[C]
    case Feature.SHIPWRECK => SHIPWRECK_CONFIG.asInstanceOf[C]
    case Feature.OCEAN_RUIN => RUIN_CONFIG.asInstanceOf[C]
    case Feature.BURIED_TREASURE => TREASURE_CONFIG.asInstanceOf[C]
    case Feature.STRONGHOLD | Feature.DESERT_PYRAMID | Feature.JUNGLE_TEMPLE | Feature.IGLOO | Feature.SWAMP_HUT | Feature.WOODLAND_MANSION => IFeatureConfig.NO_FEATURE_CONFIG.asInstanceOf[C]
    case _ => {
      LOGGER.warn("No config for " + structure.getStructureName)
      IFeatureConfig.NO_FEATURE_CONFIG.asInstanceOf[C]
    }
  }

  override def hasStructure(biome: Biome, structure: Structure[_ <: net.minecraft.world.gen.feature.IFeatureConfig]): Boolean = Structures.ALL.contains(structure)

  override def initStructureStarts(chunk: IChunk, _gen: ChunkGenerator[_], temp_man: TemplateManager) {
    var rand = new SharedSeedRandom
    val chunkpos = chunk.getPos
    val x = chunkpos.getXStart + 8
    val z = chunkpos.getZStart + 8
    // It might not have generated yet
    val y = make_rock(x, z).length
    // if (y > 160)
    //   LOGGER.warn("/tp @s " + x + " " + (y + 5) + " " + z)
    val r = Rain(terr.rain(x, y, z))
    val t = Temp(terr.temp(x, y, z))
    for ((name, structure) <- Feature.STRUCTURES.asScala) {
      if (Structures.ALL.get(structure).map(_.check(r, t, y)).getOrElse(false)) {
        //LOGGER.info("Climate for " + name + " checks out")
        if (structure.hasStartAt(this, rand, chunkpos.x, chunkpos.z)) {
          LOGGER.info(name + " has start here")
          if (structure == Feature.VILLAGE) {
            // Switch village config to appropriate type
            if (r <= R.NORMAL && t >= T.SUBTROPIC)
              VILLAGE_CONFIG = MULGA_VILLAGE_CONFIG
            else if (t <= Temp(5))
              VILLAGE_CONFIG = SNOW_VILLAGE_CONFIG
            else if (r <= R.DRY)
              VILLAGE_CONFIG = DESERT_VILLAGE_CONFIG
            else if (r >= R.DAMP && t >= T.SUBTROPIC)
              VILLAGE_CONFIG = TAIGA_VILLAGE_CONFIG
            else
              VILLAGE_CONFIG = PLAINS_VILLAGE_CONFIG
          }

          val start = structure.getStartFactory.create(structure, chunkpos.x, chunkpos.z, ExtraBiomes.NULL_BIOME, net.minecraft.util.math.MutableBoundingBox.getNewBoundingBox(), 0, this.getSeed())
          start.init(this, temp_man, chunkpos.x, chunkpos.z, ExtraBiomes.NULL_BIOME)
          LOGGER.warn("passed init()")
          if (start.isValid)
            chunk.putStructureStart(structure.getStructureName, start)
        }
      }
    }
  }

  def max_chunk_height(pos: ChunkPos): Int = {
    terr.rand.setFeatureSeed(world.getSeed, pos.x, pos.z)

    val start_x = pos.getXStart
    val start_z = pos.getZStart

    var max = 0
    for (x <- 0 until 16;
         z <- 0 until 16) {
      val arr = make_rock(start_x + x, start_z + z)

      max = max.max(arr.length)
    }
    max
  }

  override def makeBase(worldIn: IWorld, chunkIn: IChunk): Unit = {
    terr.rand.setFeatureSeed(worldIn.getSeed, chunkIn.getPos.x, chunkIn.getPos.z)

    val start_x = chunkIn.getPos().getXStart()
    val start_z = chunkIn.getPos().getZStart()

    // This bit is to smooth out villages, it makes everything MUCH worse currently
    // import net.minecraft.world.gen.feature.jigsaw._
    //
    // var structures = ArrayStack[AbstractVillagePiece]()
    // var junctions = ArrayStack[JigsawJunction]()
    // var max_height_map = collection.mutable.Map[(Int, Int), Int]()
    // val st_name = Feature.VILLAGE.getStructureName
    // for (ref <- chunkIn.getStructureReferences(st_name).asScala) {
    //   val pos = new ChunkPos(ref)
    //   val ichunk = worldIn.getChunk(pos.x, pos.z)
    //   val start = ichunk.getStructureStart(st_name)
    //   if (start != null && start.isValid) {
    //     for (piece <- start.getComponents.asScala if piece.func_214810_a(pos, 12) && piece.isInstanceOf[AbstractVillagePiece]) {
    //       val v_piece = piece.asInstanceOf[AbstractVillagePiece]
    //       // I'm not checking for RIGID placement behaviour, I should try that as well though
    //       structures.push(v_piece)
    //
    //       {
    //         var max = 0
    //         for (i <- v_piece.getBoundingBox.minX to v_piece.getBoundingBox.maxX;
    //              j <- v_piece.getBoundingBox.minZ to v_piece.getBoundingBox.maxZ) {
    //                val h = make_rock(i, j).length
    //                max = max.max(h)
    //              }
    //        for (i <- v_piece.getBoundingBox.minX to v_piece.getBoundingBox.maxX;
    //             j <- v_piece.getBoundingBox.minZ to v_piece.getBoundingBox.maxZ) {
    //               if (max_height_map.get((i,j)).map(max < _).getOrElse(true))
    //                 max_height_map += (i,j) -> max
    //             }
    //       }
    //
    //       for (junction <- v_piece.getJunctions.asScala) {
    //         val x = junction.getSourceX
    //         val z = junction.getSourceZ
    //         var max = 0
    //         for (i <- x - 6 to x + 6;
    //              j <- z - 6 to z + 6) {
    //                val h = make_rock(i, j).length
    //                max = max.max(h)
    //              }
    //        for (i <- x - 6 to x + 6;
    //             j <- z - 6 to z + 6) {
    //               if (max_height_map.get((i,j)).map(max < _).getOrElse(true))
    //                 max_height_map += (i,j) -> max
    //             }
    //       }
    //     }
    //   }
    // }

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

      // Part of village smoothing
      // max_height_map.get((start_x + x, start_z + z)) match {
      //   case Some(h) => for (y <- arr.length to h) {
      //     val top = arr.top
      //     chunkIn.setBlockState(
      //       new BlockPos(x, y, z),
      //       top,
      //       false
      //     )
      //   }
      //   case None => ()
      // }
    }
  }
}
