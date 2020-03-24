package terragen

import net.minecraft.block.{Blocks, BlockState}
import net.minecraft.state.properties.BlockStateProperties
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IWorld
import net.minecraft.util.SharedSeedRandom

case class Temp(x: Double) {
  def <=(other: Temp) = this.x <= other.x
  def >=(other: Temp) = this.x >= other.x
}
case class Rain(x: Double) {
  def <=(other: Rain) = this.x <= other.x
  def >=(other: Rain) = this.x >= other.x
}

// cover = how much space is reserved for not other plants
// density = how much space is actually filled with it
// so if cover == density then it won't use up any free space
abstract class Plant(val cover: Double, val density: Double) {
  def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean
  def place(pos: BlockPos, world: IWorld, rand: SharedSeedRandom): Unit
}

case class CRange(tlow: Temp, thigh: Temp, rlow: Rain, rhigh: Rain) {
  def check(rain: Rain, temp: Temp): Boolean = rain >= rlow && rain <= rhigh && temp >= tlow && temp <= thigh
}

case class SimplePlant(override val cover: Double, override val density: Double, block: BlockState, crange: CRange, ground: Set[BlockState]) extends Plant(cover, density) {
  override def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean = crange.check(rain, temp) && ground.contains(below)

  override def place(pos: BlockPos, world: IWorld, rand: SharedSeedRandom): Unit = {
    world.setBlockState(pos, block, 0)
  }
}

case class Tree(override val cover: Double, override val density: Double, crange: CRange, ground: Set[BlockState], lheight: Int, hheight: Int, trunk: BlockState, lrad: Int, hrad: Int, leaf: BlockState) extends Plant(cover, density) {
  override def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean = crange.check(rain, temp) && ground.contains(below)

  override def place(pos: BlockPos, world: IWorld, rand: SharedSeedRandom): Unit = {
    val height = lheight + rand.nextInt(hheight - lheight)
    val rad    = lrad    + rand.nextInt(hrad    - lrad   )
    var rad2: Double = rad*rad
    val drad = hrad - lrad

    // Trunk
    for (y <- pos.getY to pos.getY + height) {
      world.setBlockState(new BlockPos(pos.getX, y, pos.getZ), trunk, 0)
    }

    // Leaves
    for (x <- -rad to rad;
         y <- -rad to rad;
         z <- -rad to rad) {
       val p = new BlockPos(pos.getX + x, pos.getY + height + y, pos.getZ + z)
       rad2 += rand.nextDouble() - 0.5
       if (world.getBlockState(p).getBlock == Blocks.AIR && x*x + y*y + z*z <= rad2)
        world.setBlockState(p, leaf, 0)
     }

    // TODO roots
  }
}

object T {
  val ANY = Temp(30)
  val TROPIC = Temp(18)
  val SUBTROPIC = Temp(15)
  val TEMPERATE = Temp(7)
  val SUBPOLAR = Temp(0)
  val POLAR = Temp(-10)
}

object R {
  val ANY = Rain(1.0)
  val WET = Rain(0.75)
  val DAMP = Rain(0.5)
  val NORMAL = Rain(0.25)
  val DRY = Rain(0.15)
  val DESERT = Rain(0.0)
}

object S {
  val DIRT_GRASS = Set(Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState())
  val DIRT_SAND_GRAVEL = Set(Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState(), Blocks.SAND.getDefaultState(), Blocks.RED_SAND.getDefaultState(), Blocks.GRAVEL.getDefaultState())
}

object Plants {
  val ALL = Array[Plant](
    // All the trees have persistent leaves because I'm making the canopies larger than Minecraft's
    // Brazil nut tree
    Tree(0.2, 0.1, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, 30, 40, Blocks.JUNGLE_LOG.getDefaultState(), 5, 7, Blocks.JUNGLE_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT)),
    // Young Brazil nut
    Tree(0.1, 0.05, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, 2, 5, Blocks.JUNGLE_LOG.getDefaultState, 1, 2, Blocks.JUNGLE_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT)),

    // Mushrooms in the rainforest
    SimplePlant(0.1, 0.1, Blocks.BROWN_MUSHROOM.getDefaultState, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS),
    // This is supposed to be holly or something; an understory specialist
    SimplePlant(0.2, 0.2, Blocks.SWEET_BERRY_BUSH.getDefaultState, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS),
    // If we're in a rainforest and nothing's grown yet, we'll probably want ferns there
    SimplePlant(0.9, 0.9, Blocks.FERN.getDefaultState, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS),

    // Birch tree
    Tree(0.4, 0.05, CRange(Temp(2), Temp(12), R.NORMAL, R.WET), S.DIRT_GRASS, 7, 16, Blocks.BIRCH_LOG.getDefaultState, 2, 5, Blocks.BIRCH_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT)),
    // Oak tree
    Tree(0.4, 0.05, CRange(T.TEMPERATE, T.TROPIC, R.NORMAL, R.WET), S.DIRT_GRASS, 10, 15, Blocks.OAK_LOG.getDefaultState, 4, 7, Blocks.OAK_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT)),
    // Spruce
    Tree(0.4, 0.15, CRange(T.SUBPOLAR, Temp(10), R.NORMAL, R.DAMP), S.DIRT_GRASS, 8, 40, Blocks.SPRUCE_LOG.getDefaultState, 2, 4, Blocks.SPRUCE_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT)),

    SimplePlant(0.3, 0.3, Blocks.GRASS.getDefaultState(), CRange(T.TEMPERATE, T.ANY, R.NORMAL, R.WET), S.DIRT_GRASS),
    SimplePlant(0.2, 0.2, Blocks.DEAD_BUSH.getDefaultState(), CRange(T.TEMPERATE, T.ANY, R.DESERT, R.NORMAL), S.DIRT_SAND_GRAVEL)
  )
}
