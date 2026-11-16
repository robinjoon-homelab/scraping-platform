package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.CollectionOperationsUseCase
import com.example.template.collection.application.port.input.CollectionTaskUseCase
import com.example.template.collection.application.port.input.StaleTaskVersionException
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
@RequestMapping("/admin/plans/{id}")
class PlanOperationPageController(
    private val tasks: CollectionTaskUseCase,
    private val operations: CollectionOperationsUseCase,
) {
    @PostMapping("/enabled")
    fun enabled(
        @PathVariable id: UUID,
        @RequestParam version: Long,
        @RequestParam enabled: Boolean,
        flash: RedirectAttributes,
    ): String {
        tasks.setEnabled(id, version, enabled)
        flash.addFlashAttribute("notice", if (enabled) "계획을 활성화했습니다." else "계획을 중지했습니다. 실행 중인 작업은 완료될 수 있습니다.")
        return "redirect:/admin/plans/$id"
    }

    @PostMapping("/run")
    fun run(
        @PathVariable id: UUID,
        @RequestParam version: Long,
        @RequestParam requestKey: String,
    ): String {
        val run = operations.runNow(id, version, requestKey)
        return "redirect:/admin/runs/${run.id}"
    }

    @PostMapping("/preview")
    fun preview(
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String {
        val preview = operations.preview(id)
        flash.addFlashAttribute("preview", preview.records.map { it.payload.take(4000) })
        flash.addFlashAttribute("notice", "제한된 샘플입니다. 본문은 4,000자까지 표시하며 운영 자료·일정·진행 위치는 바뀌지 않습니다.")
        return "redirect:/admin/plans/$id"
    }

    @ExceptionHandler(IllegalArgumentException::class, StaleTaskVersionException::class)
    fun failed(
        @PathVariable id: UUID,
        flash: RedirectAttributes,
    ): String {
        flash.addFlashAttribute("noticeError", "요청을 처리하지 못했습니다. 설정·수집기 지원 여부·현재 버전을 확인하고 다시 시도하세요.")
        return "redirect:/admin/plans/$id"
    }
}
