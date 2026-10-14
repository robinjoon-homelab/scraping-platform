package com.example.template.collection.domain.source

import com.example.template.collection.domain.InvalidCollectionConfigurationException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SourceDefinitionTest {
    @Test
    fun `이름은 앞뒤 공백을 제외한 1자와 120자를 허용한다`() {
        val shortest = SourceDefinition(" 가 ", SourceKind.HTTP_JSON, "https://example.com")
        val longest = SourceDefinition(" ${"가".repeat(120)} ", SourceKind.HTML, "https://example.com")

        assertThat(shortest.name.trim()).hasSize(1)
        assertThat(longest.name.trim()).hasSize(120)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\t\n"])
    fun `공백만 있는 이름을 거부한다`(name: String) {
        assertThatThrownBy { SourceDefinition(name, SourceKind.HTTP_JSON, "https://example.com") }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `앞뒤 공백을 제외해도 121자인 이름을 거부한다`() {
        assertThatThrownBy { SourceDefinition(" ${"가".repeat(121)} ", SourceKind.HTTP_JSON, "https://example.com") }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://example.com", "https://example.com", "HTTPS://example.com"])
    fun `HTTP와 HTTPS 주소를 허용한다`(baseUrl: String) {
        val definition = SourceDefinition("공개 데이터", SourceKind.RSS_ATOM, baseUrl)

        assertThat(definition.baseUrl).isEqualTo(baseUrl)
        assertThat(definition.credentialRef).isNull()
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "https://", "ftp://example.com", "/relative", "https://example.com/a b"])
    fun `HTTP 주소가 아니거나 공백이 포함된 주소를 거부한다`(baseUrl: String) {
        assertThatThrownBy { SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, baseUrl) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `주소는 2048자까지 허용하고 2049자부터 거부한다`() {
        val prefix = "https://example.com/"
        val longestUrl = prefix + "a".repeat(2048 - prefix.length)

        assertThat(SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, longestUrl).baseUrl).hasSize(2048)
        assertThatThrownBy { SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, longestUrl + "a") }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["a", "API_2026.finance-key"])
    fun `인증 참조에 영문과 숫자 및 구분자를 허용한다`(credentialRef: String) {
        val definition = SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, "https://example.com", credentialRef)

        assertThat(definition.credentialRef).isEqualTo(credentialRef)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "한글", "secret/key", "token:value", "api key"])
    fun `허용하지 않는 인증 참조 문자를 거부한다`(credentialRef: String) {
        assertThatThrownBy { SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, "https://example.com", credentialRef) }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }

    @Test
    fun `인증 참조는 120자까지 허용하고 121자부터 거부한다`() {
        val credentialRef = "a".repeat(120)

        assertThat(SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, "https://example.com", credentialRef).credentialRef)
            .hasSize(120)
        assertThatThrownBy { SourceDefinition("공개 데이터", SourceKind.HTTP_JSON, "https://example.com", credentialRef + "a") }
            .isInstanceOf(InvalidCollectionConfigurationException::class.java)
    }
}
