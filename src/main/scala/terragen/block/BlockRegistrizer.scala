package terragen.block

import net.minecraft.block._
import net.minecraft.item._
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import collection.mutable.ArrayStack
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.api.distmarker.Dist
import net.minecraft.world.biome.BiomeColors
import net.minecraft.world.FoliageColors
import terragen._

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger


object RegisterEverything {
  val LOGGER = LogManager.getLogger()

  var BLOCKS = ArrayStack[Block]()
  var FOLIAGE = ArrayStack[Block]()
  var ITEMS = ArrayStack[Item]()

  FMLJavaModLoadingContext.get().getModEventBus().addListener((evt: RegistryEvent.Register[Block]) => {
    // This actually gets all register events
    // So we check for the name
    if (evt.getName().toString().equals("minecraft:block")) {
      evt.asInstanceOf[RegistryEvent.Register[Block]].getRegistry.registerAll(
        BLOCKS : _*
      )
      LOGGER.info("Registered BlockRegistrizer blocks")
    }
    if (evt.getName().toString().equals("minecraft:item")) {
      evt.asInstanceOf[RegistryEvent.Register[Item]].getRegistry.registerAll(
        ITEMS : _*
      )
      LOGGER.info("Registered BlockRegistrizer items")
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

  net.minecraftforge.fml.DistExecutor.runWhenOn(Dist.CLIENT, () => () =>
      FMLJavaModLoadingContext.get.getModEventBus.addListener((evt: net.minecraftforge.client.event.ColorHandlerEvent.Block) => {
            evt.getBlockColors.register(
              (state, reader, pos, i) => if (reader != null && pos != null) BiomeColors.getFoliageColor(reader, pos) else FoliageColors.getDefault(),
              FOLIAGE : _*)
            LOGGER.info("Registered BlockRegistrizer foliage colors")
          }
      )
  )
}

import RegisterEverything._

trait BlockRegistrizer extends Block {
  var has_item = true
  var is_foliage = false
  var group = ItemGroup.BUILDING_BLOCKS

  // You should call this statically or at least before registry events fire
  def register(name: String): BlockRegistrizer = {
    this.setRegistryName(name)
    BLOCKS.push(this)
    if (has_item)
      ITEMS.push(new BlockItem(this, new Item.Properties().group(group)).setRegistryName(name))
    if (is_foliage)
      FOLIAGE.push(this)
    this
  }

  def no_item: BlockRegistrizer = {
    has_item = false
    this
  }

  def group(gr: ItemGroup): BlockRegistrizer = {
    group = gr
    this
  }

  // Sets this block up to change color with biome and conditions
  def foliage(): BlockRegistrizer = {
    is_foliage = true
    this
  }
}
