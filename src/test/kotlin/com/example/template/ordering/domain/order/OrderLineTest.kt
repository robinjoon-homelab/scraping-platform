package com.example.template.ordering.domain.order

import com.example.template.ordering.domain.Sku
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class OrderLineTest {
    @ParameterizedTest
    @ValueSource(ints = [1, 100])
    fun `quantities at the limits are allowed`(quantity: Int) {
        assertThat(OrderLine(Sku("KB-001"), quantity).quantity).isEqualTo(quantity)
    }

    @ParameterizedTest
    @ValueSource(ints = [Int.MIN_VALUE, -1, 0, 101, Int.MAX_VALUE])
    fun `quantities outside one to one hundred are rejected`(quantity: Int) {
        assertThatThrownBy { OrderLine(Sku("KB-001"), quantity) }
            .isInstanceOf(InvalidOrderException::class.java)
            .hasMessage("quantity must be between 1 and 100")
    }
}
