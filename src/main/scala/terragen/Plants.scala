package terragen

import net.minecraft.block.{Block, Blocks, BlockState}
import net.minecraft.state.properties.BlockStateProperties
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IWorld
import net.minecraft.util.SharedSeedRandom
import scala.collection.mutable.ArrayStack
import net.minecraft.block.DoublePlantBlock

case class NI(low: Double, high: Double) {
  def check(x: Double) = x >= low && x <= high
}
object NI {
  val ALL = NI(0, 1)
}

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
abstract class Plant(val cover: Double, val density: Double, val noise_interval: NI) {
  def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean
  def place(pos: BlockPos, world: IWorld, rand: Terrain): Unit
}

case class HRange(tlow: Temp, thigh: Temp, rlow: Rain, rhigh: Rain, hlow: Double, hhigh: Double) {
  def check(rain: Rain, temp: Temp, height: Double): Boolean = rain >= rlow && rain <= rhigh && temp >= tlow && temp <= thigh && height >= hlow && height <= hhigh
}
object CRange {
  def apply(tlow: Temp, thigh: Temp, rlow: Rain, rhigh: Rain): HRange = HRange(tlow, thigh, rlow, rhigh, 0, 256)
}
// Humidity doesn't make sense in the sea
object SeaRange {
  def apply(tlow: Temp, thigh: Temp, hhigh: Double): HRange = HRange(tlow, thigh, Rain(0), Rain(1), 0, hhigh)
}

case class SimplePlant(override val cover: Double, override val density: Double, block: BlockState, crange: HRange, ground: Set[BlockState], ni: NI) extends Plant(cover, density, ni) {
  override def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean = crange.check(rain, temp, height) && ground.contains(below)

  override def place(pos: BlockPos, world: IWorld, rand: Terrain): Unit = {
    world.setBlockState(pos, block, 2)
  }
}

case class FlowerPlant(override val cover: Double, override val density: Double, blocks: Array[Block], crange: HRange, ground: Set[BlockState], ni: NI) extends Plant(cover, density, ni) {
  override def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean = crange.check(rain, temp, height) && ground.contains(below)

  override def place(pos: BlockPos, world: IWorld, terr: Terrain): Unit = {
    val i = (terr.gen.getValue(pos.getX * 0.05, pos.getY * 0.05) * 0.5 + 0.5) * blocks.length
    world.setBlockState(pos, blocks(i.toInt.min(blocks.length-1)).getDefaultState, 2)
  }
}

case class DoublePlant(override val cover: Double, override val density: Double, block: DoublePlantBlock, crange: HRange, ground: Set[BlockState], ni: NI) extends Plant(cover, density, ni) {
  override def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean = crange.check(rain, temp, height) && ground.contains(below)

  override def place(pos: BlockPos, world: IWorld, rand: Terrain): Unit = {
    block.placeAt(world, pos, 2)
  }
}

class Tree(override val cover: Double, override val density: Double, crange: HRange, ground: Set[BlockState], lheight: Int, hheight: Int, trunk: BlockState, lrad: Int, hrad: Int, leaf: BlockState, ni: NI) extends Plant(cover, density, ni) {
  override def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean = crange.check(rain, temp, height) && ground.contains(below)

  def make_trunk(pos: BlockPos, world: IWorld, rand: SharedSeedRandom, height_mod: Int): ArrayStack[BlockPos] = {
    val height = lheight + rand.nextInt(hheight - lheight) + height_mod

    for (y <- pos.getY to pos.getY + height) {
      world.setBlockState(new BlockPos(pos.getX, y, pos.getZ), trunk, 2)
    }

    ArrayStack(new BlockPos(pos.getX, pos.getY + height, pos.getZ))
  }

  def make_leaves(ground_pos: BlockPos, pos: BlockPos, world: IWorld, rand: SharedSeedRandom): Unit = {
    val rad    = if (hrad > lrad) lrad + rand.nextInt(hrad    - lrad   ) else lrad
    var rad2: Double = rad*rad
    val drad = hrad - lrad

    // Leaves
    for (x <- -rad to rad;
         y <- -rad to rad;
         z <- -rad to rad) {
       val p = new BlockPos(pos.getX + x, pos.getY + y, pos.getZ + z)
       rad2 += rand.nextDouble() - 0.5
       if ((world.getBlockState(p).getBlock == Blocks.AIR || world.getBlockState(p).getBlock == Blocks.WATER) && x*x + y*y + z*z <= rad2)
        world.setBlockState(p, leaf, 2)
     }
  }

  override def place(pos: BlockPos, world: IWorld, terr: Terrain): Unit = {
    val top_poss = make_trunk(pos, world, terr.rand, 0)

    for (p <- top_poss)
      make_leaves(pos, p, world, terr.rand)

    // TODO roots
  }
}

class SpruceTree(override val cover: Double, override val density: Double, crange: HRange, ground: Set[BlockState], lheight: Int, hheight: Int, trunk: BlockState, lrad: Int, hrad: Int, leaf: BlockState, ni: NI)
  extends Tree(cover, density, crange, ground, lheight, hheight, trunk, lrad, hrad, leaf, ni) {

  override def make_leaves(ground_pos: BlockPos, top_pos: BlockPos, world: IWorld, rand: SharedSeedRandom): Unit = {
    val rad    = lrad    + rand.nextInt(hrad    - lrad   )

    val start_y = ground_pos.getY + 3
    val end_y = top_pos.getY + 2
    val slope = (rad-1).asInstanceOf[Double]/(end_y-start_y-1)

    var cur_rad: Double = rad

    for (y <- start_y to end_y) {
      var rad2: Double = cur_rad*cur_rad
      var i_rad = cur_rad.asInstanceOf[Int]

      for (x <- -i_rad to i_rad;
           z <- -i_rad to i_rad) {
        val p = new BlockPos(top_pos.getX + x, y, top_pos.getZ + z)
        rad2 += rand.nextDouble() - 0.5
        if (world.getBlockState(p).getBlock == Blocks.AIR && x*x + z*z <= rad2)
        world.setBlockState(p, leaf, 2)
      }

      cur_rad -= slope
    }
  }
}

class BareTree(override val cover: Double, override val density: Double, crange: HRange, ground: Set[BlockState], lheight: Int, hheight: Int, trunk: BlockState, ni: NI)
  extends Tree(cover, density, crange, ground, lheight, hheight, trunk, 0, 0, Blocks.AIR.getDefaultState, ni) {

  override def make_leaves(ground_pos: BlockPos, top_pos: BlockPos, world: IWorld, rand: SharedSeedRandom): Unit = {}
}

class SplitTree(override val cover: Double, override val density: Double, crange: HRange, ground: Set[BlockState], lheight: Int, hheight: Int, trunk: BlockState, lrad: Int, hrad: Int, leaf: BlockState, bend_fac: Double, split_fac: Double, ni: NI)
  extends Tree(cover, density, crange, ground, lheight, hheight, trunk, lrad, hrad, leaf, ni) {

  override def make_trunk(pos: BlockPos, world: IWorld, rand: SharedSeedRandom, height_mod: Int): ArrayStack[BlockPos] = {
    val height = lheight + rand.nextInt(hheight - lheight) + height_mod
    var x = pos.getX
    var z = pos.getZ
    var stack = ArrayStack[BlockPos]()

    var i = 0

    for (y <- pos.getY to pos.getY + height) {
      i += 1

      val split = rand.nextDouble < split_fac

      if (split)
        stack ++= make_trunk(new BlockPos(x, y, z), world, rand, height_mod-i)

      if (split || rand.nextDouble < bend_fac)
        if (rand.nextBoolean)
          if (rand.nextBoolean)
            x += 1
          else
            x -= 1
        else
          if (rand.nextBoolean)
            z += 1
          else
            z -= 1

      world.setBlockState(new BlockPos(x, y, z), trunk, 2)
    }

    stack.push(new BlockPos(x, pos.getY + height, z))
    stack
  }
}

class Coral(override val cover: Double, override val density: Double, crange: HRange, ground: Set[BlockState], lheight: Int, hheight: Int, trunk: BlockState, top: BlockState, fan: Block, bend_fac: Double, split_fac: Double, ni: NI)
  extends Plant(cover, density, ni) {
  override def check(rain: Rain, temp: Temp, height: Double, below: BlockState): Boolean = crange.check(rain, temp, height) && ground.contains(below)

  override def place(pos: BlockPos, world: IWorld, terr: Terrain): Unit = place_(pos, world, terr.rand, 0)

  def place_(pos: BlockPos, world: IWorld, rand: SharedSeedRandom, height_mod: Int): Unit = {
    val height = lheight + rand.nextInt(hheight - lheight) + height_mod
    var x = pos.getX
    var z = pos.getZ

    var i = 0

    for (y <- pos.getY to pos.getY + height) {
      i += 1

      val split = rand.nextDouble < split_fac

      if (split)
        place_(new BlockPos(x, y, z), world, rand, height_mod-i)

      if (split || rand.nextDouble < bend_fac)
        if (rand.nextBoolean)
          if (rand.nextBoolean)
            x += 1
          else
            x -= 1
        else
          if (rand.nextBoolean)
            z += 1
          else
            z -= 1

      val b = world.getBlockState(new BlockPos(x, y, z)).getBlock
      if (b == Blocks.WATER || b.isInstanceOf[net.minecraft.block.AbstractCoralPlantBlock])
        world.setBlockState(new BlockPos(x, y, z), trunk, 2)
      if (world.getBlockState(new BlockPos(x, y+1, z)).getBlock == Blocks.WATER)
        if (rand.nextDouble > 0.8)
          world.setBlockState(new BlockPos(x, y+1, z), Blocks.SEA_PICKLE.getDefaultState.`with`[Integer, Integer](net.minecraft.block.SeaPickleBlock.PICKLES, rand.nextInt(4) + 1), 2)
        else
          world.setBlockState(new BlockPos(x, y+1, z), top, 2)

      for (i <- -1 to 1; j <- -1 to 1)
        if ((i + j).abs == 1 && world.getBlockState(new BlockPos(x + i, y, z + j)).getBlock == Blocks.WATER)
          world.setBlockState(new BlockPos(x + i, y, z + j), fan.getDefaultState
            .`with`(net.minecraft.block.DeadCoralWallFanBlock.FACING, net.minecraft.util.Direction.getFacingFromVector(i, 0, j))
            , 2)
    }
  }
}

object T {
  val ANY = Temp(30)
  val TROPIC = Temp(25)
  val SUBTROPIC = Temp(16)
  val TEMPERATE = Temp(8)
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
  val DIRT_GRASS = Set(Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState(), Blocks.PODZOL.getDefaultState())
  val DIRT_SAND_GRAVEL = Set(Blocks.GRASS_BLOCK.getDefaultState(), Blocks.DIRT.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState(), Blocks.PODZOL.getDefaultState(), Blocks.SAND.getDefaultState(), Blocks.RED_SAND.getDefaultState(), Blocks.GRAVEL.getDefaultState())
  val SAND = Set(Blocks.RED_SAND.getDefaultState, Blocks.SAND.getDefaultState)

  val FLOWERS = Array(Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM, Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP, Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY)
}

object Plants {
  val ALL = Array[Plant](
    // All the trees have persistent leaves because I'm making the canopies larger than Minecraft's
    // Brazil nut tree
    new Tree(0.2, 0.1, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, 30, 40, Blocks.JUNGLE_LOG.getDefaultState(), 5, 7, Blocks.JUNGLE_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT), NI.ALL),
    // Young Brazil nut
    new Tree(0.1, 0.05, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, 2, 5, Blocks.JUNGLE_LOG.getDefaultState, 1, 2, Blocks.JUNGLE_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT), NI.ALL),
    // Bamboo
    new BareTree(0.1, 0.05, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, 2, 18, Blocks.BAMBOO.getDefaultState, NI(0.4, 1)),

    // Mushrooms in the rainforest
    FlowerPlant(0.1, 0.1, Array(Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM), CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, NI.ALL),
    // This is supposed to be holly or something; an understory specialist
    SimplePlant(0.2, 0.2, Blocks.SWEET_BERRY_BUSH.getDefaultState, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, NI.ALL),
    // If we're in a rainforest and nothing's grown yet, we'll probably want ferns there
    DoublePlant(0.5, 0.5, Blocks.LARGE_FERN.asInstanceOf[DoublePlantBlock], CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, NI.ALL),
    SimplePlant(0.9, 0.9, Blocks.FERN.getDefaultState, CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY), S.DIRT_GRASS, NI.ALL),

    // Savannah
    // I'm making this an Australian mulga
    new SplitTree(0.2, 0.002, CRange(T.TEMPERATE, T.ANY, Rain(0.1), R.NORMAL), S.DIRT_GRASS, 3, 7, Blocks.ACACIA_LOG.getDefaultState, 2, 5, Blocks.ACACIA_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT), 0.3, 0.1, NI(0.2, 1)),
    DoublePlant(0.4, 0.4, Blocks.TALL_GRASS.asInstanceOf[DoublePlantBlock], CRange(T.TEMPERATE, T.ANY, R.DRY, R.DAMP), S.DIRT_GRASS, NI.ALL),

    // Birch tree
    new Tree(0.2, 0.01, CRange(Temp(2), Temp(12), R.NORMAL, R.WET), S.DIRT_GRASS, 7, 16, Blocks.BIRCH_LOG.getDefaultState, 2, 5, Blocks.BIRCH_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT), NI(0, 0.4)),
    // Oak tree
    new SplitTree(0.2, 0.01, CRange(T.TEMPERATE, T.TROPIC, R.NORMAL, R.WET), S.DIRT_GRASS, 8, 12, Blocks.OAK_LOG.getDefaultState, 3, 6, Blocks.OAK_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT), 0.2, 0.2, NI(0.1, 0.9)),
    // Dark oak; I'm saying these are the bigger oaks
    new SplitTree(0.2, 0.01, CRange(T.TEMPERATE, T.TROPIC, R.NORMAL, R.WET), S.DIRT_GRASS, 11, 15, Blocks.DARK_OAK_LOG.getDefaultState, 4, 7, Blocks.DARK_OAK_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT), 0.2, 0.2, NI(0.1, 0.9)),
    // Spruce
    new SpruceTree(0.4, 0.05, CRange(T.SUBPOLAR, Temp(10), R.NORMAL, R.DAMP), S.DIRT_GRASS, 8, 40, Blocks.SPRUCE_LOG.getDefaultState, 2, 4, Blocks.SPRUCE_LEAVES.getDefaultState.cycle(BlockStateProperties.PERSISTENT), NI(0.3, 1)),

    FlowerPlant(0.05, 0.05, S.FLOWERS, CRange(T.TEMPERATE, T.ANY, R.NORMAL, R.ANY), S.DIRT_GRASS, NI.ALL),
    SimplePlant(0.3, 0.3, Blocks.GRASS.getDefaultState(), CRange(T.TEMPERATE, T.ANY, R.NORMAL, R.WET), S.DIRT_GRASS, NI.ALL),
    SimplePlant(0.2, 0.2, Blocks.DEAD_BUSH.getDefaultState(), CRange(T.TEMPERATE, T.ANY, R.DESERT, R.NORMAL), S.DIRT_SAND_GRAVEL, NI.ALL)
  )

  val WATER = Array[Plant](
    // Coral reefs
    new Coral(0.3, 0.3, SeaRange(T.SUBTROPIC, T.ANY, 60), S.SAND, 1, 5, Blocks.FIRE_CORAL_BLOCK.getDefaultState, Blocks.FIRE_CORAL.getDefaultState, Blocks.FIRE_CORAL_WALL_FAN, 0.7, 0.6, NI(0.4, 0.9)),
    new Coral(0.3, 0.3, SeaRange(T.SUBTROPIC, T.ANY, 60), S.SAND, 1, 5, Blocks.TUBE_CORAL_BLOCK.getDefaultState, Blocks.TUBE_CORAL.getDefaultState, Blocks.TUBE_CORAL_WALL_FAN, 0.7, 0.6, NI(0.5, 1)),
    new Coral(0.3, 0.3, SeaRange(T.SUBTROPIC, T.ANY, 60), S.SAND, 1, 5, Blocks.HORN_CORAL_BLOCK.getDefaultState, Blocks.HORN_CORAL.getDefaultState, Blocks.HORN_CORAL_WALL_FAN, 0.7, 0.6, NI(0.4, 0.9)),
    new Coral(0.3, 0.3, SeaRange(T.SUBTROPIC, T.ANY, 60), S.SAND, 1, 5, Blocks.BRAIN_CORAL_BLOCK.getDefaultState, Blocks.BRAIN_CORAL.getDefaultState, Blocks.BRAIN_CORAL_WALL_FAN, 0.7, 0.6, NI(0.5, 1)),

    // Sea grass
    SimplePlant(0.3, 0.3, Blocks.SEAGRASS.getDefaultState, SeaRange(T.SUBPOLAR, T.ANY, 65), S.DIRT_SAND_GRAVEL, NI.ALL)
  )
}
