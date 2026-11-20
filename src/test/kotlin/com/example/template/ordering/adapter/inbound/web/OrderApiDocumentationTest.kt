package com.example.template.ordering.adapter.inbound.web

import com.example.template.ordering.application.port.input.OfferNotOnSaleException
import com.example.template.ordering.application.port.input.OrderNotFoundException
import com.example.template.ordering.application.port.input.OrderUseCase
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.order.InvalidOrderException
import com.example.template.ordering.domain.order.Order
import com.example.template.ordering.domain.order.OrderId
import com.example.template.ordering.domain.order.OrderLine
import com.example.template.ordering.domain.order.OrderStatus
import com.example.template.support.web.RestDocsWebTest
import com.example.template.support.web.documented
import com.example.template.support.web.problemFields
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

// The use case is a strict mock that answers only the inputs a test stubs. The line count, distinct SKUs, offer
// checks, and repeated cancellation belong to OrderTest and OrderServiceTest; this test checks the HTTP responses the
// endpoint returns for requests, results, and failures.
class OrderApiDocumentationTest : RestDocsWebTest() {
    private val orderId: UUID = UUID.fromString("5d2a7c1e-3b4f-4a6d-9e8c-1f2a3b4c5d6e")
    private val missingId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val placedAt: Instant = Instant.parse("2026-10-13T00:00:00Z")
    private val orderUseCase = mockk<OrderUseCase>()
    private val mockMvc = restDocsMockMvc(OrderController(orderUseCase), OrderExceptionHandler())

    @Test
    fun `place with padded skus returns the placed order with lines in request order and its location`() {
        val lines = listOf(OrderLine(Sku("MS-001"), 1), OrderLine(Sku("KB-001"), 3))
        every { orderUseCase.place(lines) } returns Order.place(OrderId(orderId), lines, placedAt)

        place(linesBody("MS-001" to 1, "  KB-001  " to 3))
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/orders/$orderId"))
            .andExpect(content().json(orderJson("PLACED", "MS-001" to 1, "KB-001" to 3), JsonCompareMode.STRICT))
            .andDo(
                documented(
                    "orders-place",
                    requestFields(
                        fieldWithPath("lines").description("주문 라인 1~10개. 앞뒤 공백을 제거한 SKU가 같은 라인을 둘 이상 넣을 수 없습니다."),
                        fieldWithPath("lines[].sku").description(
                            "판매 중인 Offer의 SKU. 앞뒤 공백을 제거한 뒤 1~64자여야 하며 재고는 확인하지 않습니다.",
                        ),
                        fieldWithPath("lines[].quantity").description("주문 수량, 1~100"),
                    ),
                    responseHeaders(headerWithName("Location").description("생성된 주문의 조회 경로")),
                    responseFields(*orderFields()),
                ),
            )
    }

    @Test
    fun `get returns the order of the path id`() {
        every { orderUseCase.get(OrderId(orderId)) } returns order(OrderStatus.PLACED)

        mockMvc
            .perform(get("/api/orders/{id}", orderId))
            .andExpect(status().isOk)
            .andExpect(content().json(orderJson("PLACED", "KB-001" to 3), JsonCompareMode.STRICT))
            .andDo(documented("orders-get", orderIdParameter(), responseFields(*orderFields())))
    }

    @Test
    fun `cancel returns the cancelled order`() {
        every { orderUseCase.cancel(OrderId(orderId)) } returns order(OrderStatus.CANCELLED)

        mockMvc
            .perform(patch("/api/orders/{id}/cancel", orderId))
            .andExpect(status().isOk)
            .andExpect(content().json(orderJson("CANCELLED", "KB-001" to 3), JsonCompareMode.STRICT))
            .andDo(documented("orders-cancel", orderIdParameter(), responseFields(*orderFields())))
    }

    // Each line becomes an OrderLine before the use case runs, so line values fail here.
    @Test
    fun `invalid line values return a documented bad request`() {
        place(linesBody("KB-001" to 0))
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid order"))
            .andExpect(jsonPath("$.instance").value("/api/orders"))
            .andDo(documented("orders-invalid", responseFields(*problemFields())))
        place(linesBody("   " to 1))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid order"))
    }

    @Test
    fun `requests that do not bind to lines return a problem bad request`() {
        val invalidRequests =
            listOf(
                """{"lines":[null]}""",
                """{"lines":null}""",
                """{"lines":[{"quantity":1}]}""",
                """{"lines":[{"sku":"KB-001"}]}""",
                """{"lines":[{"sku":null,"quantity":1}]}""",
                """{"lines":[{"sku":"KB-001","quantity":null}]}""",
                """{"lines":[{"sku":"KB-001","quantity":"many"}]}""",
                """{"sku":"KB-001","quantity":1}""",
                "{",
            )

        invalidRequests.forEach { request ->
            place(request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    @Test
    fun `order rules rejected by the use case return a problem bad request`() {
        every { orderUseCase.place(emptyList()) } throws InvalidOrderException("an order must have between 1 and 10 lines")

        place(linesBody())
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid order"))
            .andExpect(jsonPath("$.detail").value("an order must have between 1 and 10 lines"))
    }

    @Test
    fun `skus not on sale return a documented conflict`() {
        val lines = listOf(OrderLine(Sku("KB-001"), 1), OrderLine(Sku("MS-001"), 1), OrderLine(Sku("KB-404"), 1))
        every { orderUseCase.place(lines) } throws OfferNotOnSaleException(setOf(Sku("MS-001"), Sku("KB-404")))

        place(linesBody("KB-001" to 1, "MS-001" to 1, "KB-404" to 1))
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("Offer not on sale"))
            .andExpect(jsonPath("$.detail").value("Not on sale: KB-404, MS-001"))
            .andDo(documented("orders-not-on-sale", responseFields(*problemFields())))
    }

    @Test
    fun `missing orders return a documented not found for get and cancel`() {
        every { orderUseCase.get(OrderId(missingId)) } throws OrderNotFoundException(OrderId(missingId))
        every { orderUseCase.cancel(OrderId(missingId)) } throws OrderNotFoundException(OrderId(missingId))

        expectNotFound(get("/api/orders/{id}", missingId))
            .andDo(documented("orders-not-found", orderIdParameter(), responseFields(*problemFields())))
        expectNotFound(patch("/api/orders/{id}/cancel", missingId))
    }

    @Test
    fun `malformed order id returns a problem bad request`() {
        listOf(get("/api/orders/{id}", "invalid-id"), patch("/api/orders/{id}/cancel", "invalid-id")).forEach { request ->
            mockMvc
                .perform(request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    private fun place(body: String): ResultActions =
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun linesBody(vararg lines: Pair<String, Int>): String =
        lines.joinToString(",", """{"lines":[""", "]}") { (sku, quantity) -> """{"sku":"$sku","quantity":$quantity}""" }

    private fun order(status: OrderStatus): Order = Order.restore(OrderId(orderId), listOf(OrderLine(Sku("KB-001"), 3)), status, placedAt)

    private fun orderJson(
        status: String,
        vararg lines: Pair<String, Int>,
    ): String {
        val linesJson = lines.joinToString(",") { (sku, quantity) -> """{"sku":"$sku","quantity":$quantity}""" }
        return """{"id":"$orderId","lines":[$linesJson],"status":"$status","placedAt":"$placedAt"}"""
    }

    private fun expectNotFound(request: RequestBuilder): ResultActions =
        mockMvc
            .perform(request)
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Order not found"))

    private fun orderIdParameter() = pathParameters(parameterWithName("id").description("주문의 UUID"))

    private fun orderFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("id").description("주문의 UUID"),
            fieldWithPath("lines").description("요청한 순서대로 저장된 주문 라인. 접수 후 바뀌지 않습니다."),
            fieldWithPath("lines[].sku").description("주문한 상품 SKU"),
            fieldWithPath("lines[].quantity").description("주문 수량"),
            fieldWithPath("status").description("PLACED(접수됨) 또는 CANCELLED(취소됨). 접수 직후에는 PLACED입니다."),
            fieldWithPath("placedAt").description("접수 시각, ISO 8601 UTC"),
        )
}
