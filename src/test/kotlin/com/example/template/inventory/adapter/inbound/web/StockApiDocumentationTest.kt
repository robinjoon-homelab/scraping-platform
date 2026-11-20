package com.example.template.inventory.adapter.inbound.web

import com.example.template.inventory.application.port.input.StockItemNotFoundException
import com.example.template.inventory.application.port.input.StockUseCase
import com.example.template.inventory.domain.stock.DuplicateSkuException
import com.example.template.inventory.domain.stock.InvalidStockException
import com.example.template.inventory.domain.stock.Sku
import com.example.template.inventory.domain.stock.StockItem
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
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.request.RequestDocumentation.parameterWithName
import org.springframework.restdocs.request.RequestDocumentation.pathParameters
import org.springframework.test.json.JsonCompareMode
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

// The use case is a strict mock that answers only the inputs a test stubs. An any() matcher cannot stand for the
// inventory Sku, because MockK builds its placeholder through the Sku constructor, which rejects a random value.
// SKU format, onHand limits, overflow, accumulation, and duplicate detection belong to StockItemTest,
// StockServiceTest, and the repository test; this test checks the HTTP responses the endpoint returns for requests,
// results, and failures.
class StockApiDocumentationTest : RestDocsWebTest() {
    private val stockUseCase = mockk<StockUseCase>()
    private val mockMvc = restDocsMockMvc(StockController(stockUseCase), StockExceptionHandler())

    @Test
    fun `register returns the item of the requested sku and onHand with its location`() {
        every { stockUseCase.register(Sku("KB-001"), 5) } returns StockItem.register(Sku("KB-001"), 5)

        register("""{"sku":"KB-001","onHand":5}""")
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/inventory/items/KB-001"))
            .andExpect(content().json("""{"sku":"KB-001","onHand":5}""", JsonCompareMode.STRICT))
            .andDo(
                documented(
                    "inventory-items-register",
                    requestFields(
                        fieldWithPath("sku").description(
                            "1~64자 SKU. 영문자·숫자로 시작하고 영문자·숫자·`.`·`_`·`-`만 사용합니다. 공백을 제거하지 않습니다.",
                        ),
                        fieldWithPath("onHand").description("0 이상의 보유 수량"),
                    ),
                    responseHeaders(headerWithName("Location").description("등록된 재고 품목의 조회 경로")),
                    responseFields(*stockItemFields()),
                ),
            )
    }

    @Test
    fun `get returns the item of the path sku`() {
        every { stockUseCase.get(Sku("KB-001")) } returns StockItem.restore(Sku("KB-001"), 5)

        mockMvc
            .perform(get("/api/inventory/items/{sku}", "KB-001"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"sku":"KB-001","onHand":5}""", JsonCompareMode.STRICT))
            .andDo(documented("inventory-items-get", skuParameter(), responseFields(*stockItemFields())))
    }

    @Test
    fun `restock of the path sku by the requested quantity returns the restocked item`() {
        every { stockUseCase.restock(Sku("KB-001"), 3) } returns StockItem.restore(Sku("KB-001"), 8)

        restock("KB-001", """{"quantity":3}""")
            .andExpect(status().isOk)
            .andExpect(content().json("""{"sku":"KB-001","onHand":8}""", JsonCompareMode.STRICT))
            .andDo(
                documented(
                    "inventory-items-restock",
                    skuParameter(),
                    requestFields(fieldWithPath("quantity").description("추가할 수량. 1 이상이며 합계가 2147483647을 넘을 수 없습니다.")),
                    responseFields(*stockItemFields()),
                ),
            )
    }

    // The SKU is built before the use case runs, so a SKU outside the format fails here.
    @Test
    fun `invalid sku returns a documented bad request`() {
        register("""{"sku":"KB 001","onHand":5}""")
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.title").value("Invalid stock request"))
            .andExpect(jsonPath("$.instance").value("/api/inventory/items"))
            .andDo(documented("inventory-items-invalid", responseFields(*problemFields())))
    }

    @Test
    fun `registrations that do not bind return a problem bad request`() {
        val invalidRequests =
            listOf(
                """{"onHand":5}""",
                """{"sku":"KB-001"}""",
                """{"sku":null,"onHand":5}""",
                """{"sku":"KB-001","onHand":null}""",
                "{",
            )

        invalidRequests.forEach { request ->
            register(request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    @Test
    fun `stock rules rejected by the use case return a problem bad request`() {
        every { stockUseCase.register(Sku("KB-001"), -1) } throws InvalidStockException("onHand must not be negative")
        every { stockUseCase.restock(Sku("KB-001"), 0) } throws InvalidStockException("restock quantity must be positive")

        register("""{"sku":"KB-001","onHand":-1}""")
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid stock request"))
            .andExpect(jsonPath("$.detail").value("onHand must not be negative"))
        restock("KB-001", """{"quantity":0}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid stock request"))
            .andExpect(jsonPath("$.detail").value("restock quantity must be positive"))
    }

    @Test
    fun `restocks that do not bind return a problem bad request`() {
        listOf("{}", """{"quantity":null}""", "{").forEach { request ->
            restock("KB-001", request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    @Test
    fun `missing item returns a documented not found`() {
        every { stockUseCase.get(Sku("KB-404")) } throws StockItemNotFoundException(Sku("KB-404"))

        mockMvc
            .perform(get("/api/inventory/items/{sku}", "KB-404"))
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Stock item not found"))
            .andExpect(jsonPath("$.instance").value("/api/inventory/items/KB-404"))
            .andDo(documented("inventory-items-not-found", skuParameter(), responseFields(*problemFields())))
    }

    @Test
    fun `restock of a missing item returns not found`() {
        every { stockUseCase.restock(Sku("KB-404"), 1) } throws StockItemNotFoundException(Sku("KB-404"))

        restock("KB-404", """{"quantity":1}""")
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Stock item not found"))
    }

    @Test
    fun `invalid sku in the path returns a problem bad request`() {
        mockMvc
            .perform(get("/api/inventory/items/{sku}", "KB 001"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid stock request"))
        restock("-KB", """{"quantity":1}""")
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.title").value("Invalid stock request"))
    }

    @Test
    fun `duplicate sku returns a documented conflict`() {
        every { stockUseCase.register(Sku("KB-001"), 9) } throws
            DuplicateSkuException(Sku("KB-001"), IllegalStateException("unique key violated"))

        register("""{"sku":"KB-001","onHand":9}""")
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("SKU already registered"))
            .andExpect(jsonPath("$.detail").value("SKU already registered: KB-001"))
            .andDo(documented("inventory-items-duplicate", responseFields(*problemFields())))
    }

    private fun register(body: String): ResultActions =
        mockMvc.perform(post("/api/inventory/items").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun restock(
        sku: String,
        body: String,
    ): ResultActions =
        mockMvc.perform(post("/api/inventory/items/{sku}/restock", sku).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun skuParameter() = pathParameters(parameterWithName("sku").description("재고 품목의 SKU"))

    private fun stockItemFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("sku").description("재고 품목의 SKU"),
            fieldWithPath("onHand").description("보유 수량"),
        )
}
