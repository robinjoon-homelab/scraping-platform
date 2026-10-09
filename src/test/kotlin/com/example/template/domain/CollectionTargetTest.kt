package com.example.template.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CollectionTargetTest {
    @ParameterizedTest
    @ValueSource(strings = ["/", "/v1/prices", "/경제/지표", "/v1/.../prices"])
    fun `단일 슬래시로 시작하는 경로를 허용한다`(resource: String) {
        val target = CollectionTarget(resource)

        assertThat(target.resource).isEqualTo(resource)
        assertThat(target.parameters).isEmpty()
    }

    @ParameterizedTest
    @ValueSource(
        strings = ["", "prices", "//example.com", "/prices?symbol=A", "/prices#latest", "/a\\b", "/a b", "/a\tb", "/..", "/a/../b"],
    )
    fun `잘못된 경로와 쿼리 및 상위 경로 이동을 거부한다`(resource: String) {
        assertThatThrownBy { CollectionTarget(resource) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `경로는 2048자까지 허용하고 2049자부터 거부한다`() {
        val longestResource = "/" + "a".repeat(2047)

        assertThat(CollectionTarget(longestResource).resource).hasSize(2048)
        assertThatThrownBy { CollectionTarget(longestResource + "a") }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `파라미터는 50개까지 허용하고 51개부터 거부한다`() {
        val parameters = (1..50).associate { "key$it" to "value$it" }

        assertThat(CollectionTarget("/prices", parameters).parameters).hasSize(50)
        assertThatThrownBy { CollectionTarget("/prices", parameters + ("key51" to "value51")) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `파라미터 키는 1자부터 100자까지 허용한다`() {
        val parameters = mapOf("가" to "value", "나".repeat(100) to "value")

        assertThat(CollectionTarget("/prices", parameters).parameters).isEqualTo(parameters)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `공백만 있는 파라미터 키를 거부한다`(key: String) {
        assertThatThrownBy { CollectionTarget("/prices", mapOf(key to "value")) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `101자인 파라미터 키를 거부한다`() {
        assertThatThrownBy { CollectionTarget("/prices", mapOf("a".repeat(101) to "value")) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `파라미터 값은 빈 문자열과 2000자를 허용하고 2001자를 거부한다`() {
        val parameters = mapOf("empty" to "", "longest" to "가".repeat(2000))

        assertThat(CollectionTarget("/prices", parameters).parameters).isEqualTo(parameters)
        assertThatThrownBy { CollectionTarget("/prices", mapOf("value" to "가".repeat(2001))) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `원본 파라미터를 변경해도 생성된 대상과 해시값은 변하지 않는다`() {
        val parameters = mutableMapOf("symbol" to "005930", "market" to "KRX")
        val target = CollectionTarget("/prices", parameters)
        val originalHashCode = target.hashCode()
        parameters["symbol"] = "000660"
        parameters.remove("market")
        parameters["new"] = "added"

        assertThat(target.parameters).isEqualTo(mapOf("symbol" to "005930", "market" to "KRX"))
        assertThat(target.hashCode()).isEqualTo(originalHashCode)
    }

    @Test
    fun `경로와 파라미터 값이 같으면 입력 순서와 관계없이 같은 대상이다`() {
        val first = CollectionTarget("/prices", linkedMapOf("symbol" to "005930", "market" to "KRX"))
        val second = CollectionTarget("/prices", linkedMapOf("market" to "KRX", "symbol" to "005930"))

        assertThat(first).isEqualTo(second)
        assertThat(first.hashCode()).isEqualTo(second.hashCode())
        assertThat(setOf(first, second)).hasSize(1)
    }

    @Test
    fun `경로나 파라미터가 다르면 다른 대상이다`() {
        val target = CollectionTarget("/prices", mapOf("symbol" to "005930"))

        assertThat(target).isNotEqualTo(CollectionTarget("/news", target.parameters))
        assertThat(target).isNotEqualTo(CollectionTarget("/prices", mapOf("symbol" to "000660")))
        assertThat(target).isNotEqualTo(null)
        assertThat(target).isNotEqualTo("/prices")
    }
}
