package terragen.biomes

import terragen._
import net.minecraft.entity.EntityType
import net.minecraft.entity.EntityClassification
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.Biome.SpawnListEntry
import net.minecraft.world.biome.DefaultBiomeFeatures
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilder
import net.minecraft.world.gen.feature.structure.MineshaftConfig
import net.minecraft.world.gen.feature.structure.MineshaftStructure
import net.minecraft.world.gen.{feature => f}
import net.minecraft.world.gen.feature.{Feature, IFeatureConfig, ConfiguredFeature, NoFeatureConfig, ProbabilityConfig, BushConfig, GrassFeatureConfig}
import net.minecraft.world.gen.{GenerationStage, ChunkGenerator}
import net.minecraft.util.SharedSeedRandom
import net.minecraft.util.math.{BlockPos, MathHelper}
import net.minecraft.world.{IWorld, GrassColors, FoliageColors}
import net.minecraft.world.chunk.IChunk
import net.minecraft.block.{BlockState, Blocks}
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.api.distmarker.OnlyIn
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraft.world.gen.Heightmap

import java.{util => ju}
import scala.util.control.Breaks._
import scala.collection.JavaConverters._

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class NullBiome(terr: Terrain) extends Biome(new Biome.Builder().surfaceBuilder(SurfaceBuilder.DEFAULT, SurfaceBuilder.AIR_CONFIG).precipitation(Biome.RainType.RAIN).category(Biome.Category.EXTREME_HILLS).depth(0.125f).scale(0.05f).temperature(0.2f).downfall(0.5f).waterColor(4159204).waterFogColor(329011).parent(null)) {
  val LOGGER = LogManager.getLogger()

  val zombie_entry = new SpawnListEntry(EntityType.ZOMBIE, 95, 4, 4)
  val arid_entries = List(new SpawnListEntry(EntityType.ZOMBIE, 15, 4, 4), new SpawnListEntry(EntityType.HUSK, 80, 4, 4))
  val llama_entry =  new SpawnListEntry(EntityType.LLAMA, 100, 4, 6)
  val turtle_entry = new SpawnListEntry(EntityType.TURTLE, 5, 2, 5)
  val jungle_entries = List(new SpawnListEntry(EntityType.PANDA, 1, 1, 2), new SpawnListEntry(EntityType.PARROT, 40, 1, 2))

  val med_water = List(new SpawnListEntry(EntityType.DOLPHIN, 1, 1, 2), new SpawnListEntry(EntityType.COD, 10, 3, 6))
  val cold_water = List(new SpawnListEntry(EntityType.SALMON, 15, 1, 5), new SpawnListEntry(EntityType.COD, 15, 3, 6))
  val warm_water = List(new SpawnListEntry(EntityType.DOLPHIN, 2, 1, 2), new SpawnListEntry(EntityType.PUFFERFISH, 15, 1, 3), new SpawnListEntry(EntityType.TROPICAL_FISH, 25, 8, 8))

  addSpawn(EntityClassification.CREATURE, new SpawnListEntry(EntityType.SHEEP, 12, 4, 4));
  addSpawn(EntityClassification.CREATURE, new SpawnListEntry(EntityType.PIG, 10, 4, 4));
  addSpawn(EntityClassification.CREATURE, new SpawnListEntry(EntityType.CHICKEN, 10, 4, 4));
  addSpawn(EntityClassification.CREATURE, new SpawnListEntry(EntityType.COW, 8, 4, 4));
  addSpawn(EntityClassification.CREATURE, new SpawnListEntry(EntityType.RABBIT, 4, 2, 3))

  addSpawn(EntityClassification.WATER_CREATURE, new SpawnListEntry(EntityType.SQUID, 4, 1, 4));

  addSpawn(EntityClassification.AMBIENT, new SpawnListEntry(EntityType.BAT, 10, 8, 8))

  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.SPIDER, 100, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.SKELETON, 100, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.CREEPER, 100, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.SLIME, 100, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.ENDERMAN, 10, 1, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.WITCH, 5, 1, 1))
  addSpawn(EntityClassification.MONSTER, zombie_entry)
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.ZOMBIE_VILLAGER, 5, 1, 1))

  @SubscribeEvent
  def handle_spawn(evt: WorldEvent.PotentialSpawns) {
    return
    evt.getType match {
      case EntityClassification.MONSTER => {
        val r = terr.rain(evt.getPos.getX, evt.getPos.getY, evt.getPos.getZ)
        // Spawn husks instead of most zombies in deserts
        if (r < 0.2) {
          val list = evt.getList
          list.remove(zombie_entry)
          list.addAll(arid_entries.asJava)
        }
      }
      case EntityClassification.CREATURE => {
        val list = evt.getList

        // Spawn llamas at high altitudes
        if (evt.getPos.getY > 120)
          list.add(llama_entry)

        // Spawn turtles on (TODO - only natural) beaches
        if (evt.getPos.getY > 60 && evt.getPos.getY < 70)
          list.add(turtle_entry)

      }
      case EntityClassification.WATER_CREATURE => {
        val list = evt.getList

        // We're in the ocean or something
        if (evt.getPos.getY < 62) {
          val t = Temp(terr.temp(evt.getPos.getX, evt.getPos.getY, evt.getPos.getZ))

          if (t >= T.SUBTROPIC)
            list.addAll(warm_water.asJava)
          else if (t >= Temp(4))
            list.addAll(med_water.asJava)
          else if (t >= T.POLAR)
            list.addAll(cold_water.asJava)
          else
            // Too cold for anything to live in the water
            list.clear()
        }
      }
      case _ => {}
    }
  }

  // This should make it snow and water freeze when it's cold enough
  override def getTemperature(pos: BlockPos): Float = terr.temp(pos.getX, pos.getY, pos.getZ).toFloat / 25 - 0.05f

  @OnlyIn(Dist.CLIENT)
  override def getGrassColor(pos: BlockPos): Int = GrassColors.get(MathHelper.clamp(getTemperature(pos), 0, 1), MathHelper.clamp(terr.rain(pos.getX, pos.getY, pos.getZ), 0, 1))
  @OnlyIn(Dist.CLIENT)
  override def getFoliageColor(pos: BlockPos): Int = FoliageColors.get(MathHelper.clamp(getTemperature(pos), 0, 1), MathHelper.clamp(terr.rain(pos.getX, pos.getY, pos.getZ), 0, 1))

  // Called eight times per chunk in the same location, the block with the lowest X and Z in the chunk and y=0
  override def decorate(stage: GenerationStage.Decoration, chunkGenerator: ChunkGenerator[_ <: net.minecraft.world.gen.GenerationSettings], worldIn: IWorld, seed: Long, random: SharedSeedRandom, start: BlockPos): Unit = {
    random.setDecorationSeed(seed, start.getX, start.getZ)
    stage match {
      case GenerationStage.Decoration.VEGETAL_DECORATION => {
        for (x <- start.getX() until start.getX() + 16;
             z <- start.getZ() until start.getZ() + 16) {
          var spawn = random.nextDouble()
          val h = worldIn.getHeight(Heightmap.Type.OCEAN_FLOOR, x, z)

          val r = Rain(terr.rain(x, h, z))
          val t = Temp(terr.temp(x, h, z))
          val pos = new BlockPos(x, h, z)

          val plants = if (worldIn.getBlockState(pos).getBlock() == Blocks.AIR)
              Plants.LAND
            else if (worldIn.getBlockState(pos).getBlock() == Blocks.WATER)
              Plants.WATER
            else Array[Plant]()

          val under_pos = new BlockPos(x, h-1, z)
          val under_block = worldIn.getBlockState(under_pos)

          val noise = terr.gen.getValue(x * 0.0005 + 9.267, z * 0.0005 - 12.983) * 0.5 + 0.5

          breakable {
            for (plant <- plants) {
              if (plant.check(r, t, h, under_block)) {
                if (plant.noise_interval.check(noise)) {
                  if (spawn <= plant.cover) {
                    if (spawn <= plant.density)
                      plant.place(pos, worldIn, terr)
                    break
                  } else
                    spawn = (spawn - plant.cover) / (1 - plant.cover)
                }
              }
            }
          }
        }
      }
      case GenerationStage.Decoration.TOP_LAYER_MODIFICATION => {
        for (x <- start.getX() until start.getX() + 16;
             z <- start.getZ() until start.getZ() + 16) {
          val h = worldIn.getHeight(Heightmap.Type.OCEAN_FLOOR, x, z)
          val t = terr.temp(x, h, z) + random.nextDouble * 2 - 1
          val pos = new BlockPos(x, h, z)
          if (t < 0 && worldIn.getBlockState(pos).getBlock() == Blocks.AIR) {
            worldIn.setBlockState(pos, Blocks.SNOW_BLOCK.getDefaultState(), 2)
            worldIn.setBlockState(new BlockPos(x, h+1, z), Blocks.SNOW.getDefaultState(), 2)
          } else if (t < 5 && worldIn.getBlockState(pos).getBlock() == Blocks.AIR)
            worldIn.setBlockState(pos, Blocks.SNOW.getDefaultState(), 2)

          // Freeze the top layer of water
          val ocean = new BlockPos(x, 63, z)
          if (t < 5 && worldIn.getBlockState(ocean).getBlock == Blocks.WATER)
            worldIn.setBlockState(ocean, Blocks.ICE.getDefaultState, 2)
        }
      }
      case _ => {}
    }
  }

  val replace = Set(Blocks.AIR, Blocks.WATER)

  // Called once for each X and Z location
  override def buildSurface(random: ju.Random, chunkIn: IChunk, x: Int, z: Int, h: Int, noise: Double, defaultBlock: BlockState, defaultFluid: BlockState, seaLevel: Int, random0: Long): Unit = {
    return
    if (!replace.contains(chunkIn.getBlockState(new BlockPos(x, h, z)).getBlock()))
      return

    val r = terr.rain(x, h, z)
    val t = terr.temp(x, h, z)

    var blend = random.nextDouble() * 2 - 1
    val top = if (r + blend * 0.05 < 0.2 || h + blend * 2 < 68)
      if (terr.red_sand(x, z))
        Blocks.RED_SAND.getDefaultState
      else
        Blocks.SAND.getDefaultState
    else if (t + blend * 4 >= 25 && r + blend * 0.05 >= 0.75) Blocks.PODZOL.getDefaultState else Blocks.GRASS_BLOCK.getDefaultState

    val depth = 3 + (noise * 3).asInstanceOf[Int]
    chunkIn.setBlockState(new BlockPos(x, h, z), top, false)

    for (i <- 1 to depth) {
      if (h-i > 0) {
        blend = random.nextDouble() * 2 - 1
        val mid = if (r + blend * 0.05 < 0.2 || h + blend * 2 < 68)
          if (terr.red_sand(x, z))
            Blocks.RED_SAND.getDefaultState
          else
            Blocks.SAND.getDefaultState
        else Blocks.DIRT.getDefaultState
        chunkIn.setBlockState(new BlockPos(x, h-i, z), mid, false)
      }
    }
  }
}
