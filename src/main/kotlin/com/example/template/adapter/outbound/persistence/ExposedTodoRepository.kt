package com.example.template.adapter.outbound.persistence

import com.example.template.application.port.output.TodoRepository
import com.example.template.domain.Todo
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.ZoneOffset
import java.util.UUID

class ExposedTodoRepository(
    private val database: Database,
) : TodoRepository {
    override fun save(todo: Todo): Todo =
        transaction(db = database) {
            TodosTable.upsert(TodosTable.id) {
                it[id] = todo.id
                it[title] = todo.title
                it[completed] = todo.completed
                it[createdAt] = todo.createdAt.atOffset(ZoneOffset.UTC)
            }
            TodosTable
                .selectAll()
                .where { TodosTable.id eq todo.id }
                .single()
                .toTodo()
        }

    override fun findById(id: UUID): Todo? =
        transaction(db = database) {
            TodosTable
                .selectAll()
                .where { TodosTable.id eq id }
                .singleOrNull()
                ?.toTodo()
        }

    override fun findAll(): List<Todo> =
        transaction(db = database) {
            TodosTable
                .selectAll()
                .orderBy(TodosTable.createdAt to SortOrder.ASC, TodosTable.id to SortOrder.ASC)
                .map { it.toTodo() }
        }

    private fun ResultRow.toTodo(): Todo =
        Todo(
            id = this[TodosTable.id],
            title = this[TodosTable.title],
            completed = this[TodosTable.completed],
            createdAt = this[TodosTable.createdAt].toInstant(),
        )
}
