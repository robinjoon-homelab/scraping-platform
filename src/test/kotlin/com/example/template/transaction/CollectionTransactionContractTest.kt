package com.example.template.transaction

import com.example.template.application.port.input.CollectionTaskUseCase
import com.example.template.application.port.input.SourceUseCase
import com.example.template.application.port.input.StaleTaskVersionException
import com.example.template.domain.CollectionTarget
import com.example.template.domain.CollectionTask
import com.example.template.domain.IntervalSchedule
import com.example.template.domain.SourceDefinition
import com.example.template.domain.SourceKind
import com.example.template.domain.TaskDefinition
import com.example.template.transaction.TransactionTestConfig.ConfigurationScenario
import com.example.template.transaction.TransactionTestConfig.RecordingTaskRepository
import com.example.template.transaction.TransactionTestConfig.TransactionObservations
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.sql.Connection
import java.sql.SQLException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.sql.DataSource

@ActiveProfiles("test")
@Import(TransactionTestConfig::class)
abstract class CollectionTransactionContractTest {
    @Autowired
    private lateinit var sources: SourceUseCase

    @Autowired
    private lateinit var tasks: CollectionTaskUseCase

    @Autowired
    private lateinit var scenario: ConfigurationScenario

    @Autowired
    private lateinit var observations: TransactionObservations

    @Autowired
    private lateinit var recordingTasks: RecordingTaskRepository

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var context: ApplicationContext

    @BeforeEach
    fun clearDatabase() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM collection_tasks")
                statement.executeUpdate("DELETE FROM sources")
            }
        }
        observations.clear()
        recordingTasks.stopSynchronizingReads()
    }

    @AfterEach
    fun transactionStateIsReleased() {
        recordingTasks.stopSynchronizingReads()
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse()
        assertThat(TransactionManager.currentOrNull()).isNull()
    }

    @Test
    fun `Exposed is the only Spring transaction manager and both use cases are proxied`() {
        val managers = context.getBeansOfType(PlatformTransactionManager::class.java)

        assertThat(managers).containsOnlyKeys("springTransactionManager")
        assertThat(managers.values.single()).isInstanceOf(SpringTransactionManager::class.java)
        assertThat(AopUtils.isAopProxy(sources)).isTrue()
        assertThat(AopUtils.isAopProxy(tasks)).isTrue()
        assertThat(AopUtils.isAopProxy(scenario)).isTrue()
    }

    @Test
    fun `source creation commits before returning to its caller`() {
        sources.create(sourceDefinition())

        assertThat(storedSourceNames()).containsExactly("Market source")
        assertWriteTransaction("source.insert")
    }

    @Test
    fun `task creation keeps source lookup and insert in one writable transaction`() {
        val source = sources.create(sourceDefinition())
        observations.clear()

        tasks.create(taskDefinition(source.id))

        assertThat(storedTaskNames()).containsExactly("Market task")
        assertWriteTransaction("source.findById", "task.insert")
    }

    @Test
    fun `source and task queries run inside read only Spring and Exposed transactions`() {
        val source = sources.create(sourceDefinition())
        val task = tasks.create(taskDefinition(source.id))
        observations.clear()

        assertThat(sources.get(source.id)).isEqualTo(source)
        assertThat(sources.list(0, 10)).containsExactly(source)
        assertThat(tasks.get(task.id)).isEqualTo(task)
        assertThat(tasks.list(0, 10)).containsExactly(task)

        assertThat(observations.snapshot().map { it.operation })
            .containsExactly("source.findById", "source.findAll", "task.findById", "task.findAll")
        assertThat(observations.snapshot()).allSatisfy { observation ->
            assertThat(observation.active).isTrue()
            assertThat(observation.readOnly).isTrue()
            assertThat(observation.exposedReadOnly).isTrue()
            assertThat(observation.isolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
        }
    }

    @Test
    fun `task replacement keeps both lookups and update in one writable transaction`() {
        val task = createTask()
        observations.clear()

        val replaced = tasks.replace(task.id, task.version, task.definition.copy(name = "Updated task"))

        assertThat(replaced.version).isEqualTo(1)
        assertThat(storedTaskNames()).containsExactly("Updated task")
        assertWriteTransaction("task.findById", "source.findById", "task.update")
    }

    @Test
    fun `enabling keeps the internal read and update in one writable transaction`() {
        val task = createTask()
        observations.clear()

        val enabled = tasks.setEnabled(task.id, task.version, true)

        assertThat(enabled.enabled).isTrue()
        assertThat(enabled.version).isEqualTo(1)
        assertWriteTransaction("task.findById", "task.update")
        assertThat(tasks.get(task.id)).isEqualTo(enabled)
    }

    @Test
    fun `required use cases join one outer transaction and commit both entities`() {
        scenario.createConfiguration()

        assertWriteTransaction("source.insert", "source.findById", "task.insert")
        assertThat(storedSourceNames()).containsExactly("Scenario source")
        assertThat(storedTaskNames()).containsExactly("Scenario task")
    }

    @Test
    fun `failure after source and task writes rolls back the whole outer transaction`() {
        assertThatThrownBy { scenario.createConfigurationThenFail() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessage("Fail after source and task writes")

        assertWriteTransaction("source.insert", "source.findById", "task.insert")
        assertThat(storedSourceNames()).isEmpty()
        assertThat(storedTaskNames()).isEmpty()
    }

    @Test
    fun `checked SQL failure after both writes rolls back under the Exposed default policy`() {
        assertThatThrownBy { scenario.createConfigurationThenSqlFailure() }
            .isInstanceOf(SQLException::class.java)
            .hasMessage("SQL failure after source and task writes")

        assertWriteTransaction("source.insert", "source.findById", "task.insert")
        assertThat(storedSourceNames()).isEmpty()
        assertThat(storedTaskNames()).isEmpty()
    }

    @Test
    fun `competing service calls that read the same version allow exactly one update`() {
        val task = createTask()
        observations.clear()
        recordingTasks.synchronizeTwoReads()

        val results =
            Executors.newVirtualThreadPerTaskExecutor().use { executor ->
                val writers = List(2) { Callable { tasks.setEnabled(task.id, task.version, true) } }
                executor.invokeAll(writers).map { resultOrStale(it) }
            }
        recordingTasks.stopSynchronizingReads()

        assertThat(results.filterNotNull()).hasSize(1)
        assertThat(results.count { it == null }).isEqualTo(1)
        val concurrent = observations.snapshot()
        assertThat(concurrent.map { it.transactionId }.distinct()).hasSize(2)
        assertThat(concurrent.groupBy { it.transactionId }.values).allSatisfy { transaction ->
            assertThat(transaction.map { it.operation }).containsExactly("task.findById", "task.update")
            assertThat(transaction).allSatisfy { assertThat(it.readOnly).isFalse() }
        }
        val saved = tasks.get(task.id)
        assertThat(saved).isEqualTo(results.filterNotNull().single())
        assertThat(saved.version).isEqualTo(1)
    }

    private fun resultOrStale(result: Future<CollectionTask>): CollectionTask? =
        try {
            result.get()
        } catch (failure: ExecutionException) {
            assertThat(failure.cause).isInstanceOf(StaleTaskVersionException::class.java)
            null
        }

    private fun createTask(): CollectionTask {
        val source = sources.create(sourceDefinition())
        return tasks.create(taskDefinition(source.id))
    }

    private fun sourceDefinition(): SourceDefinition = SourceDefinition("Market source", SourceKind.HTTP_JSON, "https://example.com")

    private fun taskDefinition(sourceId: UUID): TaskDefinition =
        TaskDefinition(
            name = "Market task",
            sourceId = sourceId,
            target = CollectionTarget("/observations", mapOf("series" to "GDP")),
            schedule = IntervalSchedule(3600, Instant.parse("2026-01-01T00:00:00Z")),
        )

    private fun assertWriteTransaction(vararg operations: String) {
        val recorded = observations.snapshot()
        assertThat(recorded.map { it.operation }).containsExactly(*operations)
        assertThat(recorded.map { it.transactionId }.distinct()).hasSize(1)
        assertThat(recorded).allSatisfy { observation ->
            assertThat(observation.active).isTrue()
            assertThat(observation.readOnly).isFalse()
            assertThat(observation.exposedReadOnly).isFalse()
            assertThat(observation.isolation).isEqualTo(Connection.TRANSACTION_READ_COMMITTED)
        }
    }

    private fun storedSourceNames(): List<String> = storedNames("SELECT display_name FROM sources ORDER BY display_name")

    private fun storedTaskNames(): List<String> = storedNames("SELECT display_name FROM collection_tasks ORDER BY display_name")

    // A fresh connection observes committed state after the service proxy has returned.
    private fun storedNames(sql: String): List<String> =
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { result ->
                    generateSequence { if (result.next()) result.getString(1) else null }.toList()
                }
            }
        }
}
