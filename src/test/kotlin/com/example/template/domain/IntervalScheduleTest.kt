package com.example.template.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

class IntervalScheduleTest {
    private val anchor = Instant.parse("2026-10-05T01:02:03.123456789Z")

    @ParameterizedTest
    @ValueSource(longs = [60, 31_536_000])
    fun `interval endpoints are accepted`(seconds: Long) {
        assertThat(IntervalSchedule(seconds, anchor).everySeconds).isEqualTo(seconds)
    }

    @ParameterizedTest
    @ValueSource(longs = [-1, 0, 59, 31_536_001, Long.MAX_VALUE])
    fun `intervals outside one minute through one year are rejected`(seconds: Long) {
        assertThatThrownBy { IntervalSchedule(seconds, anchor) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `a reference before the anchor returns the anchor`() {
        val schedule = IntervalSchedule(60, anchor)

        assertThat(schedule.nextAfter(anchor.minusNanos(1))).isEqualTo(anchor)
        assertThat(schedule.nextAfter(Instant.MIN)).isEqualTo(anchor)
    }

    @Test
    fun `the next time is strictly after an exact scheduled time`() {
        val schedule = IntervalSchedule(60, anchor)

        assertThat(schedule.nextAfter(anchor)).isEqualTo(anchor.plusSeconds(60))
        assertThat(schedule.nextAfter(anchor.plusSeconds(60))).isEqualTo(anchor.plusSeconds(120))
    }

    @Test
    fun `schedule preserves nanosecond anchor phase on either side of a slot`() {
        val schedule = IntervalSchedule(60, anchor)
        val slot = anchor.plusSeconds(60)

        assertThat(schedule.nextAfter(slot.minusNanos(1))).isEqualTo(slot)
        assertThat(schedule.nextAfter(slot.plusNanos(1))).isEqualTo(anchor.plusSeconds(120))
    }

    @Test
    fun `missed intervals are skipped without shifting the anchor phase`() {
        val schedule = IntervalSchedule(60, anchor)

        assertThat(schedule.nextAfter(anchor.plusSeconds(12_031))).isEqualTo(anchor.plusSeconds(12_060))
    }

    @ParameterizedTest
    @ValueSource(strings = ["1970-01-01T00:00:00Z", "9999-12-31T23:59:59.999999999Z"])
    fun `supported anchor timestamp endpoints are accepted`(timestamp: String) {
        val instant = Instant.parse(timestamp)

        assertThat(IntervalSchedule(60, instant).anchorAt).isEqualTo(instant)
    }

    @ParameterizedTest
    @ValueSource(strings = ["1969-12-31T23:59:59.999999999Z", "+10000-01-01T00:00:00Z"])
    fun `unsupported anchor timestamps are rejected`(timestamp: String) {
        assertThatThrownBy { IntervalSchedule(60, Instant.parse(timestamp)) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `a next time beyond the supported timestamp range is rejected`() {
        val schedule = IntervalSchedule(60, Instant.parse("9999-12-31T23:59:59Z"))

        assertThatThrownBy { schedule.nextAfter(schedule.anchorAt) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }
}
