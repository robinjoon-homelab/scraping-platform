package com.example.template.inventory.domain.stock

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StockItemTest {
    private val sku = Sku("KB-001")

    @Test
    fun `registration accepts zero but rejects negative onHand`() {
        assertThat(StockItem.register(sku, 0).onHand).isZero()
        assertThatThrownBy { StockItem.register(sku, -1) }.isInstanceOf(InvalidStockException::class.java)
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", " KB-001", "KB 001", "KB/001", "-KB", "키보드"])
    fun `skus outside the inventory format are rejected`(value: String) {
        assertThatThrownBy { Sku(value) }.isInstanceOf(InvalidStockException::class.java)
    }

    @Test
    fun `sku length is limited to 64 characters`() {
        val longest = "A".repeat(64)

        assertThat(Sku(longest).value).isEqualTo(longest)
        assertThat(Sku("kb_001.v2").value).isEqualTo("kb_001.v2")
        assertThatThrownBy { Sku("A".repeat(65)) }.isInstanceOf(InvalidStockException::class.java)
    }

    @Test
    fun `restock adds a positive quantity and leaves the original unchanged`() {
        val original = StockItem.register(sku, 5)

        val restocked = original.restock(3)

        assertThat(restocked).isEqualTo(StockItem.restore(sku, 8))
        assertThat(original.onHand).isEqualTo(5)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, Int.MIN_VALUE])
    fun `restock rejects a quantity that is not positive`(quantity: Int) {
        assertThatThrownBy { StockItem.register(sku, 5).restock(quantity) }.isInstanceOf(InvalidStockException::class.java)
    }

    @Test
    fun `restock accepts the largest onHand and rejects overflow`() {
        val item = StockItem.register(sku, Int.MAX_VALUE - 3)

        assertThat(item.restock(3).onHand).isEqualTo(Int.MAX_VALUE)
        assertThatThrownBy { item.restock(4) }.isInstanceOf(InvalidStockException::class.java)
        assertThatThrownBy { StockItem.register(sku, Int.MAX_VALUE).restock(1) }.isInstanceOf(InvalidStockException::class.java)
    }

    @Test
    fun `restoration rejects negative onHand`() {
        assertThat(StockItem.restore(sku, 0)).isEqualTo(StockItem.register(sku, 0))
        assertThatThrownBy { StockItem.restore(sku, -1) }.isInstanceOf(InvalidStockException::class.java)
    }
}
