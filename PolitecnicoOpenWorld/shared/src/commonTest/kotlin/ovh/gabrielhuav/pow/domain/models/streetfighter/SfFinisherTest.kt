package ovh.gabrielhuav.pow.domain.models.streetfighter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SfFinisherTest {

    private val tzitzi = SfFinisherCatalog.forFighter(SfFighterId.LA_TZITZIMIME)!!.command // ↓ ↓ ← + B

    private val neutral = SfInput()
    private val down = SfInput(down = true)
    private val back = SfInput(backward = true)
    private val downBack = SfInput(down = true, backward = true)

    /** Alimenta una secuencia de inputs, un tick cada [stepMs]; devuelve si ALGÚN tick completó. */
    private fun SfFinisherInputTracker.play(cmd: SfFinisherCommand, vararg inputs: SfInput, stepMs: Long = 60L): Boolean {
        var t = 1000L
        var done = false
        for (i in inputs) {
            if (feed(i, t, cmd)) done = true
            t += stepMs
        }
        return done
    }

    @Test
    fun `el comando exacto de la Tzitzimime se reconoce`() {
        val tr = SfFinisherInputTracker()
        val ok = tr.play(tzitzi, down, neutral, down, neutral, back, SfInput(backward = true, heavyPunch = true))
        assertTrue(ok, "↓ ↓ ← + B debería completar el remate")
    }

    @Test
    fun `mantener abajo cuenta como UNA sola direccion`() {
        val tr = SfFinisherInputTracker()
        val ok = tr.play(tzitzi, down, down, down, back, SfInput(heavyPunch = true))
        assertFalse(ok, "sostener ↓ no son dos toques")
    }

    @Test
    fun `la diagonal tactil entre abajo y atras no rompe el comando`() {
        val tr = SfFinisherInputTracker()
        // ↓, suelta, ↓, se desliza por ↙ hasta ←, B
        val ok = tr.play(tzitzi, down, neutral, down, downBack, back, SfInput(heavyPunch = true))
        assertTrue(ok, "la diagonal no emite token y no debería estorbar")
    }

    @Test
    fun `boton equivocado o fuera de tiempo no remata`() {
        val tr = SfFinisherInputTracker()
        assertFalse(tr.play(tzitzi, down, neutral, down, neutral, back, SfInput(lightPunch = true)), "X no es B")
        tr.reset()
        // Paso de 700 ms entre toques: el primer ↓ ya salió de la ventana de 1800 ms
        assertFalse(
            tr.play(tzitzi, down, neutral, down, neutral, back, SfInput(heavyPunch = true), stepMs = 700L),
            "demasiado lento",
        )
    }

    @Test
    fun `el hadouken detectado sigue contando como puno de su fuerza`() {
        val tr = SfFinisherInputTracker()
        val ok = tr.play(tzitzi, down, neutral, down, neutral, back, SfInput(special = SfAttackStrength.HEAVY))
        assertTrue(ok, "special HEAVY = botón B")
    }

    @Test
    fun `cualquier patada sirve para la Llorona`() {
        val llorona = SfFinisherCatalog.forFighter(SfFighterId.LA_LLORONA)!!.command // ← → ← + A
        val tr = SfFinisherInputTracker()
        val fwd = SfInput(forward = true)
        assertTrue(tr.play(llorona, back, neutral, fwd, neutral, back, SfInput(backward = true, heavyKick = true)), "patada fuerte")
        tr.reset()
        assertTrue(tr.play(llorona, back, neutral, fwd, neutral, back, SfInput(lightKick = true)), "patada ligera")
    }

    @Test
    fun `al completarse se consume y no se repite en el siguiente tick`() {
        val tr = SfFinisherInputTracker()
        val fin = SfInput(heavyPunch = true)
        assertTrue(tr.play(tzitzi, down, neutral, down, neutral, back, fin), "primera vez")
        assertFalse(tr.feed(fin, 9999L, tzitzi), "historial consumido")
    }

    @Test
    fun `subsecuencia en orden con huecos`() {
        val d = SfFinisherToken.DOWN
        val b = SfFinisherToken.BACK
        val f = SfFinisherToken.FORWARD
        assertTrue(SfFinisherInputTracker.containsInOrder(listOf(d, f, d, b), listOf(d, d, b)), "con ruido")
        assertFalse(SfFinisherInputTracker.containsInOrder(listOf(b, d, d), listOf(d, d, b)), "orden importa")
    }

    @Test
    fun `rangos de distancia`() {
        assertTrue(SfFinisherRange.CERCA.contains(60f), "60 px es cerca")
        assertFalse(SfFinisherRange.CERCA.contains(170f), "170 px no es cerca")
        assertTrue(SfFinisherRange.LEJOS.contains(176f), "la distancia inicial ya es lejos")
        assertFalse(SfFinisherRange.LEJOS.contains(90f), "90 px no es lejos")
        assertTrue(SfFinisherRange.CUALQUIERA.contains(0f), "cualquiera")
    }

    @Test
    fun `pista del HUD solo usa caracteres de la fuente arcade`() {
        for (def in SfFinisherCatalog.all) {
            for (en in listOf(false, true)) {
                val hint = def.command.hudHint(en)
                assertTrue(hint.all { it in 'A'..'Z' || it in '0'..'9' || it == ' ' }, "pista inválida: $hint")
            }
            assertTrue(def.nameEs.all { it in 'A'..'Z' || it == ' ' }, "nombre con caracteres fuera de la fuente: ${def.nameEs}")
        }
        assertEquals("ABAJO ABAJO ATRAS B CERCA", tzitzi.hudHint(english = false))
    }

    @Test
    fun `catalogo solo para los personajes con remate`() {
        assertNotNull(SfFinisherCatalog.forFighter(SfFighterId.LA_TZITZIMIME))
        assertNotNull(SfFinisherCatalog.forFighter(SfFighterId.LA_LLORONA))
        assertNotNull(SfFinisherCatalog.forFighter(SfFighterId.CHARRO_NEGRO))
        assertNull(SfFinisherCatalog.forFighter(SfFighterId.PRANKEDY), "sin remate → KO clásico")
    }

    @Test
    fun `cada cinematica termina con la victima desaparecida y el nombre en pantalla`() {
        for (def in SfFinisherCatalog.all) {
            val start = SfFinisherVisuals.cinematic(def, 1, 0L, "MOVIMIENTO FINAL", def.nameEs)
            val end = SfFinisherVisuals.cinematic(def, 1, def.durationMs, "MOVIMIENTO FINAL", def.nameEs)
            assertEquals(1f, start.victimAlpha, "${def.fighter}: al inicio se ve")
            assertEquals("", start.headline, "${def.fighter}: el nombre no se adelanta")
            assertEquals(0f, end.victimAlpha, "${def.fighter}: al final ya no está")
            assertEquals(def.nameEs, end.subline, "${def.fighter}: nombre al final")
            assertTrue(end.darkness in 0f..1f, "${def.fighter}: oscuridad válida")
        }
    }

    @Test
    fun `la Tzitzimime levanta a la victima y la encoge hasta cero`() {
        val def = SfFinisherCatalog.forFighter(SfFighterId.LA_TZITZIMIME)!!
        val mid = SfFinisherVisuals.cinematic(def, 1, 3000L, "", "")
        assertEquals(48f, mid.victimLiftPx, "levita 48 px")
        val end = SfFinisherVisuals.cinematic(def, 1, 4300L, "", "")
        assertEquals(0f, end.victimScale, "devorada por las estrellas")
    }

    @Test
    fun `la CPU mas dificil siempre remata`() {
        assertEquals(1f, SfFinisherCpu.chance(SfCpuDifficulty.PESADILLA))
        assertTrue(SfFinisherCpu.chance(SfCpuDifficulty.BASICA) < SfFinisherCpu.chance(SfCpuDifficulty.NORMAL))
    }
}
