package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.adapter.inbound.web.view.RecordView
import com.example.template.collection.adapter.inbound.web.view.RunView
import com.example.template.collection.application.port.input.CollectionRunQueryUseCase
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

@Controller
class RunPageController(
    private val runs: CollectionRunQueryUseCase,
) {
    @GetMapping("/admin/plans/{id}/runs")
    fun list(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") offset: Int,
        model: Model,
    ): String {
        model.addAttribute("planId", id)
        model.addAttribute("runs", runs.listRuns(id, offset, 20).map(RunView::from))
        model.addAttribute("offset", offset)
        return "admin/runs"
    }

    @GetMapping("/admin/runs/{id}")
    fun detail(
        @PathVariable id: UUID,
        @RequestParam(defaultValue = "0") offset: Int,
        model: Model,
    ): String {
        val run = runs.getRun(id)
        model.addAttribute("run", RunView.from(run))
        model.addAttribute("planId", run.task.taskId)
        model.addAttribute(
            "records",
            runs.listRecords(id, offset, 20).map {
                mapOf(
                    "id" to it.id.toString(),
                    "key" to it.identity.sourceRecordKey,
                    "hash" to it.identity.revisionHash,
                    "bytes" to it.payloadBytes.toString(),
                )
            },
        )
        model.addAttribute("offset", offset)
        return "admin/run"
    }

    @GetMapping("/admin/records/{id}")
    fun record(
        @PathVariable id: UUID,
        model: Model,
    ): String {
        model.addAttribute("record", RecordView.from(runs.getRecord(id)))
        return "admin/record"
    }
}
