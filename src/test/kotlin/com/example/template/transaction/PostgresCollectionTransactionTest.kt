package com.example.template.transaction

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@EnabledIfEnvironmentVariable(named = "POSTGRES_TEST_URL", matches = "jdbc:postgresql:.*")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.datasource.driver-class-name=org.postgresql.Driver"],
)
class PostgresCollectionTransactionTest : CollectionTransactionContractTest() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun postgresProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { requireNotNull(System.getenv("POSTGRES_TEST_URL")) }
            registry.add("spring.datasource.username") { requireNotNull(System.getenv("POSTGRES_TEST_USER")) }
            registry.add("spring.datasource.password") { requireNotNull(System.getenv("POSTGRES_TEST_PASSWORD")) }
        }
    }
}
