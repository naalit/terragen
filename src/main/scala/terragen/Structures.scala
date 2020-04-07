package terragen

import net.minecraft.world.gen.feature.{Feature, IFeatureConfig}
import net.minecraft.world.gen.feature.structure.Structure

object Structures {
  val ALL: Map[Structure[_ <: IFeatureConfig], HRange] = Map(
    Feature.WOODLAND_MANSION -> CRange(T.TEMPERATE, T.TROPIC, R.NORMAL, R.WET),
    Feature.DESERT_PYRAMID -> CRange(T.TEMPERATE, T.ANY, R.DESERT, R.DRY),
    Feature.BURIED_TREASURE -> SeaRange(T.SUBPOLAR, T.ANY, 70),
    Feature.IGLOO -> CRange(T.POLAR, Temp(5), R.NORMAL, R.WET),
    Feature.STRONGHOLD -> CRange(T.POLAR, T.ANY, R.DESERT, R.ANY),
    Feature.OCEAN_MONUMENT -> SeaRange(T.SUBPOLAR, T.ANY, 55),
    Feature.PILLAGER_OUTPOST -> CRange(T.TEMPERATE, T.TROPIC, R.NORMAL, R.WET),
    Feature.SHIPWRECK -> SeaRange(T.POLAR, T.ANY, 60),
    Feature.SWAMP_HUT -> HRange(T.TEMPERATE, T.SUBTROPIC, R.WET, R.ANY, 58, 64),
    Feature.VILLAGE -> CRange(T.SUBPOLAR, T.ANY, R.DESERT, R.WET),
    Feature.OCEAN_RUIN -> SeaRange(T.POLAR, T.ANY, 60),
    Feature.MINESHAFT -> CRange(T.POLAR, T.ANY, R.DESERT, R.ANY),
    Feature.JUNGLE_TEMPLE -> CRange(T.TEMPERATE, T.ANY, Rain(0.1), R.NORMAL) //CRange(T.SUBTROPIC, T.ANY, R.WET, R.ANY)
  )
}
