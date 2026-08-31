package com.frustradar.fusion

import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [CategoryMapper].
 *
 * Covers exact boundary mapping: [min,max) lower-inclusive, upper-exclusive.
 * Boundary values map to the higher band.
 * Top band includes 100: [80, 100].
 */
class CategoryMapperTest {

    private val mapper = CategoryMapper()

    @Test
    fun `0 maps to calm`() {
        assertEquals(CategoryMapper.CALM, mapper.mapToCategory(0f))
    }

    @Test
    fun `19_99 maps to calm`() {
        assertEquals(CategoryMapper.CALM, mapper.mapToCategory(19.99f))
    }

    @Test
    fun `20 maps to mild (boundary maps upward)`() {
        assertEquals(CategoryMapper.MILD, mapper.mapToCategory(20f))
    }

    @Test
    fun `39_99 maps to mild`() {
        assertEquals(CategoryMapper.MILD, mapper.mapToCategory(39.99f))
    }

    @Test
    fun `40 maps to moderate`() {
        assertEquals(CategoryMapper.MODERATE, mapper.mapToCategory(40f))
    }

    @Test
    fun `59_99 maps to moderate`() {
        assertEquals(CategoryMapper.MODERATE, mapper.mapToCategory(59.99f))
    }

    @Test
    fun `60 maps to high`() {
        assertEquals(CategoryMapper.HIGH, mapper.mapToCategory(60f))
    }

    @Test
    fun `79_99 maps to high`() {
        assertEquals(CategoryMapper.HIGH, mapper.mapToCategory(79.99f))
    }

    @Test
    fun `80 maps to critical`() {
        assertEquals(CategoryMapper.CRITICAL, mapper.mapToCategory(80f))
    }

    @Test
    fun `100 maps to critical`() {
        assertEquals(CategoryMapper.CRITICAL, mapper.mapToCategory(100f))
    }

    @Test
    fun `mid-range values`() {
        assertEquals(CategoryMapper.CALM, mapper.mapToCategory(10f))
        assertEquals(CategoryMapper.MILD, mapper.mapToCategory(30f))
        assertEquals(CategoryMapper.MODERATE, mapper.mapToCategory(50f))
        assertEquals(CategoryMapper.HIGH, mapper.mapToCategory(70f))
        assertEquals(CategoryMapper.CRITICAL, mapper.mapToCategory(90f))
    }
}
