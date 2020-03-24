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
import net.minecraft.world.WorldType
import net.minecraft.world.biome.provider.OverworldBiomeProvider
import net.minecraft.world.biome.provider.OverworldBiomeProviderSettings
import net.minecraft.world.biome.Biome

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

@Mod("terragen")
class Terragen extends WorldType("Terragen") {
  val LOGGER = LogManager.getLogger()

  val terr = new Terrain

  FMLJavaModLoadingContext.get().getModEventBus().addListener(new java.util.function.Consumer[FMLCommonSetupEvent] {
    override def accept(evt: FMLCommonSetupEvent) = commonSetup(evt)
  })
  FMLJavaModLoadingContext.get().getModEventBus().addListener(new java.util.function.Consumer[RegistryEvent.Register[Biome]] {
    override def accept(evt: RegistryEvent.Register[Biome]) = {
      // For some reason, a) registry events fire on the mod bus, and b) we get all registry events in here, not just biome ones
      if (evt.getName().toString().equals("minecraft:biome")) {
        ExtraBiomes.init(terr)
        evt.getRegistry().registerAll(ExtraBiomes.COLD_DESERT.setRegistryName("cold_desert"), ExtraBiomes.NULL_BIOME.setRegistryName("null_biome"))
        LOGGER.debug("Registered biome")
      }
    }
  })

  override def createChunkGenerator(world: World): ChunkGenerator[_] = new ChunkGen(world, terr, new GenerationSettings())

  def commonSetup(evt: FMLCommonSetupEvent) {
    LOGGER.info("Working!")
  }
}
