package com.example.template.transaction

import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = ["spring.datasource.url=jdbc:h2:mem:collection-transaction-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"],
)
class H2CollectionTransactionTest : CollectionTransactionContractTest()
