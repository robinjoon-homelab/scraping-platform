package com.example.template.todo.adapter.outbound.persistence

import com.example.template.todo.application.port.output.TodoRepository
import com.example.template.todo.domain.todo.Todo
import com.example.template.todo.domain.todo.TodoId
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.ZoneOffset

class ExposedTodoRepository : TodoRepository {
    override fun add(todo: Todo): Todo {
        TodosTable.insert {
            it[id] = todo.id.value
            it[title] = todo.title
            it[completed] = todo.completed
            it[createdAt] = todo.createdAt.atOffset(ZoneOffset.UTC)
        }
        return selectById(todo.id).single().toTodo()
    }

    // id and createdAt never change after creation, so only the mutable state is written.
    override fun update(todo: Todo): Todo {
        val updated =
            TodosTable.update({ TodosTable.id eq todo.id.value }) {
                it[title] = todo.title
                it[completed] = todo.completed
            }
        check(updated == 1) { "Todo to update does not exist: ${todo.id}" }
        return selectById(todo.id).single().toTodo()
    }

    override fun findById(id: TodoId): Todo? = selectById(id).singleOrNull()?.toTodo()

    override fun findAll(): List<Todo> =
        TodosTable
            .selectAll()
            .orderBy(TodosTable.createdAt to SortOrder.ASC, TodosTable.id to SortOrder.ASC)
            .map { it.toTodo() }

    private fun selectById(id: TodoId): Query = TodosTable.selectAll().where { TodosTable.id eq id.value }

    private fun ResultRow.toTodo(): Todo =
        Todo.restore(
            id = TodoId(this[TodosTable.id]),
            title = this[TodosTable.title],
            completed = this[TodosTable.completed],
            createdAt = this[TodosTable.createdAt].toInstant(),
        )
}
