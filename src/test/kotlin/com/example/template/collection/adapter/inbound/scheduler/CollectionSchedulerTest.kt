package com.example.template.collection.adapter.inbound.scheduler

import com.example.template.collection.application.port.input.CollectionSchedulingUseCase
import com.example.template.collection.application.port.input.CollectionWorkerUseCase
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CollectionSchedulerTest {
    private val worker = mockk<CollectionWorkerUseCase>()
    private val scheduling = mockk<CollectionSchedulingUseCase>()
    private val scheduler = CollectionScheduler(worker, scheduling, 7)

    @Test
    fun `failing steps do not end the cycle with an exception`() {
        every { worker.expireLeases(7) } throws IllegalStateException("database password=secret")
        every { scheduling.scheduleDue(7) } throws IllegalStateException("scheduling down")
        every { worker.runPending(7) } throws IllegalStateException("source down")

        assertThatCode { scheduler.runCycle() }.doesNotThrowAnyException()
    }

    @Test
    fun `a JVM error from a step after failing steps still reaches the caller`() {
        every { worker.expireLeases(7) } throws IllegalStateException("database password=secret")
        every { scheduling.scheduleDue(7) } returns 1
        every { worker.runPending(7) } throws StackOverflowError()

        assertThatThrownBy { scheduler.runCycle() }.isInstanceOf(StackOverflowError::class.java)
    }

    @Test
    fun `an interruption stops the cycle`() {
        every { worker.expireLeases(7) } throws InterruptedException()

        assertThatThrownBy { scheduler.runCycle() }.isInstanceOf(InterruptedException::class.java)
    }
}
