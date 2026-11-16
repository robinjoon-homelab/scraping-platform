package com.example.template.ui

import com.example.template.support.web.pageMockMvc
import com.example.template.support.web.renderedPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

private const val FAILED_NOTICE = "아이디 또는 비밀번호가 올바르지 않습니다."
private const val LOCKED_NOTICE = "로그인 시도가 너무 많습니다. 잠시 후 다시 시도하세요."
private const val LOGGED_OUT_NOTICE = "로그아웃했습니다."
private const val INTERNAL_DETAIL = "internal-failure-detail"

class UiKitRenderingTest {
    private val mockMvc = pageMockMvc(UiKitPreviewController())

    @Test
    fun `login page shows the form without a notice`() {
        val html = mockMvc.renderedPage("/preview/login", "login")

        assertThat(html).contains(
            "로그인",
            "name=\"username\"",
            "name=\"password\"",
            "autocomplete=\"current-password\"",
            "action=\"/admin/login\"",
        )
        assertThat(html).doesNotContain(FAILED_NOTICE, LOCKED_NOTICE, LOGGED_OUT_NOTICE)
    }

    @Test
    fun `failed login shows why the login was rejected`() {
        assertThat(mockMvc.renderedPage("/preview/login?error", "login-error")).contains(FAILED_NOTICE)
    }

    @Test
    fun `locked login tells the user to wait`() {
        assertThat(mockMvc.renderedPage("/preview/login?locked", "login-locked")).contains(LOCKED_NOTICE)
    }

    @Test
    fun `after logout the login page confirms it`() {
        assertThat(mockMvc.renderedPage("/preview/login?logout", "login-logout")).contains(LOGGED_OUT_NOTICE)
    }

    @ParameterizedTest
    @CsvSource(
        "403, 접근 권한이 없습니다",
        "404, 페이지를 찾을 수 없습니다",
        "4xx, 요청을 처리할 수 없습니다",
        "5xx, 일시적인 오류가 발생했습니다",
    )
    fun `error pages show their own message and no internal detail`(
        name: String,
        message: String,
    ) {
        val html = mockMvc.renderedPage("/preview/error/$name", "error-$name")

        assertThat(html).contains(message)
        assertThat(html).doesNotContain(INTERNAL_DETAIL)
    }

    @Test
    fun `shared components render through the shell`() {
        val html = mockMvc.renderedPage("/preview/components", "components")

        assertThat(html).contains(
            "aria-current=\"page\"",
            "role=\"status\"",
            "ui-notice-error",
            "ui-badge-success",
            "ui-empty",
            "action=\"/admin/logout\"",
        )
    }
}

@Controller
class UiKitPreviewController {
    @GetMapping("/preview/login")
    fun login(): String = "app/login"

    @GetMapping("/preview/components")
    fun components(): String = "preview/components"

    // Carries the attributes Spring Boot exposes to real error views, so leaking any of them fails the test.
    @GetMapping("/preview/error/{name}")
    fun errorPage(
        @PathVariable name: String,
        model: Model,
    ): String {
        model.addAttribute("status", name.replace('x', '0').toInt())
        model.addAttribute("message", INTERNAL_DETAIL)
        model.addAttribute("exception", INTERNAL_DETAIL)
        model.addAttribute("trace", INTERNAL_DETAIL)
        return "error/$name"
    }
}
