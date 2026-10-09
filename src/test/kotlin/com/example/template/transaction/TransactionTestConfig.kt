package com.example.template.transaction

import com.example.template.application.port.input.CollectionTaskUseCase
import com.example.template.application.port.input.SourceUseCase
import com.example.template.application.port.output.CollectionTaskRepository
import com.example.template.application.port.output.SourceRepository
import com.example.template.domain.CollectionTarget
import com.example.template.domain.CollectionTask
import com.example.template.domain.IntervalSchedule
import com.example.template.domain.Source
import com.example.template.domain.SourceDefinition
import com.example.template.domain.SourceKind
import com.example.template.domain.TaskDefinition
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@TestConfiguration(proxyBeanMethods = false)
class TransactionTestConfig {
    @Bean
    fun transactionObservations(): TransactionObservations = TransactionObservations()

    @Bean
    @Primary
    fun recordingSourceRepository(
        @Qualifier("sourceRepository") delegate: SourceRepository,
        observations: TransactionObservations,
    ): RecordingSourceRepository = RecordingSourceRepository(delegate, observations)

    @Bean
    @Primary
    fun recordingTaskRepository(
        @Qualifier("collectionTaskRepository") delegate: CollectionTaskRepository,
        observations: TransactionObservations,
    ): RecordingTaskRepository = RecordingTaskRepository(delegate, observations)

    @Bean
    fun configurationScenario(
        sources: SourceUseCase,
        tasks: CollectionTaskUseCase,
    ): ConfigurationScenario = ConfigurationScenario(sources, tasks)

    @Transactional
    class ConfigurationScenario(
        private val sources: SourceUseCase,
        private val tasks: CollectionTaskUseCase,
    ) {
        fun createConfiguration(): CollectionTask {
            val source = sources.create(SourceDefinition("Scenario source", SourceKind.HTTP_JSON, "https://example.com"))
            return tasks.create(
                TaskDefinition(
                    name = "Scenario task",
                    sourceId = source.id,
                    target = CollectionTarget("/observations"),
                    schedule = IntervalSchedule(3600, Instant.parse("2026-01-01T00:00:00Z")),
                ),
            )
        }

        fun createConfigurationThenFail() {
            createConfiguration()
            error("Fail after source and task writes")
        }

        fun createConfigurationThenSqlFailure() {
            createConfiguration()
            throw SQLException("SQL failure after source and task writes")
        }
    }

    class RecordingSourceRepository(
        private val delegate: SourceRepository,
        private val observations: TransactionObservations,
    ) : SourceRepository {
        override fun insert(source: Source): Source {
            observations.record("source.insert")
            return delegate.insert(source)
        }

        override fun findById(id: UUID): Source? {
            observations.record("source.findById")
            return delegate.findById(id)
        }

        override fun findAll(
            offset: Int,
            limit: Int,
        ): List<Source> {
            observations.record("source.findAll")
            return delegate.findAll(offset, limit)
        }
    }

    class RecordingTaskRepository(
        private val delegate: CollectionTaskRepository,
        private val observations: TransactionObservations,
    ) : CollectionTaskRepository {
        private val readBarrier = AtomicReference<CyclicBarrier?>()

        override fun insert(task: CollectionTask): CollectionTask {
            observations.record("task.insert")
            return delegate.insert(task)
        }

        override fun findById(id: UUID): CollectionTask? {
            observations.record("task.findById")
            val task = delegate.findById(id)
            readBarrier.get()?.await(10, TimeUnit.SECONDS)
            return task
        }

        override fun findAll(
            offset: Int,
            limit: Int,
        ): List<CollectionTask> {
            observations.record("task.findAll")
            return delegate.findAll(offset, limit)
        }

        override fun update(
            task: CollectionTask,
            expectedVersion: Long,
        ): CollectionTask? {
            observations.record("task.update")
            return delegate.update(task, expectedVersion)
        }

        fun synchronizeTwoReads() {
            readBarrier.set(CyclicBarrier(2))
        }

        fun stopSynchronizingReads() {
            readBarrier.set(null)
        }
    }

    class TransactionObservations {
        private val recorded = ConcurrentLinkedQueue<TransactionObservation>()

        fun record(operation: String) {
            val transaction = TransactionManager.current()
            recorded +=
                TransactionObservation(
                    operation = operation,
                    active = TransactionSynchronizationManager.isActualTransactionActive(),
                    readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly(),
                    exposedReadOnly = transaction.readOnly,
                    transactionId = transaction.transactionId,
                    isolation = transaction.connection.transactionIsolation,
                )
        }

        fun snapshot(): List<TransactionObservation> = recorded.toList()

        fun clear() = recorded.clear()
    }

    data class TransactionObservation(
        val operation: String,
        val active: Boolean,
        val readOnly: Boolean,
        val exposedReadOnly: Boolean,
        val transactionId: String,
        val isolation: Int,
    )
}
