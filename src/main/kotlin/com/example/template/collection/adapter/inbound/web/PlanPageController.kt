package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.adapter.inbound.web.view.PlanForm
import com.example.template.collection.adapter.inbound.web.view.PlanView
import com.example.template.collection.adapter.inbound.web.view.SourceView
import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.collection.application.port.input.SourceUseCase
import com.example.template.collection.application.port.input.StaleTaskVersionException
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.time.format.DateTimeParseException
import java.util.UUID

@Controller
@RequestMapping("/admin/plans")
class PlanPageController(
    private val tasks: CollectionTaskUseCase,
    private val sources: SourceUseCase,
    private val operations: CollectionOperationsUseCase,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") offset: Int,
        model: Model,
    ): String {
        model.addAttribute("plans", tasks.list(offset, 20).map(PlanView::from))
        model.addAttribute("offset", offset)
        return "admin/plans"
    }

    @GetMapping("/new")
    fun newPlan(
        @RequestParam(required = false) sourceId: UUID?,
        model: Model,
    ): String {
        model.addAttribute("form", PlanForm(sourceId = sourceId?.toString().orEmpty()))
        val choices = if (sourceId == null) sources.list(0, 100) else listOf(sources.get(sourceId))
        model.addAttribute("sources", choices.map(SourceView::from))
        return "admin/plan-form"
    }

    @GetMapping("/{id}")
    fun detail(
        @PathVariable id: UUID,
        model: Model,
    ): String {
        val task = tasks.get(id)
        model.addAttribute("plan", PlanView.from(task))
        model.addAttribute("form", PlanForm.from(task))
        model.addAttribute("sources", listOf(SourceView.from(sources.get(task.definition.sourceId))))
        val validation = operations.validate(id)
        model.addAttribute("executable", validation.executable)
        model.addAttribute(
            "issues",
            validation.issues.map {
                when {
                    it.startsWith("source.kind:") -> "출처 유형: 공개 HTTP JSON만 실행할 수 있습니다."
                    it.startsWith("source.credentialRef:") -> "출처 인증: 인증이 필요한 원천은 지원하지 않습니다."
                    else -> "설정 검증 실패: $it"
                }
            },
        )
        model.addAttribute("requestKey", UUID.randomUUID().toString())
        return "admin/plan-form"
    }

    @PostMapping
    fun create(
        @Valid @ModelAttribute form: PlanForm,
        errors: BindingResult,
        flash: RedirectAttributes,
    ): String {
        if (errors.hasErrors()) return invalid(flash)
        val task = tasks.create(form.definition())
        flash.addFlashAttribute("notice", "계획을 저장했습니다. 검증 후 활성화하거나 즉시 실행하세요.")
        return "redirect:/admin/plans/${task.id}"
    }

    @PostMapping("/{id}")
    fun update(
        @PathVariable id: UUID,
        @RequestParam version: Long,
        @Valid @ModelAttribute form: PlanForm,
        errors: BindingResult,
    ): String {
        require(!errors.hasErrors()) { "입력값을 확인하세요." }
        tasks.replace(id, version, form.definition())
        return "redirect:/admin/plans/$id"
    }

    @ExceptionHandler(IllegalArgumentException::class, DateTimeParseException::class, StaleTaskVersionException::class)
    fun invalid(flash: RedirectAttributes): String {
        flash.addFlashAttribute("noticeError", "저장하지 못했습니다. 입력 형식 또는 변경 충돌을 확인하고 계획을 다시 여세요.")
        return "redirect:/admin/plans"
    }
}
