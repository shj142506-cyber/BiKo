package com.biko.client.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** 纯函数数值 / 时长格式化的单元测试。 */
class FormatTest {

    @Test
    fun formatCount_belowTenThousand() {
        assertEquals("0", formatCount(0))
        assertEquals("9999", formatCount(9999))
    }

    @Test
    fun formatCount_wan() {
        assertEquals("1.0万", formatCount(10_000))
        assertEquals("12.3万", formatCount(123_456))
    }

    @Test
    fun formatCount_yi() {
        assertEquals("1.0亿", formatCount(100_000_000))
    }

    @Test
    fun formatDuration_belowOneHour() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("0:59", formatDuration(59))
        assertEquals("1:00", formatDuration(60))
        assertEquals("59:59", formatDuration(3599))
    }

    @Test
    fun formatDuration_overOneHour() {
        assertEquals("1:00:00", formatDuration(3600))
        assertEquals("1:15:30", formatDuration(4530))
        assertEquals("2:05:09", formatDuration(7509))
    }

    @Test
    fun formatDuration_negativeClampedToZero() {
        assertEquals("0:00", formatDuration(-5))
    }
}
