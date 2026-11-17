package br.com.devoaceitar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfitProjectionTest {
    private val plan = WorkPlan(5, 6.0, 150.0, 1200.0, 520.0, 520.0)

    @Test fun `combina custo variável e fixo com a mesma base das metas`() {
        val fuel = EnergyCost(EnergySource.GASOLINE, 5.6, 8.0)
        val base = plan.project(fuel.reaisPerKm)
        assertEquals(655.0, base.costs!!, 0.000001)
        assertEquals(109.0, base.forPeriod(WorkPeriod.DAY).net!!, 0.000001)
        assertEquals(5200.0, base.forPeriod(WorkPeriod.MONTH).gross!!, 0.000001)
        val simulated = plan.project(fuel.reaisPerKm, 25)
        assertEquals(1200.0, plan.weeklyGoal!!, 0.0)
        assertEquals(2.0, simulated.grossPerKm!!, 0.000001)
        assertEquals(845.0 / 30, simulated.netPerHour!!, 0.000001)
        assertEquals(simulated.costPerKm!!,
            Preferences(energyCost = fuel, workPlan = plan, includeFixedCosts = true).reaisPerKm, 0.000001)
    }

    @Test fun `converte autonomia e capacidade em rendimento elétrico`() {
        val electric = electricRendimento(350.0, 70.0)
        assertEquals(5.0, electric!!, 0.0)
        assertEquals(0.24, EnergyCost(EnergySource.ELECTRIC, 1.2, electric).reaisPerKm, 0.000001)
        assertNull(electricRendimento(350.0, 0.0))
    }

    @Test fun `oferece resultados parciais sem dividir por zero`() {
        val partial = WorkPlan(daysPerWeek = 5, hoursPerDay = 0.0, weeklyGoal = 1200.0).project(0.9)
        assertEquals(240.0, partial.forPeriod(WorkPeriod.DAY).gross!!, 0.0)
        assertNull(partial.grossPerHour)
        assertNull(partial.grossPerKm)
        assertNull(partial.net)
    }
}
