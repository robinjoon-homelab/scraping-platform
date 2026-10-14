# 품질 검사 유지보수

**detekt 설정·Gradle 검사 연결·사용자 정의 규칙·ArchUnit 검사기·fixture를 바꿀 때만** 읽는다. 수치와 허용 예외는 [코드 품질 규칙](code-quality.md), 앱 경계는 [아키텍처와 도메인 경계](ddd-architecture.md), 명령은 [실행과 검증](development.md#실행과-검증)을 따른다.

## 변경 원칙

- 규칙을 바꾸면 정상·위반·경계값·DSL 내부·예외 범위를 fixture로 검증한다. 아키텍처 검사를 바꾸면 금지한 의존이 실제로 실패하는지 확인한다.
- DDD·BC 검사를 맞추려고 공통 규칙이나 fixture를 약화하지 않는다. 범용 검사기의 허용 범위로 현재 앱의 허용 범위를 넓히지 않는다.
- 수치·정확한 예외는 [코드 품질 규칙](code-quality.md)에만 정의하고, 계약을 바꾸면 해당 원본 문서를 함께 갱신한다.

## detekt와 빌드 연결

- detekt는 **`2.0.0-alpha.6`으로 고정**한다. 루트 `build.gradle.kts`의 플러그인·`toolVersion`과 `quality-rules/build.gradle.kts`의 `detektVersion`을 함께 바꾸고, 업그레이드하면 JDK 25·Kotlin 호환성과 사용자 정의 규칙 회귀를 확인한다.
- `config.validation`, `warningsAsErrors`, `ignoreFailures=false`, `failOnSeverity=Error`를 유지하고 관례 기반 baseline을 쓰지 않는다. `LargeClass`의 warning은 리뷰 신호로 둔다.
- `NestedBlockDepth`·`TooManyFunctions`는 끈 상태로 둔다.
- 강제 규칙·허용 예외를 바꾸면 [`ForbiddenSuppress`](../config/detekt/detekt.yml) 목록과 [억제 정책](code-quality.md#예외와-억제)을 함께 확인한다. 별도 소스 검사기를 추가하지 않는다.
- `test`·`check`는 `detektMain`·`detektTest`와 규칙 fixture 테스트를 실행하고, 공개 `detekt`도 두 타입 분석을 포함한다. 타입 분석 없는 기본 태스크로 되돌리지 않는다.
- 컴파일을 `detektMain`에 의존시키지 않는다.
- `detektTest`는 공통·테스트 설정을 함께 읽고 비활성 plain JAR가 아닌 실제 main/test 클래스 출력 경로를 쓴다. `detekt.use.worker.api=true`를 유지한다.
- `KtLintCheckTask`는 `ktlintFormat` 뒤, 컴파일·테스트 전에 전체 소스에 실행하고 CI의 `git diff --exit-code`를 유지한다.
- `architecture.productionClasses`로 전체 운영 클래스 출력 경로를 전달한다. 속성 누락·빈 스캔·테스트 fixture 혼입은 실패해야 한다.

## quality-rules 모듈

detekt는 분석 전에 컴파일된 규칙 JAR를 플러그인으로 읽으므로 사용자 정의 규칙을 앱 소스와 분리한 `quality-rules` 모듈에서 먼저 빌드한다. 빌드 전용 모듈이며 앱 계층이 아니고 실행 JAR에 포함되지 않는다.

- 앱 detekt는 `src/main`·`src/test`를 검사한다. `quality-rules`에는 자기 detekt를 적용하지 않고 ktlint·fixture 테스트를 적용하며, 전체 `build`가 이 테스트를 포함한다.
- `CleanCodeRuleSetProvider`가 `clean-code` 규칙 `LogicalNestingDepth`·`NoMutableCollectionExposure`·`MaximumFunctionLength`를 등록한다. 동작은 [코드 품질 규칙](code-quality.md)의 조건과 맞추고 조건마다 회귀 fixture를 유지한다.

## 아키텍처 검사기

- 공통 검사기에 앱 이름을 넣지 않는다. 앱별 값은 `ArchitectureRules`의 루트 패키지·시작 클래스명(기본값 `TemplateApplication`)·추가 운영 타입 인자로 전달한다.
- **새 규칙은 해당 `all()`에도 포함한다.**

| 구성 | 책임 |
| --- | --- |
| `ArchitecturePackages` | 기능 경로·계층·역할 해석 |
| `ArchitectureRules` | 공통 헥사고날 검사 |
| `DddArchitectureRules` | 도메인 비공개·어그리게이트 검사 |
| `BoundedContextArchitectureRules` | BC 코어 격리·공용 코드 독립 검사 |

### 패키지 해석과 범용 레이아웃

- 패키지를 `<root>.[feature...].<layer>.[role/implementation...]`로 해석하고 첫 `domain`·`application`·`adapter`·`config`를 계층 시작으로 취급한다. 이 이름은 기능 이름으로 쓰지 않는다.
- 공통 검사에서 계층 우선·다단계 기능 우선 구조를 검증한다. **현재 앱은 최상위 BC만 허용**하며 [패키지 배치](ddd-architecture.md#패키지-배치)를 따른다.
- 포트 구현은 같은 기능이나 루트 공용 포트에 대해, 계층 뒤 경로가 포트의 역할 경로를 순서를 유지한 연속된 완전한 패키지 세그먼트로 포함하는지 검증한다. 구현 세그먼트는 앞뒤에 둘 수 있다.
- 기능은 전체 경로로 비교한다. 하위 역할 없는 포트·루트 공용 포트의 허용은 [역할과 위치](ddd-architecture.md#역할과-위치)를 따른다.
- 입력 어댑터는 자기 기능·루트 공용 입력 포트와 역할을 맞춘다. 공통 검사에서는 애플리케이션 서비스의 다른 기능 입력 계약 호출을 허용하되, 현재 앱에는 BC 검사도 함께 적용한다.
- 어댑터는 기능 경로·방향·첫 그룹 단위로 격리한다. 하위 이름으로 공급자 경계를 추측하지 않는다.
- 공통 검사는 다른 기능의 도메인 값·입력 계약·모델을 허용하고 서비스 구현·출력 포트·어댑터·설정을 금지한다. 공용 포트·`config`는 소유권 예외지만 계층·순수성 검사는 받는다. **이 허용을 현재 앱에 단독 적용하지 않는다.**
- Kotlin companion·기본 구현 보조 클래스를 일반 구현과 구분한다. 포트의 데이터 계약·인터페이스 판정은 [역할과 위치](ddd-architecture.md#역할과-위치)와 맞춘다.
- JDBC·Exposed는 출력 어댑터 경로의 `persistence` 세그먼트와 `config`에만 허용한다. 순환은 루트 아래 모든 패키지 단위로 검사한다.

### 명시적 추가 운영 타입

- 계층 밖에 보존할 기존 Java 마이그레이션은 `additionalProductionTypes`에 정확한 FQCN으로 등록하고 앱 문서·테스트에 근거를 남긴다.
- **등록되어 있고 Flyway `JavaMigration`을 구현한 타입만** `java.sql`·`javax.sql`을 쓸 수 있다. 미등록 마이그레이션·일반 등록 타입의 JDBC와 마이그레이션의 Exposed·Spring JDBC는 실패하며, 다른 경계 검사도 그대로 적용한다.
- `ArchitectureLayoutsTest`로 두 레이아웃과 이 예외 범위를 검증한다.

### 트랜잭션 검사 확장

- 애너테이션 검사 대상은 Spring `org.springframework.transaction.annotation.Transactional`의 직접·합성·상속 선언이다.
- JTA 도입 전에 `jakarta.transaction.Transactional`·`javax.transaction.Transactional` 탐지와 같은 회귀 사례를 먼저 추가한다.
- 어댑터 금지 의존: `TransactionManager`·`TransactionOperations`·`TransactionAspectSupport`, Exposed `transactions` 패키지.
- 금지 수동 제어: JDBC `Connection`·Exposed `ExposedConnection`의 `commit`·`rollback`·`setAutoCommit`·`setSavepoint`·`releaseSavepoint`·`setTransactionIsolation`·`setReadOnly`, Exposed `Transaction`의 `commit`·`rollback`.
- 직접 호출·메서드 참조·연결이나 커넥터 경유 호출을 모두 검사하고, 일반 JDBC·Exposed 조회는 정상 fixture로 유지한다.

### DDD 검사

- 공통 검사가 허용한 다른 기능 도메인 참조를 추가로 제한한다. 대상은 `<root>.domain.AggregateRoot`를 직접 단 클래스이며 애너테이션 타입은 제외한다.
- 비공개 생성자·copy 검사는 Kotlin 합성 생성자·`DefaultConstructorMarker`·`copy$default`를 구분하고 value class로 맹글링된 `copy-<hash>`도 검사한다.
- 기능·계층 우선 구조, 여러 루트, ID 참조, 상속·제네릭 포트, 합성 멤버, 공개 copy의 정상·위반 fixture를 유지한다.
- 표식 누락, 반사 접근, 깊은 불변성, 래퍼·타입 소거를 거친 간접 참조, 실제 쓰기 대상은 코드 검토와 행동 테스트로 확인한다.

### BC 검사

- 하위 기능은 등록 경로 중 가장 구체적인 BC에 귀속시킨다. 현재 앱은 **모든 업무 기능·루트를 명시 목록과 대조**한다.
- BC 코어는 자기 BC·공용 타입만 사용한다. 같은 BC 내부와 어댑터·`config`·미등록 기능에도 공통·DDD 검사를 적용한다. 공용 애플리케이션·어댑터는 업무 기능 타입에 의존하지 않는다.
- 함수 값·`invokedynamic` 람다·반사·인라인·value class 소거를 거친 의존, `config` 조립 내용, 업무 의미, 다른 BC 모델을 참조하는 어댑터는 [BC 경계](ddd-architecture.md#bc-경계)로 리뷰한다.
- 새 전달 방식은 [연동 계약](ddd-architecture.md#bc-간-연동-요구가-생기면)과 검사를 함께 설계한다.

### fixture 규약

- ArchUnit은 컴파일된 바이트코드를 검사하고 Kotlin은 기본 인자·`copy`·companion 같은 합성 멤버와 의존을 추가하므로, 기대 의존만 남기도록 fixture는 Java로 `src/test/java/architecturefixtures/`에 두는 것이 기본이다. Kotlin 생성 멤버를 검증하거나 그 멤버가 필요한 fixture는 `src/test/kotlin/architecturefixtures/`에 둔다.
- 운영 스캔에 fixture가 섞이지 않게 한다.
- 정상 fixture는 해당 `all()`을 통과해야 한다. 위반 fixture는 정확한 클래스를 찾고, 기대 규칙이 `all()`에 포함되어 실제로 실패하는지 확인한다.
- 한 BC의 여러 루트와 독립 BC에 공통·DDD·BC 검사를 함께 적용한다. ID 참조 허용 사례는 value class 소거로 빈 검사가 되지 않게 실제 바이트코드 의존을 단언한다.
- 루트 밖 클래스 위치, 고립된 순환, 직접·합성·상속 트랜잭션, 데이터 계약, Kotlin 생성 멤버를 회귀 사례로 유지한다.
