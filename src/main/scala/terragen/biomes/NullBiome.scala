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
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IWorld
import net.minecraft.world.chunk.IChunk
import net.minecraft.block.{BlockState, Blocks}

import java.{util => ju}
import scala.util.control.Breaks._

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class NullBiome(terr: Terrain) extends Biome(new Biome.Builder().surfaceBuilder(SurfaceBuilder.DEFAULT, SurfaceBuilder.AIR_CONFIG).precipitation(Biome.RainType.RAIN).category(Biome.Category.NONE).depth(0.125f).scale(0.05f).temperature(0.5f).downfall(0).waterColor(4159204).waterFogColor(329011).parent(null)) {
  val LOGGER = LogManager.getLogger()

  addSpawn(EntityClassification.CREATURE, new SpawnListEntry(EntityType.RABBIT, 4, 2, 3))
  addSpawn(EntityClassification.AMBIENT, new SpawnListEntry(EntityType.BAT, 10, 8, 8))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.SPIDER, 100, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.SKELETON, 100, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.CREEPER, 100, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.SLIME, 100, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.ENDERMAN, 10, 1, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.WITCH, 5, 1, 1))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.ZOMBIE, 19, 4, 4))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.ZOMBIE_VILLAGER, 1, 1, 1))
  addSpawn(EntityClassification.MONSTER, new SpawnListEntry(EntityType.HUSK, 80, 4, 4))

  // Called eight times per chunk in the same location, the block with the lowest X and Z in the chunk and y=0
  override def decorate(stage: GenerationStage.Decoration, chunkGenerator: ChunkGenerator[_ <: net.minecraft.world.gen.GenerationSettings], worldIn: IWorld, seed: Long, random: SharedSeedRandom, pos: BlockPos): Unit = {
    stage match {
      case GenerationStage.Decoration.VEGETAL_DECORATION => {
        for (x <- pos.getX() until pos.getX() + 16;
             z <- pos.getZ() until pos.getZ() + 16) {
          var spawn = random.nextDouble()

          val r = Rain(terr.rain(x, z))
          val t = Temp(terr.temp(x, z))
          val h = terr.height(x, z)
          val pos = new BlockPos(x, h.ceil.asInstanceOf[Int]+1, z)

          val under_pos = new BlockPos(x, h.ceil.asInstanceOf[Int], z)
          val under_block = worldIn.getBlockState(under_pos)

          breakable {
            for (plant <- Plants.ALL) {
              if (plant.check(r, t, h, under_block)) {
                if (spawn <= plant.cover) {
                  if (spawn <= plant.density)
                    plant.place(pos, worldIn, random)
                  break
                } else
                  spawn = (spawn - plant.cover) / (1 - plant.cover)
              }
            }
          }
        }
      }
      case GenerationStage.Decoration.TOP_LAYER_MODIFICATION => {
        for (x <- pos.getX() until pos.getX() + 16;
             z <- pos.getZ() until pos.getZ() + 16) {
          val t = terr.temp(x, z) + random.nextDouble * 8 - 4
          val h = terr.height(x, z)
          val pos = new BlockPos(x, h+1, z)
          if (worldIn.getBlockState(pos).getBlock() == Blocks.AIR && t < 5)
            worldIn.setBlockState(pos, Blocks.SNOW.getDefaultState(), 0)
        }
      }
      case _ => {}
    }
  }

  // Called once for each X and Z location
  override def buildSurface(random: ju.Random, chunkIn: IChunk, x: Int, z: Int, h: Int, noise: Double, defaultBlock: BlockState, defaultFluid: BlockState, seaLevel: Int, random0: Long): Unit = {
    val r = terr.rain(x, z)
    val t = terr.temp(x, z)

    var blend = random.nextDouble() * 2 - 1
    val top = if (r + blend * 0.05 < 0.2 || h + blend * 2 < 68) Blocks.SAND.getDefaultState else if (r + blend * 0.05 < 0.75) Blocks.GRASS_BLOCK.getDefaultState else Blocks.PODZOL.getDefaultState

    val depth = 3 + (noise * 3).asInstanceOf[Int]
    chunkIn.setBlockState(new BlockPos(x, h, z), top, false)

    for (i <- 1 to depth) {
      blend = random.nextDouble() * 2 - 1
      val mid = if (r + blend * 0.05 < 0.2 || h + blend * 2 < 68) Blocks.SAND.getDefaultState else Blocks.DIRT.getDefaultState
      chunkIn.setBlockState(new BlockPos(x, h-i, z), mid, false)
    }
  }
}
