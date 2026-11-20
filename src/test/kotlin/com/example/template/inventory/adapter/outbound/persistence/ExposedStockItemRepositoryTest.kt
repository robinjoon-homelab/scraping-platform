package com.example.template.inventory.adapter.outbound.persistence

import com.example.template.inventory.domain.stock.DuplicateSkuException
import com.example.template.inventory.domain.stock.Sku
import com.example.template.inventory.domain.stock.StockItem
import com.example.template.support.persistence.MigratedH2Database
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.sql.SQLException

class ExposedStockItemRepositoryTest {
    private val database = MigratedH2Database()
    private val repository = ExposedStockItemRepository()

    private val sku = Sku("KB-001")

    @AfterEach
    fun closeDatabase() = database.close()

    @Test
    fun `add returns the state that a later read restores`() {
        val added = inTransaction { repository.add(StockItem.register(sku, 5)) }

        assertThat(added).isEqualTo(StockItem.register(sku, 5))
        assertThat(inTransaction { repository.findBySku(sku) }).isEqualTo(added)
        assertThat(inTransaction { repository.findBySku(Sku("KB-404")) }).isNull()
    }

    // An upsert would succeed here.
    @Test
    fun `adding a registered sku fails as a duplicate instead of overwriting`() {
        inTransaction { repository.add(StockItem.register(sku, 5)) }

        assertThatThrownBy { inTransaction { repository.add(StockItem.register(sku, 9)) } }
            .isInstanceOf(DuplicateSkuException::class.java)
            .hasMessage("SKU already registered: KB-001")
            .hasCauseInstanceOf(SQLException::class.java)
    }

    @Test
    fun `update stores onHand of that item only`() {
        val other = inTransaction { repository.add(StockItem.register(Sku("MS-001"), 2)) }
        val stored = inTransaction { repository.add(StockItem.register(sku, 5)) }

        val changed = inTransaction { repository.update(stored.restock(4)) }

        assertThat(changed).isEqualTo(StockItem.restore(sku, 9))
        assertThat(inTransaction { repository.findBySku(sku) }).isEqualTo(changed)
        assertThat(inTransaction { repository.findBySku(other.sku) }).isEqualTo(other)
    }

    @Test
    fun `updating a missing item fails instead of inserting it`() {
        assertThatIllegalStateException().isThrownBy { inTransaction { repository.update(StockItem.register(sku, 5)) } }

        assertThat(inTransaction { repository.findBySku(sku) }).isNull()
    }

    @Test
    fun `findBySkuForUpdate returns the stored item or null`() {
        val stored = inTransaction { repository.add(StockItem.register(sku, 5)) }

        assertThat(inTransaction { repository.findBySkuForUpdate(sku) }).isEqualTo(stored)
        assertThat(inTransaction { repository.findBySkuForUpdate(Sku("KB-404")) }).isNull()
    }

    private fun <T> inTransaction(block: () -> T): T = database.inTransaction(block)
}
