package terragen.block

import net.minecraft.block.Block
import net.minecraft.block.material._
import terragen._

object TBlocks {
  def force_init() = {}

  // Basalt isn't quite as hard as most stone
  val BASALT = new BasicBlock(Block.Properties.create(Material.ROCK, MaterialColor.OBSIDIAN).hardnessAndResistance(1.2F, 5.0F)).register("basalt")
  // Neither is limestone
  val LIMESTONE = new BasicBlock(Block.Properties.create(Material.ROCK, MaterialColor.SAND).hardnessAndResistance(1.2F, 5.0F)).register("limestone")
  // But marble's harder
  val MARBLE = new BasicBlock(Block.Properties.create(Material.ROCK, MaterialColor.QUARTZ).hardnessAndResistance(1.7F, 7.0F)).register("marble")

  val MOLTEN_STONE = new MoltenStone().register("molten_stone")

  val BRAZIL_LEAF = new LeafBlock().register("brazil_nut_leaf")
  val OAK_LEAF = new LeafBlock().register("oak_leaf")
  val BIRCH_LEAF = new LeafBlock().register("birch_leaf")
  val MULGA_LEAF = new LeafBlock().register("mulga_leaf")
  val SPRUCE_LEAF = new LeafBlock().register("spruce_leaf")

  val BRAZIL_SAPLING = new Sapling(Array(Plants.BRAZIL_TREE_BIG, Plants.BRAZIL_TREE_YOUNG)).register("brazil_nut_sapling")
  val OAK_SAPLING = new Sapling(Array(Plants.OAK_TREE, Plants.DARK_OAK_TREE)).register("oak_sapling")
  val BIRCH_SAPLING = new Sapling(Array(Plants.BIRCH_TREE)).register("birch_sapling")
  val MULGA_SAPLING = new Sapling(Array(Plants.MULGA_TREE)).register("mulga_sapling")
  val SPRUCE_SAPLING = new Sapling(Array(Plants.SPRUCE_TREE)).register("spruce_sapling")
}
