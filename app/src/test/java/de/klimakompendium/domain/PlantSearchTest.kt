package de.klimakompendium.domain

import de.klimakompendium.domain.model.AccessType
import de.klimakompendium.domain.model.Controller
import de.klimakompendium.domain.model.Plant
import de.klimakompendium.domain.search.PlantFilter
import de.klimakompendium.domain.search.PlantSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class PlantSearchTest {
    private fun plant(id: Long, name: String, customer: String, ref: String, maker: String, ctrl: String = "") = Plant(
        id = id, name = name, customer = customer, location = "Werk $id", buildYear = 2020, refrigerant = ref,
        manufacturer = maker, serialNumber = "SN$id", controllerType = ctrl, notes = "", lastOpenedAt = null,
        controllers = if (ctrl.isEmpty()) emptyList() else listOf(
            Controller(id, id, "Hauptregler", ctrl, AccessType.WEB, "192.168.1.$id", "admin", true, ""),
        ),
    )

    private val plants = listOf(
        plant(1, "Kühlhaus Nord", "Müller GmbH", "R290", "Bitzer", "Carel pCO5"),
        plant(2, "Serverraum", "Schmidt AG", "R410A", "Daikin", "Daikin D3"),
        plant(3, "Büro 3. OG", "Müller GmbH", "R32", "Mitsubishi"),
    )

    @Test fun emptyFilterReturnsAll() = assertEquals(3, PlantSearch.filter(plants, PlantFilter()).size)

    @Test fun searchByNameCustomerControllerAndRefrigerant() {
        assertEquals(listOf(1L), PlantSearch.filter(plants, PlantFilter(query = "kühlhaus")).map { it.id })
        assertEquals(listOf(1L, 3L), PlantSearch.filter(plants, PlantFilter(query = "muller")).map { it.id })
        assertEquals(listOf(1L), PlantSearch.filter(plants, PlantFilter(query = "pco5")).map { it.id })
        assertEquals(listOf(3L), PlantSearch.filter(plants, PlantFilter(query = "r32")).map { it.id })
        assertEquals(listOf(2L), PlantSearch.filter(plants, PlantFilter(query = "192.168.1.2")).map { it.id })
    }

    @Test fun toleratesTypos() {
        assertEquals(listOf(2L), PlantSearch.filter(plants, PlantFilter(query = "Serverram")).map { it.id })
    }

    @Test fun filtersCombine() {
        assertEquals(listOf(3L), PlantSearch.filter(plants, PlantFilter(customer = "Müller GmbH", refrigerant = "R32")).map { it.id })
        assertEquals(listOf(2L), PlantSearch.filter(plants, PlantFilter(manufacturer = "daikin")).map { it.id })
        assertEquals(0, PlantSearch.filter(plants, PlantFilter(query = "kühlhaus", refrigerant = "R32")).size)
    }
}
