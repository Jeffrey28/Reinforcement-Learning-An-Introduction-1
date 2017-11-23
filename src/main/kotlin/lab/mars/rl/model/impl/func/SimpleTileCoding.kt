@file:Suppress("NAME_SHADOWING")

package lab.mars.rl.model.impl.func

import lab.mars.rl.model.State
import lab.mars.rl.util.matrix.Matrix

class SimpleTileCoding(val numOfTilings: Int,
                       tilingSize: Int,
                       val tileWidth: Int,
                       val tilingOffset: Double,
                       val scalar: (State) -> Double) : Feature {
    val tilingSize = tilingSize + 1
    override val numOfComponents = numOfTilings * tilingSize

    override fun invoke(s: State): Matrix {
        val s = scalar(s)
        return Matrix.column(numOfComponents) {
            val tilingIdx = it / tilingSize
            val tileIdx = it % tilingSize
            val start = -tileWidth + tilingIdx * tilingOffset + tileIdx * tileWidth
            if (start <= s && s < start + tileWidth) 1.0 else 0.0
        }
    }

    override fun alpha(alpha: Double, s: State) = alpha / numOfTilings
}