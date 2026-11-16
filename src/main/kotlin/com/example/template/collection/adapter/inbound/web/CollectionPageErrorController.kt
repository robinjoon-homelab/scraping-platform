package com.example.template.collection.adapter.inbound.web

import com.example.template.collection.application.port.input.CollectedRecordNotFoundException
import com.example.template.collection.application.port.input.CollectionPreviewFailedException
import com.example.template.collection.application.port.input.CollectionRunNotFoundException
import com.example.template.collection.application.port.input.CollectionTaskNotFoundException
import com.example.template.collection.application.port.input.SourceNotFoundException
import com.example.template.collection.application.port.input.StaleTaskVersionException
import com.example.template.collection.domain.InvalidCollectionConfigurationException
import org.springframework.core.annotation.Order
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Order(-1)
@ControllerAdvice(
    assignableTypes = [
        SourcePageController::class,
        PlanPageController::class,
        PlanOperationPageController::class,
        RunPageController::class,
        ExportPageController::class,
    ],
)
class CollectionPageErrorController {
    @ExceptionHandler(
        CollectedRecordNotFoundException::class,
        CollectionPreviewFailedException::class,
        CollectionRunNotFoundException::class,
        CollectionTaskNotFoundException::class,
        SourceNotFoundException::class,
        StaleTaskVersionException::class,
        InvalidCollectionConfigurationException::class,
    )
    fun failure(flash: RedirectAttributes): String {
        flash.addFlashAttribute("noticeError", "요청을 처리하지 못했습니다. 대상이 존재하는지, 입력값과 수집기 지원 여부를 확인하세요. 잠시 후 다시 시도할 수 있습니다.")
        return "redirect:/admin/plans"
    }
}
