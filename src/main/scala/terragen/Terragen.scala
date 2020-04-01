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

import scala.collection.JavaConverters._

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

object TBlocks {
  // Basalt isn't quite as hard as most stone
  val BASALT = new Block(Block.Properties.create(Material.ROCK, MaterialColor.OBSIDIAN).hardnessAndResistance(1.2F, 5.0F)).setRegistryName("basalt")
  // Neither is limestone
  val LIMESTONE = new Block(Block.Properties.create(Material.ROCK, MaterialColor.SAND).hardnessAndResistance(1.2F, 5.0F)).setRegistryName("limestone")
  // But marble's harder
  val MARBLE = new Block(Block.Properties.create(Material.ROCK, MaterialColor.QUARTZ).hardnessAndResistance(1.7F, 7.0F)).setRegistryName("marble")

  val BRAZIL_LEAF = new LeafBlock().setRegistryName("brazil_nut_leaf")
  val OAK_LEAF = new LeafBlock().setRegistryName("oak_leaf")
  val BIRCH_LEAF = new LeafBlock().setRegistryName("birch_leaf")
  val MULGA_LEAF = new LeafBlock().setRegistryName("mulga_leaf")
  val SPRUCE_LEAF = new LeafBlock().setRegistryName("spruce_leaf")

  val BRAZIL_SAPLING = new Sapling(Array(Plants.BRAZIL_TREE_BIG, Plants.BRAZIL_TREE_YOUNG)).setRegistryName("brazil_nut_sapling")
  val OAK_SAPLING = new Sapling(Array(Plants.OAK_TREE, Plants.DARK_OAK_TREE)).setRegistryName("oak_sapling")
  val BIRCH_SAPLING = new Sapling(Array(Plants.BIRCH_TREE)).setRegistryName("birch_sapling")
  val MULGA_SAPLING = new Sapling(Array(Plants.MULGA_TREE)).setRegistryName("mulga_sapling")
  val SPRUCE_SAPLING = new Sapling(Array(Plants.SPRUCE_TREE)).setRegistryName("spruce_sapling")
}

@Mod("terragen")
class Terragen extends WorldType("terragen") {
  val LOGGER = LogManager.getLogger()

  val terr = new Terrain

  def item(block: Block, group: ItemGroup): Item = new BlockItem(block, new Item.Properties().group(group)).setRegistryName(block.getRegistryName)

  FMLJavaModLoadingContext.get().getModEventBus().addListener(commonSetup)
  FMLJavaModLoadingContext.get().getModEventBus().addListener((evt: RegistryEvent.Register[Biome]) => {
      // For some reason, a) registry events fire on the mod bus, and b) we get all registry events in here, not just biome ones
      if (evt.getName().toString().equals("minecraft:biome")) {
        ExtraBiomes.init(terr)
        MinecraftForge.EVENT_BUS.register(ExtraBiomes.NULL_BIOME)
        evt.getRegistry.registerAll(ExtraBiomes.COLD_DESERT.setRegistryName("cold_desert"), ExtraBiomes.NULL_BIOME.setRegistryName("null_biome"))
        LOGGER.debug("Registered biomes")
      }
      if (evt.getName().toString().equals("minecraft:block")) {
        evt.asInstanceOf[RegistryEvent.Register[Block]].getRegistry.registerAll(
          TBlocks.BASALT,
          TBlocks.LIMESTONE,
          TBlocks.MARBLE,
          TBlocks.BRAZIL_LEAF,
          TBlocks.OAK_LEAF,
          TBlocks.BIRCH_LEAF,
          TBlocks.MULGA_LEAF,
          TBlocks.SPRUCE_LEAF,
          TBlocks.BRAZIL_SAPLING,
          TBlocks.OAK_SAPLING,
          TBlocks.BIRCH_SAPLING,
          TBlocks.MULGA_SAPLING,
          TBlocks.SPRUCE_SAPLING
        )
        LOGGER.debug("Registered blocks")
      }

      if (evt.getName().toString().equals("minecraft:item")) {
        evt.asInstanceOf[RegistryEvent.Register[Item]].getRegistry.registerAll(
          item(TBlocks.BASALT, ItemGroup.BUILDING_BLOCKS),
          item(TBlocks.LIMESTONE, ItemGroup.BUILDING_BLOCKS),
          item(TBlocks.MARBLE, ItemGroup.BUILDING_BLOCKS),
          item(TBlocks.BRAZIL_LEAF, ItemGroup.DECORATIONS),
          item(TBlocks.OAK_LEAF, ItemGroup.DECORATIONS),
          item(TBlocks.BIRCH_LEAF, ItemGroup.DECORATIONS),
          item(TBlocks.MULGA_LEAF, ItemGroup.DECORATIONS),
          item(TBlocks.SPRUCE_LEAF, ItemGroup.DECORATIONS),
          item(TBlocks.BRAZIL_SAPLING, ItemGroup.DECORATIONS),
          item(TBlocks.OAK_SAPLING, ItemGroup.DECORATIONS),
          item(TBlocks.BIRCH_SAPLING, ItemGroup.DECORATIONS),
          item(TBlocks.MULGA_SAPLING, ItemGroup.DECORATIONS),
          item(TBlocks.SPRUCE_SAPLING, ItemGroup.DECORATIONS)
        )
        LOGGER.debug("Registered blocks")
      }

      if (evt.getName().toString().equals("terragen:plant")) {
        evt.asInstanceOf[RegistryEvent.Register[Plant]].getRegistry.registerAll(
          Plants.ALL : _*
        )
      }

      if (evt.getName().toString().equals("terragen:stratum")) {
        evt.asInstanceOf[RegistryEvent.Register[Stratum]].getRegistry.registerAll(
          Strata.TO_REGISTER : _*
        )
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
  net.minecraftforge.fml.DistExecutor.runWhenOn(Dist.CLIENT, () => () =>
      FMLJavaModLoadingContext.get.getModEventBus.addListener((evt: net.minecraftforge.client.event.ColorHandlerEvent.Block) => {
            evt.getBlockColors.register(
              (state, reader, pos, i) => if (reader != null && pos != null) BiomeColors.getFoliageColor(reader, pos) else FoliageColors.getDefault(),
              TBlocks.BRAZIL_LEAF, TBlocks.OAK_LEAF, TBlocks.BIRCH_LEAF, TBlocks.MULGA_LEAF, TBlocks.SPRUCE_LEAF)
            LOGGER.info("Registered leaf colors")
          }
      )
  )

  override def createChunkGenerator(world: World): ChunkGenerator[_] = {
    terr.reseed(world.getSeed)
    new ChunkGen(world, terr, new GenerationSettings())
  }

  def commonSetup(evt: FMLCommonSetupEvent) {
    LOGGER.info("Working!")
  }
}
