package terragen.biomes

import net.minecraft.entity.EntityType
import net.minecraft.entity.EntityClassification
import net.minecraft.world.biome.Biome
import net.minecraft.world.biome.Biome.SpawnListEntry
import net.minecraft.world.biome.DefaultBiomeFeatures
import net.minecraft.world.gen.surfacebuilders.SurfaceBuilder
import net.minecraft.world.gen.feature.structure.MineshaftConfig
import net.minecraft.world.gen.feature.structure.MineshaftStructure
import net.minecraft.world.gen.feature.Feature
import net.minecraft.world.gen.feature.IFeatureConfig

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class ColdDesert extends Biome(new Biome.Builder().surfaceBuilder(SurfaceBuilder.DEFAULT, SurfaceBuilder.SAND_SAND_GRAVEL_CONFIG).precipitation(Biome.RainType.NONE).category(Biome.Category.DESERT).depth(0.125f).scale(0.05f).temperature(-0.5f).downfall(0).waterColor(4159204).waterFogColor(329011).parent(null)) {
  val LOGGER = LogManager.getLogger()
  LOGGER.info("TEMP: " + getDefaultTemperature())

  addStructure(Feature.MINESHAFT, new MineshaftConfig(0.004, MineshaftStructure.Type.NORMAL))
  addStructure(Feature.STRONGHOLD, IFeatureConfig.NO_FEATURE_CONFIG)
  DefaultBiomeFeatures.addCarvers(this)
  DefaultBiomeFeatures.addStructures(this)
  DefaultBiomeFeatures.addDesertLakes(this)
  DefaultBiomeFeatures.addMonsterRooms(this)
  DefaultBiomeFeatures.addStoneVariants(this)
  DefaultBiomeFeatures.addOres(this)
  DefaultBiomeFeatures.addSedimentDisks(this)
  DefaultBiomeFeatures.addDefaultFlowers(this)
  DefaultBiomeFeatures.func_222348_W(this)
  DefaultBiomeFeatures.addDeadBushes(this)
  DefaultBiomeFeatures.addMushrooms(this)
  DefaultBiomeFeatures.addExtraReedsPumpkinsCactus(this)
  DefaultBiomeFeatures.addSprings(this)
  DefaultBiomeFeatures.addDesertFeatures(this)
  DefaultBiomeFeatures.addFreezeTopLayer(this)
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
}
