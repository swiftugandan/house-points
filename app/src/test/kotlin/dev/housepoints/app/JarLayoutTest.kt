package dev.housepoints.app

import dev.housepoints.app.ui.components.JarLayout
import dev.housepoints.app.ui.components.JarModel
import dev.housepoints.contracts.Points
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The jar should read at a glance for a six-year-old: big coins for small numbers, the goal line high up. */
class JarLayoutTest {
    private fun goalHeight(model: JarModel): Double {
        val layout = JarLayout.of(model)
        return layout.levelsFor(model.goalCoins ?: 0) * layout.step / JarLayout.USABLE_HEIGHT
    }

    @Test
    fun `a small goal sits high in the jar with big coins`() {
        val bea = JarModel.of(Points(50), Points(150))
        val layout = JarLayout.of(bea)
        assertEquals(2, layout.columns)
        val height = goalHeight(bea)
        assertTrue("goal line at $height of the jar", height in 0.6..0.8)
    }

    @Test
    fun `goal lines land in the upper part of the jar across sensible goals`() {
        listOf(30L, 80L, 150L, 400L, 900L, 2000L).forEach { target ->
            val height = goalHeight(JarModel.of(Points(target / 3), Points(target)))
            assertTrue("goal $target at $height", height in 0.4..0.82)
        }
    }

    @Test
    fun `coins always fit and get smaller only as they get more numerous`() {
        var lastColumns = 0
        listOf(10L, 100L, 300L, 800L, 1500L, 2000L, 2500L, 19_000L).forEach { balance ->
            val model = JarModel.of(Points(balance), null)
            val layout = JarLayout.of(model)
            assertTrue("columns grow with coins", layout.columns >= lastColumns || model.coinValue > 10)
            lastColumns = layout.columns
            assertTrue("$balance fits", layout.levelsFor(model.coins) * layout.step <= JarLayout.USABLE_HEIGHT + 0.001)
            assertTrue("coins touch", layout.step <= layout.coinHeight)
        }
    }

    @Test
    fun `an empty jar with no goal still has a sensible layout`() {
        val layout = JarLayout.of(JarModel.of(Points(0), null))
        assertEquals(2, layout.columns)
        assertTrue(layout.step > 0)
    }
}
