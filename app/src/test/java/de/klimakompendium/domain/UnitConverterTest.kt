package de.klimakompendium.domain

import de.klimakompendium.domain.convert.Measurement
import de.klimakompendium.domain.convert.MeasurementParser
import de.klimakompendium.domain.convert.PressureReference
import de.klimakompendium.domain.convert.PressureUnit
import de.klimakompendium.domain.convert.TemperatureUnit
import de.klimakompendium.domain.convert.UnitConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitConverterTest {
    private val eps = 1e-6

    @Test fun barToOtherUnits() {
        assertEquals(100.0, UnitConverter.convertPressure(1.0, PressureUnit.BAR, PressureUnit.KPA), eps)
        assertEquals(0.1, UnitConverter.convertPressure(1.0, PressureUnit.BAR, PressureUnit.MPA), eps)
        assertEquals(14.5037738, UnitConverter.convertPressure(1.0, PressureUnit.BAR, PressureUnit.PSI), 1e-5)
        assertEquals(1000.0, UnitConverter.convertPressure(1.0, PressureUnit.BAR, PressureUnit.MBAR), eps)
    }

    @Test fun psiRoundTrip() {
        val psi = 145.0
        val back = UnitConverter.convertPressure(UnitConverter.convertPressure(psi, PressureUnit.PSI, PressureUnit.BAR), PressureUnit.BAR, PressureUnit.PSI)
        assertEquals(psi, back, 1e-9)
    }

    @Test fun temperatureFixpoints() {
        assertEquals(273.15, UnitConverter.convertTemperature(0.0, TemperatureUnit.CELSIUS, TemperatureUnit.KELVIN), eps)
        assertEquals(32.0, UnitConverter.convertTemperature(0.0, TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT), eps)
        assertEquals(212.0, UnitConverter.convertTemperature(100.0, TemperatureUnit.CELSIUS, TemperatureUnit.FAHRENHEIT), eps)
        assertEquals(-40.0, UnitConverter.convertTemperature(-40.0, TemperatureUnit.FAHRENHEIT, TemperatureUnit.CELSIUS), eps)
        assertEquals(-273.15, UnitConverter.convertTemperature(0.0, TemperatureUnit.KELVIN, TemperatureUnit.CELSIUS), eps)
    }

    @Test fun gaugeAndAbsolute() {
        assertEquals(11.01325, UnitConverter.toAbsoluteBar(10.0, PressureUnit.BAR, PressureReference.GAUGE), eps)
        assertEquals(10.0, UnitConverter.toAbsoluteBar(10.0, PressureUnit.BAR, PressureReference.ABSOLUTE), eps)
        assertEquals(10.0, UnitConverter.fromAbsoluteBar(11.01325, PressureUnit.BAR, PressureReference.GAUGE), eps)
        // 1000 kPa Überdruck = 10 bar(ü)
        assertEquals(11.01325, UnitConverter.toAbsoluteBar(1000.0, PressureUnit.KPA, PressureReference.GAUGE), eps)
    }

    @Test fun parseDecimalAcceptsCommaAndRejectsGarbage() {
        assertEquals(1.5, UnitConverter.parseDecimal("1,5")!!, eps)
        assertEquals(-3.0, UnitConverter.parseDecimal(" -3 ")!!, eps)
        assertNull(UnitConverter.parseDecimal("abc"))
        assertNull(UnitConverter.parseDecimal(""))
        assertNull(UnitConverter.parseDecimal("NaN"))
    }

    @Test fun measurementParser() {
        assertEquals(Measurement.Pressure(10.0, PressureUnit.BAR), MeasurementParser.parse("10 bar"))
        assertEquals(Measurement.Pressure(7.5, PressureUnit.KPA), MeasurementParser.parse("7,5kPa"))
        assertEquals(Measurement.Temperature(-5.0, TemperatureUnit.CELSIUS), MeasurementParser.parse("-5 °C"))
        assertEquals(Measurement.Temperature(300.0, TemperatureUnit.KELVIN), MeasurementParser.parse("300 K"))
        assertEquals(Measurement.Temperature(68.0, TemperatureUnit.FAHRENHEIT), MeasurementParser.parse("68f"))
        assertNull(MeasurementParser.parse("R32"))
        assertNull(MeasurementParser.parse("bar"))
    }

    @Test fun allConversionsContainEveryUnit() {
        assertTrue(UnitConverter.allPressures(1.0, PressureUnit.BAR).keys.containsAll(PressureUnit.entries))
        assertTrue(UnitConverter.allTemperatures(1.0, TemperatureUnit.CELSIUS).keys.containsAll(TemperatureUnit.entries))
    }
}
