package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.adapter.inbound.web.view.SourceForm
import com.example.template.collection.adapter.inbound.web.view.SourceView
import com.example.template.collection.application.port.input.SourceUseCase
import jakarta.validation.Valid
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/admin/sources")
class SourcePageController(
    private val sources: SourceUseCase,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") offset: Int,
        model: Model,
    ): String {
        model.addAttribute("sources", sources.list(offset, 20).map(SourceView::from))
        model.addAttribute("offset", offset)
        model.addAttribute("form", SourceForm())
        return "admin/sources"
    }

    @PostMapping
    fun create(
        @Valid @ModelAttribute form: SourceForm,
        errors: BindingResult,
        flash: RedirectAttributes,
    ): String {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("noticeError", "출처 이름과 올바른 HTTP(S) 주소를 입력하세요.")
        } else {
            sources.create(form.definition())
            flash.addFlashAttribute("notice", "출처를 등록했습니다. 계획에서 수집 대상을 지정하세요.")
        }
        return "redirect:/admin/sources"
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalid(flash: RedirectAttributes): String {
        flash.addFlashAttribute("noticeError", "출처 설정이 올바르지 않습니다. 공개 HTTP(S) 주소와 이름을 확인하세요.")
        return "redirect:/admin/sources"
    }
}
