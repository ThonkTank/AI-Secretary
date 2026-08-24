package de.thonktank.autosecretary

import org.junit.Assert.assertEquals
import org.junit.Test

class BuiltInKotlinSmokeTest {
    @Test
    fun kotlinSourcesCompileThroughAgp() {
        assertEquals("built-in-kotlin", listOf("built", "in", "kotlin").joinToString("-"))
    }
}
