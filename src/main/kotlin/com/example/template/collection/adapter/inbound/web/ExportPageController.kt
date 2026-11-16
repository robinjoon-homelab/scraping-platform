package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.adapter.inbound.web.view.RecordView
import com.example.template.collection.application.port.input.CollectionExportUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import java.util.UUID

@Controller
@RequestMapping("/admin/plans/{id}/export")
class ExportPageController(
    private val exports: CollectionExportUseCase,
) {
    @GetMapping
    fun status(
        @PathVariable id: UUID,
        model: Model,
    ): String {
        val status = exports.status(id)
        model.addAttribute("planId", id)
        model.addAttribute("enabled", status.enabled)
        model.addAttribute("publishedThrough", status.publishedThrough)
        model.addAttribute("publishedCount", status.publishedCount)
        model.addAttribute("pullUrl", "/api/collection-tasks/$id/export/records?after=0&limit=20")
        return "admin/export"
    }

    @PostMapping
    fun configure(
        @PathVariable id: UUID,
        @RequestParam enabled: Boolean,
        flash: RedirectAttributes,
    ): String {
        exports.configure(id, enabled)
        flash.addFlashAttribute("notice", if (enabled) "인증된 Pull 내보내기를 열었습니다." else "내보내기를 중지했습니다.")
        return "redirect:/admin/plans/$id/export"
    }

    @GetMapping("/records")
    fun records(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") after: Long,
        model: Model,
    ): String {
        val page = exports.pull(id, after, 20)
        model.addAttribute("planId", id)
        model.addAttribute("records", page.items.map { RecordView.from(it.record) })
        model.addAttribute("positions", page.items.map { it.sequence })
        model.addAttribute("revisions", page.items.map { it.task.version })
        model.addAttribute("nextAfter", page.nextAfter)
        return "admin/export-records"
    }
}
