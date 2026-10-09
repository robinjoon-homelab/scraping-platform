# 개발 가이드

## 실행 환경

JDK 25와 Gradle 9.3.0 Wrapper인 `./gradlew`를 사용한다. REST Docs는 4.0.1, ktlint Gradle 플러그인은 14.2.0, ktlint 엔진은 1.8.0, detekt는 2.0.0-alpha.6으로 고정한다. detekt는 정식 릴리스 전 버전이므로 업그레이드 시 사용자 정의 규칙과 타입 분석도 다시 검증한다. 기본 설정은 H2 파일 DB, 가상 스레드, Flyway, 정상 종료를 활성화한다. 테스트는 독립된 H2 인메모리 DB를 사용한다.

애플리케이션 실행·테스트·빌드·detekt에는 **JDK 25와 저장소의 Gradle Wrapper만 필요하다.** Python은 별도 배포 도구에서만 사용한다.

```sh
./gradlew bootRun
./gradlew test
./gradlew build
```

기본 H2 데이터는 `data/`에 저장된다. 로컬 DB를 초기화하려면 애플리케이션을 종료한 후 **보존할 데이터가 없는지 확인하고** 이 디렉터리를 삭제한다. 저장소에 DB 파일이나 비밀번호를 커밋하지 않는다.

기본 프로필에서는 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 연결을 재정의할 수 있다. 표준 Spring 환경변수 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`도 사용할 수 있다. 기본 드라이버는 H2이므로 다른 DB로 전환할 때는 `DB_DRIVER`도 바꾸거나 PostgreSQL용 `prod` 프로필을 사용한다. 배포 환경은 `prod` 프로필과 표준 Spring 환경변수를 사용한다.

## 수집 설정 API

`Source`는 출처와 접근 형식, `CollectionTask`는 대상·범위·고정 간격을 표현한다. 실제 수집과 워커는 아직 없다. 도메인·불변식과 후속 경계는 [도메인 모델](domain-model.md), 외부 서비스 사례는 [조사 문서](collection-research.md)에 있다.

- `POST /api/sources`, `GET /api/sources`, `GET /api/sources/{id}`: 출처 생성·조회.
- `POST /api/collection-tasks`, `GET /api/collection-tasks`, `GET /api/collection-tasks/{id}`: 계획 생성·조회.
- `PUT /api/collection-tasks/{id}`: `expectedVersion`과 `definition`으로 설정 교체.
- `PATCH /api/collection-tasks/{id}/enabled`: `expectedVersion`과 `enabled`로 활성 설정 변경. 워커가 없으므로 수집을 시작하지 않는다.

새 계획은 비활성 상태와 version 0으로 저장한다. 수정마다 version을 1 올리며 동시 수정 충돌은 `409`, 잘못된 설정은 `400`, 없는 항목은 `404`를 반환한다. 목록 offset은 0 이상, limit은 1~100이고 기본값은 50이다. 정확한 요청·응답은 `/docs/index.html`에서 확인한다.

`SCRAPING_API_TOKEN` 환경변수를 설정하고 `/api` 요청에 Bearer 토큰을 전달한다. 미설정 서버는 `503`, 누락·불일치 토큰은 `401`로 거부한다. 단일 운영자용이며 사용자별 데이터 분리는 없다. 테스트 프로필의 공개 테스트 토큰을 운영에서 사용하지 않는다.

## 기능 추가 순서

1. `domain`에 비즈니스 규칙을 작성한다. 이 계층에는 Spring, Exposed, HTTP 의존성을 넣지 않는다.
2. `application/port/input`에 유스케이스 계약, `application/port/output`에 필요한 외부 의존성 계약을 추가한다. 포트도 Spring·Exposed에 의존하지 않는다.
3. `application/service`에서 유스케이스를 구현한다. DB는 출력 포트로 호출하고 업무 단위 트랜잭션은 Spring `@Transactional`로 선언한다. Spring 의존성은 `org.springframework.transaction.annotation`의 `Transactional`, `Propagation`, `Isolation` 세 타입만 허용한다.
4. `adapter/inbound/<group>`에서 진입점·입력 검증을, `adapter/outbound/<group>`에서 외부 기능을 구현한다. 현재 HTTP는 `web`, Exposed는 `persistence` 그룹이다. 계층 앞 기능 경로와 계층 아래 역할·구현 경로는 [패키지 경계](code-quality.md#하위-패키지와-기능-경계)를 따른다.
5. `config`에서 구현체를 연결한다. 새 입력 유스케이스와 출력 어댑터의 계약을 테스트한다.

Exposed의 행, 테이블, 트랜잭션 타입을 도메인이나 포트로 반환하지 않는다. 도메인 모델로 변환한 뒤 어댑터 밖으로 전달한다. 여러 저장 작업이 하나의 원자적 연산이어야 한다면 유스케이스의 `@Transactional` 경계 안에 두고 실제 커밋·중간 실패 시 전체 롤백을 통합 테스트한다.

운영 코드의 주입은 생성자 `private val`로 한다. 함수 크기·인자·중첩·이름·금지 문법과 좁은 예외는 [코드 품질 규칙](code-quality.md)을 따른다. 제한을 맞추기 위해 의미 없는 함수나 인자 묶음 객체를 만들지 않는다.

가상 스레드는 JDBC의 블로킹 작업을 처리하는 실행 방식이다. DB 연결 풀을 무한히 늘리지 않으며, 트래픽에 맞는 연결 풀 크기와 쿼리 성능은 별도로 조정해야 한다.

## 트랜잭션

`exposed-spring-boot4-starter:1.5.0`이 Spring 7용 Exposed `SpringTransactionManager`를 `springTransactionManager` 빈으로 등록한다. 별도의 `Database`·JDBC 트랜잭션 매니저를 구성하지 않는다. 저장소는 `transaction {}` 없이 Exposed DSL을 사용해 유스케이스의 현재 트랜잭션에 참여한다. [Exposed Spring 통합](https://www.jetbrains.com/help/exposed/spring-boot-integration.html)

`SourceService`와 `CollectionTaskService`의 기본값은 `@Transactional(readOnly = true)`다. 출처·계획의 `create`, 계획의 `replace`·`setEnabled`에는 쓰기용 `@Transactional`을 선언한다. 계획 수정의 버전 확인, 출처 확인, 조건부 UPDATE, 재조회가 하나의 업무 트랜잭션에서 실행된다. 트랜잭션 종료 후 반환하므로 성공 응답은 커밋된 상태를 나타낸다.

- **관리되는 빈을 호출한다.** `config`의 `@Bean`으로 조립된 입력 포트를 주입받아 호출한다. 직접 생성한 서비스나 같은 객체의 내부 호출은 프록시를 통과하지 않아 새 경계를 만들지 않는다. 따라서 순수 서비스 단위 테스트는 로직 검사이고 트랜잭션 검증은 Spring 통합 테스트에서 한다. [Spring 프록시 동작](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html)
- **기본 전파·격리를 유지한다.** 전파 방식은 `REQUIRED`, 격리는 `DEFAULT`다. H2와 기본 PostgreSQL에서는 실제 JDBC 연결의 `READ_COMMITTED`를 사용한다. 기존 `Database.connect(DataSource)` 경로도 풀의 실제 격리를 사용했으므로 불필요한 강제 격리 설정을 추가하지 않는다. 운영 풀에서 격리를 바꾸면 경쟁 UPDATE의 실패 유형까지 다시 검증해야 한다.
- **읽기 전용은 접근 제어가 아니다.** `readOnly`는 DB·드라이버에 전달하는 힌트이며 인증을 대신하지 않는다. 웹·저장소 어댑터에 직접·합성·상속 `@Transactional`을 추가하지 않는다.
- **롤백 조건을 명시한다.** Spring의 기본 `RuntimeException`·`Error` 외에, Exposed 스타터의 `ExposedSpringTransactionAttributeSource`는 `SQLException`도 기본 롤백 대상으로 추가한다. 그 밖의 checked 예외까지 원자성을 요구한다면 유스케이스의 `rollbackFor`를 정하고 실패 테스트를 추가한다. Kotlin이 checked 예외 선언을 강제하지 않아도 이 구분은 유지된다.
- **같은 스레드에서 완료한다.** JDBC 트랜잭션 안에서 비동기 실행이나 코루틴 디스패처로 작업을 넘기지 않는다. 가상 스레드에서도 트랜잭션 상태는 실행 스레드에 연결된다.

실제 커밋·롤백은 테스트 자체의 자동 롤백 없이 검증한다. 입력 포트의 서비스 프록시를 호출한 뒤 별도 JDBC 연결로 결과를 확인한다. 복수 서비스 호출도 외부 `@Transactional` 경계에 참여하며 중간 실패 시 출처·계획을 함께 롤백해야 한다. 저장소 단독 통합 테스트에서만 테스트용 `TransactionTemplate`으로 경계를 만든다. 운영 유스케이스에는 트랜잭션 매니저·`TransactionTemplate`·공통 트랜잭션 데코레이터를 추가하지 않는다.

## 스키마 변경

`src/main/resources/db/migration`에 `V3__describe_change.sql`처럼 다음 버전의 Flyway SQL을 추가한다. V2는 출처·계획 테이블이다. V1 Todo 테이블과 마이그레이션은 호환성을 위해 보존했지만 예제 API는 제거했다. 이미 배포된 파일은 수정하지 않는다.

스키마는 Flyway만 관리한다. Exposed 스타터의 `spring.exposed.generate-ddl=false`를 유지하며 실행 시 테이블 자동 생성으로 누락된 마이그레이션을 숨기지 않는다.

기본 통합 테스트는 H2에서 실행한다. PostgreSQL 전용 SQL, 인덱스, 타입, 잠금 동작을 추가하면 PostgreSQL에서도 해당 변경을 검증한다. H2 테스트 통과만으로 PostgreSQL 호환성이 보장되지는 않는다.

## 실제 PostgreSQL 검증

`H2CollectionTransactionTest`와 `PostgresCollectionTransactionTest`는 같은 트랜잭션 계약을 실제 DB에서 검사한다. 단일 Exposed 매니저, 두 서비스의 프록시, readOnly, 실제 JDBC `READ_COMMITTED`, 커밋, 런타임 예외·`SQLException`의 출처·계획 전체 롤백, 같은 버전을 읽은 동시 쓰기의 성공 1건·충돌 1건을 검증한다. 테스트용 기록 저장소는 실제 Exposed 저장소로 위임하며 SQL 작업을 모의 구현으로 대체하지 않는다.

PostgreSQL 검증은 **삭제 가능한 전용 테스트 DB만 사용한다. 테스트마다 `sources`와 `collection_tasks`를 비운다.** 운영 DB나 보존할 데이터가 있는 DB를 지정하지 않는다. URL, 사용자명, 비밀번호를 환경변수로 설정한다. 로컬 임시 trust 인증 DB인 경우에도 비밀번호 환경변수는 빈 문자열로 전달한다.

```sh
POSTGRES_TEST_URL=jdbc:postgresql://127.0.0.1:5432/scraping_template_test \
POSTGRES_TEST_USER=scraping_test \
POSTGRES_TEST_PASSWORD="$LOCAL_POSTGRES_TEST_PASSWORD" \
./gradlew build --rerun-tasks
```

`POSTGRES_TEST_URL`이 없으면 기본 빌드에서는 PostgreSQL 테스트만 건너뛰며 Docker 없이 H2 검증을 수행한다. URL을 설정한 뒤에는 `--rerun-tasks`로 이전 테스트 캐시를 재사용하지 않고 실행한다. 테스트 XML에서 `PostgresCollectionTransactionTest`의 실행 건수와 skipped=0을 확인해야 PostgreSQL 검증 완료로 판단한다. 특정 테스트만 실행하면 REST Docs 스니펫도 그 범위만 생성되므로 최종 확인은 전체 `build`로 한다.

## REST Docs 갱신

`SourceApiDocumentationTest`와 `CollectionTaskApiDocumentationTest`는 실제 애플리케이션, Flyway, H2와 MockMvc를 사용한다. API 계약을 바꿀 때 요청·응답 검증과 REST Docs 필드 설명을 함께 갱신한다.

```sh
./gradlew :test --tests '*ApiDocumentationTest'
./gradlew build
java -jar build/libs/application.jar
```

`src/docs/asciidoc/index.adoc`가 스니펫을 조합한다. `build/generated-snippets`와 생성 HTML은 빌드 결과물이므로 직접 수정하지 않는다. `build` 결과 JAR 실행 후 `http://localhost:8080/docs/index.html`에서 확인한다.

새 엔드포인트는 성공 응답뿐 아니라 요구사항에 필요한 검증 오류와 없는 리소스 응답도 테스트한다. 문서에서 소개하는 필드가 실제 API 응답과 일치해야 한다.

## 코드 품질과 검증

```sh
./gradlew ktlintFormat
./gradlew ktlintCheck
./gradlew detektMain detektTest
./gradlew :quality-rules:test
./gradlew :test --tests '*CollectionTaskServiceTest'
./gradlew build
git diff
```

빌드 설정은 `org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask`에 자동 포맷 의존성을 지정한다. 컴파일·테스트·빌드 전에 수정 가능한 스타일 위반을 수정하고 남은 위반을 검사한다. 검증 실행도 작업 트리의 파일을 바꿀 수 있다.

`test`, `check`, `build`는 타입 분석을 사용하는 `detektMain`, `detektTest`를 포함하며 `./gradlew detekt`도 두 태스크를 실행한다. `:quality-rules:test`는 사용자 정의 규칙의 허용·위반 fixture를 검사하고 전체 `build`에도 포함된다. detekt 보고서는 `build/reports/detekt/`에서 확인한다. CI는 빌드 후 `git diff --exit-code`로 포맷 변경까지 검사하므로 로컬 자동 수정 결과를 커밋해야 한다.

금지한 억제 문자열은 detekt의 `ForbiddenSuppress`로 검사하며, 허용한 예외의 위치와 사유는 리뷰한다. `quality-rules`는 분석 전에 로드할 detekt 규칙 JAR를 별도로 컴파일하는 빌드 전용 모듈이다. 실행 JAR에는 포함하지 않으며 ktlint와 자체 fixture로 검증한다. [검사 범위](code-quality.md#검사-도구와-실행)를 참고한다.

도메인·유스케이스 단위 테스트는 규칙과 호출 계약을, API 통합 테스트는 HTTP·저장·문서를, `HexagonalArchitectureTest`는 계층 간 의존 경계를 검사한다. 변경 범위에 맞는 테스트를 먼저 실행하고 마지막에 전체 `build`를 실행한다.

검사 실패를 baseline, 사유 없는 `@Suppress`, 경로 제외, `ignoreFailures`로 숨기지 않는다. 예외는 개별 선언·규칙·이유를 기록하고 리뷰한다. 검사 설정이나 사용자 정의 규칙을 변경하면 정상·위반·경계값 fixture도 함께 검증한다. GitHub의 실제 병합 차단은 [ruleset 활성화 절차](code-quality.md#github에서-병합-차단-활성화)를 따른다.

## 프로젝트 이름 변경

`settings.gradle.kts`의 프로젝트 이름과 `com.example.template` 패키지를 서비스 이름으로 바꿀 수 있다. 소스·테스트의 패키지 선언, 경로, 아키텍처 검사 기준, 애플리케이션 설정의 이름을 함께 확인한다.

컨테이너 빌드가 사용하는 JAR 이름은 `application.jar`다. 이름을 바꾸려면 Gradle, Dockerfile, `.dockerignore`의 허용 경로, `.github/workflows/ci.yml`의 아티팩트 업로드 경로를 함께 수정한다. 배포 식별자는 CI의 명시적 `APP_NAME`을 사용하며 Kotlin 패키지 이름에 의존하지 않는다.

앱 이미지는 `JAVA_TOOL_OPTIONS`를 지정하지 않아 JVM 기본 힙·OOM 동작을 사용한다. Gradle의 `-Xmx2g`와 Wrapper 설정은 빌드용으로 유지하며, 앱 기본값 전환을 위해 하네스 리소스나 클러스터를 수정하지 않는다.

## 템플릿 적용 기준과 차이

2026-10-08 작업 시작 시 `git ls-remote --symref`로 원격 기본 브랜치 `master`와 HEAD `ddfbfef26e7af1ab3ee3fc4f00bc137fdb0a4a87`을 확인했다. 같은 SHA의 로컬 템플릿이 깨끗한 상태임을 확인한 뒤 해당 커밋의 AGENTS·README·개발/품질/배포 문서, 실제 소스·Gradle·테스트·CI를 기준으로 적용했다. [확인한 템플릿](https://github.com/robinjoon-homelab/kotlin-springboot-exposed-template/tree/ddfbfef26e7af1ab3ee3fc4f00bc137fdb0a4a87)

서비스의 읽기 전용·쓰기 트랜잭션 선언, Exposed Spring Boot 4 스타터, 단일 매니저, `@Bean` 조립, 어댑터 트랜잭션 선언 제한과 ArchUnit 회귀 fixture를 적용했다. 저장소는 별도 트랜잭션 없이 참여하며 설정은 `generate-ddl=false`다. Gradle·Wrapper·ktlint·detekt·quality-rules·Docker 계약은 이 기준과 일치한다. CI·배포 스크립트는 이후 제공된 2026-10-08 로컬 공통 배포 계약 v2.2로 갱신했으며 명시 이름·최초 생성 설정은 [배포 가이드](deployment.md)에 기록한다. 프로젝트 이름은 아래 이유로 유지한다.

**아키텍처·트랜잭션·품질 규칙의 프로젝트별 예외는 없다.** 다음 차이는 기존 사용자 요구와 실제 서비스 계약에 필요한 구현으로 판단했다.

2026-10-09에는 로컬 템플릿의 아키텍처 공통 계약 v5를 추가 적용했다. `ArchitecturePackages`가 계층 우선과 기능 우선 구조를 함께 해석하고, 역할의 연속 세그먼트 일치와 기능·어댑터 그룹 격리를 검사한다. 현재 루트 출력 포트·`outbound.persistence` 조합은 지원 대상이므로 운영 패키지를 옮기지 않는다. `domain.collection`도 도메인의 하위 패키지로 유지한다. 공통 파일과 layout fixture를 그대로 사용하고 기존 앱 검사·전체 운영 클래스 스캔을 보존한다. 경로 일반화를 위한 앱별 검사 면제는 추가하지 않는다.

| 유지하는 차이 | 근거 | 대체·추가 검증 |
| --- | --- | --- |
| Todo 대신 Source·CollectionTask·Run·Record와 수집 설정 API | 임의 분야의 출처·대상·범위를 설정하는 기존 제품 요구. 템플릿 예제로 대체하면 기능이 사라진다. | 기존 도메인·서비스·REST Docs 테스트, H2/실제 PostgreSQL 트랜잭션·동시 CAS 검사 |
| 운영자 토큰 인증과 `scraping-platform` 식별자 | 개인 데이터 설정을 보호하고 기존 실행·운영 계약을 유지해야 한다. | 실제 HTTP 인증·경로 정규화 회귀, 토큰 미설정·불일치 검사. 하네스의 Secret 주입 요구는 배포 문서에 유지 |
| 기존 V1·V2와 설정 테이블의 FK·version·시각 정밀도 | 기존 데이터·마이그레이션 체크섬과 동시 수정 계약을 보존해야 한다. | 마이그레이션 파일 무변경 비교, Flyway·실제 저장소·정밀도·충돌·롤백 통합 테스트 |

PostgreSQL 선택 실행은 기본 빌드를 Docker에 의존시키지 않으면서 트랜잭션 변경을 실제 운영 DB 엔진으로 확인하기 위한 추가 검증이다. 수집기나 주기 실행 워커 등의 새로운 제품 기능, 원격 하네스 변경, 커밋·푸시·배포는 이 리팩토링에 포함하지 않는다.
