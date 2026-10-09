# 저장소 개발 지침

이 저장소의 공통 지침은 [AGENTS.md](../AGENTS.md)이다. 코드 수정 전에 읽고 따른다.

함수·이름·금지 문법·예외의 기준은 [코드 품질 규칙](../docs/code-quality.md)이다. 단순한 숫자 준수를 위해 불필요한 추상화를 만들지 않는다.

- 테스트·빌드·detekt 검증은 JDK 25와 `./gradlew`로 실행한다. Python은 필요하지 않다.
- 의존 방향 `adapter → application → domain`을 유지한다. 도메인·포트는 Spring/Exposed에 의존하지 않는다. `application/service`의 Spring 의존성은 `org.springframework.transaction.annotation`의 `Transactional`과 선언 옵션 타입 `Propagation`·`Isolation`만 허용하며 조립은 `config`의 `@Bean`으로 한다.
- 유스케이스의 트랜잭션에는 Exposed Spring Boot 4 스타터의 매니저를 사용한다. 저장소에 별도 `transaction {}`를 추가하지 않으며 실제 커밋·롤백을 통합 테스트한다. DDL은 Flyway가 관리한다.
- API 변경 시 REST Docs 테스트를 함께 수정한다. DB 변경은 새 Flyway 마이그레이션으로 추가한다.
- `./gradlew build`로 ktlint, 타입 분석이 있는 detekt, 아키텍처 테스트, API 문서 생성을 확인한다. `detektMain`, `detektTest` 연결을 제거해 타입 분석 없는 기본 검사로 되돌리지 않는다. 자동 포맷 변경도 검토한다.
- baseline, 사유 없는 `@Suppress`, 경로 제외, 검사 비활성화로 실패를 숨기지 않는다. 금지한 억제 문자열은 detekt가 검사하고, 허용된 `@Suppress`의 위치와 바로 앞 `// quality-exception: 구체적인 사유` 주석은 리뷰한다.
- `quality-rules`는 빌드 전용 detekt 플러그인 모듈이며 애플리케이션 실행 JAR에는 포함하지 않는다.
- 사용자의 기존 변경을 보존한다. 요청 범위 밖의 원격 작업이나 배포를 실행하지 않는다.
- 결과와 검증 상태는 한국어로 간결하게 보고한다.
