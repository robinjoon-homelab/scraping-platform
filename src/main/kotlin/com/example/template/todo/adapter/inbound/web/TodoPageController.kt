package com.example.template.todo.adapter.inbound.web

import com.example.template.todo.adapter.inbound.web.view.TodoListView
import com.example.template.todo.application.port.input.TodoNotFoundException
import com.example.template.todo.application.port.input.TodoUseCase
import com.example.template.todo.domain.todo.InvalidTodoTitleException
import com.example.template.todo.domain.todo.TodoId
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

private const val LIST_PATH = "redirect:/admin/todos"

// Domain failures become flash notices on the list page, because these local handlers win over the global ApiExceptionHandler.
@Controller
@RequestMapping("/admin/todos")
class TodoPageController(
    private val todoUseCase: TodoUseCase,
) {
    @GetMapping
    fun list(model: Model): String {
        model.addAttribute("view", TodoListView.from(todoUseCase.list()))
        return "admin/todos"
    }

    @PostMapping
    fun create(
        @Valid @ModelAttribute form: CreateTodoForm,
        binding: BindingResult,
        redirect: RedirectAttributes,
    ): String {
        val invalid = binding.allErrors.firstOrNull()
        if (invalid != null) {
            return notify(redirect, invalid.defaultMessage ?: INVALID_TITLE, true)
        }
        todoUseCase.create(form.title)
        return notify(redirect, "추가했습니다.", false)
    }

    @PostMapping("/{id}/complete")
    fun complete(
        @PathVariable id: UUID,
        redirect: RedirectAttributes,
    ): String {
        todoUseCase.complete(TodoId(id))
        return notify(redirect, "완료했습니다.", false)
    }

    @ExceptionHandler(InvalidTodoTitleException::class)
    fun handleInvalidTitle(redirect: RedirectAttributes): String = notify(redirect, INVALID_TITLE, true)

    @ExceptionHandler(TodoNotFoundException::class)
    fun handleNotFound(redirect: RedirectAttributes): String = notify(redirect, "이미 삭제되었거나 없는 항목입니다.", true)

    private fun notify(
        redirect: RedirectAttributes,
        text: String,
        isError: Boolean,
    ): String {
        redirect.addFlashAttribute("notice", text)
        redirect.addFlashAttribute("noticeError", isError)
        return LIST_PATH
    }

    private companion object {
        const val INVALID_TITLE = "제목을 확인하세요."
    }
}

data class CreateTodoForm(
    @field:NotBlank(message = "제목을 입력하세요.")
    @field:Size(max = 200, message = "제목은 200자 이하로 입력하세요.")
    val title: String = "",
)
