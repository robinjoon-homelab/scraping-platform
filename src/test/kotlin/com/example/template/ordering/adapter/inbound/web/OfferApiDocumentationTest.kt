package com.example.template.ordering.adapter.inbound.web

import com.example.template.ordering.application.port.input.OfferNotFoundException
import com.example.template.ordering.application.port.input.OfferUseCase
import com.example.template.ordering.domain.Sku
import com.example.template.ordering.domain.offer.DuplicateOfferException
import com.example.template.ordering.domain.offer.Offer
import com.example.template.ordering.domain.offer.OfferId
import com.example.template.ordering.domain.offer.OfferStatus
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
import java.util.UUID

// The use case is a strict mock that answers only the inputs a test stubs. SKU limits, duplicate detection, and
// repeated discontinuation belong to SkuTest, OfferTest, OfferServiceTest, and the repository test; this test checks
// the HTTP responses the endpoint returns for requests, results, and failures.
class OfferApiDocumentationTest : RestDocsWebTest() {
    private val offerId: UUID = UUID.fromString("7b3e9d1a-2c4f-4e6a-8b0d-3c5e7f9a1b2d")
    private val missingId: UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")
    private val offerUseCase = mockk<OfferUseCase>()
    private val mockMvc = restDocsMockMvc(OfferController(offerUseCase), OfferExceptionHandler())

    @Test
    fun `register with a padded sku returns the offer on sale with its location`() {
        every { offerUseCase.register(Sku("BOOK-001")) } returns Offer.register(OfferId(offerId), Sku("BOOK-001"))

        register("""{"sku":"  BOOK-001  "}""")
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", "/api/offers/$offerId"))
            .andExpect(content().json(offerJson("ON_SALE"), JsonCompareMode.STRICT))
            .andDo(
                documented(
                    "offers-register",
                    requestFields(
                        fieldWithPath("sku").description(
                            "판매할 상품 SKU. 앞뒤 공백을 제거한 뒤 1~64자여야 하며 이미 등록된 SKU는 다시 등록할 수 없습니다. 재고는 확인하지 않습니다.",
                        ),
                    ),
                    responseHeaders(headerWithName("Location").description("등록된 Offer의 조회 경로")),
                    responseFields(*offerFields()),
                ),
            )
    }

    @Test
    fun `get returns the offer of the path id`() {
        every { offerUseCase.get(OfferId(offerId)) } returns offer(OfferStatus.ON_SALE)

        mockMvc
            .perform(get("/api/offers/{id}", offerId))
            .andExpect(status().isOk)
            .andExpect(content().json(offerJson("ON_SALE"), JsonCompareMode.STRICT))
            .andDo(documented("offers-get", offerIdParameter(), responseFields(*offerFields())))
    }

    @Test
    fun `discontinue returns the discontinued offer`() {
        every { offerUseCase.discontinue(OfferId(offerId)) } returns offer(OfferStatus.DISCONTINUED)

        mockMvc
            .perform(patch("/api/offers/{id}/discontinue", offerId))
            .andExpect(status().isOk)
            .andExpect(content().json(offerJson("DISCONTINUED"), JsonCompareMode.STRICT))
            .andDo(documented("offers-discontinue", offerIdParameter(), responseFields(*offerFields())))
    }

    @Test
    fun `duplicate sku returns a documented conflict`() {
        every { offerUseCase.register(Sku("BOOK-001")) } throws
            DuplicateOfferException(Sku("BOOK-001"), IllegalStateException("unique key violated"))

        register("""{"sku":" BOOK-001 "}""")
            .andExpect(status().isConflict)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(409))
            .andExpect(jsonPath("$.title").value("Offer already registered"))
            .andExpect(jsonPath("$.detail").value("Offer already registered for SKU: BOOK-001"))
            .andDo(documented("offers-duplicate", responseFields(*problemFields())))
    }

    @Test
    fun `missing offers return a documented not found for get and discontinue`() {
        every { offerUseCase.get(OfferId(missingId)) } throws OfferNotFoundException(OfferId(missingId))
        every { offerUseCase.discontinue(OfferId(missingId)) } throws OfferNotFoundException(OfferId(missingId))

        expectNotFound(get("/api/offers/{id}", missingId))
            .andDo(documented("offers-not-found", offerIdParameter(), responseFields(*problemFields())))
        expectNotFound(patch("/api/offers/{id}/discontinue", missingId))
    }

    // The SKU is built before the use case runs, so a blank SKU fails here.
    @Test
    fun `invalid registrations return a documented bad request`() {
        register("""{"sku":"   "}""")
            .andExpect(status().isBadRequest)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.title").value("Invalid offer"))
            .andExpect(jsonPath("$.instance").value("/api/offers"))
            .andDo(documented("offers-invalid", responseFields(*problemFields())))

        listOf("""{"sku":null}""", "{}", "{").forEach { request ->
            register(request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    @Test
    fun `malformed offer id returns a problem bad request`() {
        listOf(get("/api/offers/{id}", "BOOK-001"), patch("/api/offers/{id}/discontinue", "BOOK-001")).forEach { request ->
            mockMvc
                .perform(request)
                .andExpect(status().isBadRequest)
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        }
    }

    private fun register(body: String): ResultActions =
        mockMvc.perform(post("/api/offers").contentType(MediaType.APPLICATION_JSON).content(body))

    private fun offer(status: OfferStatus): Offer = Offer.restore(OfferId(offerId), Sku("BOOK-001"), status)

    private fun offerJson(status: String): String = """{"id":"$offerId","sku":"BOOK-001","status":"$status"}"""

    private fun expectNotFound(request: RequestBuilder): ResultActions =
        mockMvc
            .perform(request)
            .andExpect(status().isNotFound)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.title").value("Offer not found"))

    private fun offerIdParameter() = pathParameters(parameterWithName("id").description("Offer의 UUID"))

    private fun offerFields(): Array<FieldDescriptor> =
        arrayOf(
            fieldWithPath("id").description("Offer의 UUID"),
            fieldWithPath("sku").description("판매 대상 SKU. 등록 후 바뀌지 않습니다."),
            fieldWithPath("status").description("ON_SALE(판매 중) 또는 DISCONTINUED(판매 종료). 판매 종료는 되돌릴 수 없습니다."),
        )
}
