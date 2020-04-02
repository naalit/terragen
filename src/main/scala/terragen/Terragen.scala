package terragen

import net.minecraftforge.fml.common._
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.common.ForgeHooks
import net.minecraft.world.World
import net.minecraft.world.chunk.AbstractChunkProvider
import net.minecraft.world.gen.{ChunkGenerator, GenerationSettings}
import net.minecraftforge.fml.common.registry.GameRegistry
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.registries.RegistryBuilder
import net.minecraft.world.WorldType
import net.minecraft.world.biome.provider.OverworldBiomeProvider
import net.minecraft.world.biome.provider.OverworldBiomeProviderSettings
import net.minecraft.world.biome.{Biome, BiomeColors}
import net.minecraftforge.common.MinecraftForge
import net.minecraft.block.Block
import net.minecraft.block.material.{Material, MaterialColor}
import net.minecraft.item.{Item, BlockItem, ItemGroup}
import net.minecraft.block.{Blocks, BlockState, SoundType}
import net.minecraftforge.api.distmarker.Dist
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{IEnviromentBlockReader, FoliageColors}
import net.minecraft.client.renderer.color.{BlockColors, IBlockColor}
import terragen.block._

import scala.collection.JavaConverters._

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod("terragen")
class Terragen extends WorldType("terragen") {
  TBlocks.force_init()
  Plants.force_init()
  Strata.force_init()

  val LOGGER = LogManager.getLogger()

  val terr = new Terrain

  FMLJavaModLoadingContext.get().getModEventBus().addListener(commonSetup)
  FMLJavaModLoadingContext.get().getModEventBus().addListener((evt: RegistryEvent.Register[Biome]) => {
      // For some reason, a) registry events fire on the mod bus, and b) we get all registry events in here, not just biome ones
      if (evt.getName().toString().equals("minecraft:biome")) {
        ExtraBiomes.init(terr)
        MinecraftForge.EVENT_BUS.register(ExtraBiomes.NULL_BIOME)
        evt.getRegistry.registerAll(ExtraBiomes.COLD_DESERT.setRegistryName("cold_desert"), ExtraBiomes.NULL_BIOME.setRegistryName("null_biome"))
        LOGGER.debug("Registered biomes")
      }

    })
  FMLJavaModLoadingContext.get().getModEventBus().addListener((evt: RegistryEvent.NewRegistry) => {
      // Plant registry
      new RegistryBuilder().setType(classOf[Plant]).setName(new net.minecraft.util.ResourceLocation("terragen", "plant")).add(new net.minecraftforge.registries.IForgeRegistry.BakeCallback[Plant] {
        override def onBake(registry: net.minecraftforge.registries.IForgeRegistryInternal[Plant], man: net.minecraftforge.registries.RegistryManager) = {
          Plants.WATER = registry.asScala.filter(x => x.water).toArray
          Plants.LAND = registry.asScala.filter(x => !x.water).toArray
          LOGGER.info("Baked plants")
        }
      }).create

      // Stratum registry
      new RegistryBuilder().setType(classOf[Stratum]).setName(new net.minecraft.util.ResourceLocation("terragen", "stratum")).add(new net.minecraftforge.registries.IForgeRegistry.BakeCallback[Stratum] {
        override def onBake(registry: net.minecraftforge.registries.IForgeRegistryInternal[Stratum], man: net.minecraftforge.registries.RegistryManager) = {
          Strata.METAMORPHIC = registry.asScala.filter(x => x.ty == RockType.METAMORPHIC).toArray.sorted
          Strata.SEDIMENTARY = registry.asScala.filter(x => x.ty == RockType.SEDIMENTARY).toArray.sorted
          Strata.IGNEOUS_E = registry.asScala.filter(x => x.ty == RockType.IGNEOUS_EXTRUSIVE).toArray.sorted
          Strata.IGNEOUS_I = registry.asScala.filter(x => x.ty == RockType.IGNEOUS_INTRUSIVE).toArray.sorted
          Strata.SURFACE = registry.asScala.filter(x => x.ty == RockType.SURFACE).toArray.sorted
          Strata.RANDOM = registry.asScala.filter(x => x.ty == RockType.RANDOM_ORE).toArray.sorted
          LOGGER.info("Baked strata")
        }
      }).create
    })

  override def createChunkGenerator(world: World): ChunkGenerator[_] = {
    terr.reseed(world.getSeed)
    new ChunkGen(world, terr, new GenerationSettings())
  }

  def commonSetup(evt: FMLCommonSetupEvent) {
    LOGGER.info("Working!")
  }
}
