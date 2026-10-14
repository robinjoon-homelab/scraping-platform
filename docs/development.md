# 개발 작업 지침

## 실행 환경

- `JAVA_HOME`을 JDK 25로 설정하고 저장소의 `./gradlew`를 사용한다. 전역 Gradle을 사용하지 않는다.
- 의존성 변경 전 `build.gradle.kts`와 `gradle/wrapper/gradle-wrapper.properties`의 버전을 확인한다.
- 로컬 API는 `http://localhost:8080`으로 호출하고 `Authorization: Bearer <SCRAPING_API_TOKEN>`을 보낸다. 토큰 미설정은 503, 누락·불일치는 401이며 문서·health는 공개다. H2 개발 DB를 초기화할 때는 앱을 종료하고 필요한 데이터를 백업한 뒤 `data/`를 삭제한다.
- 기본 프로필의 DB 연결은 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD` 또는 표준 `SPRING_DATASOURCE_URL`·`SPRING_DATASOURCE_USERNAME`·`SPRING_DATASOURCE_PASSWORD`로 지정한다. H2 외의 DB에는 `DB_DRIVER`도 설정한다.
- 운영 PostgreSQL에는 [배포 계약](deployment.md#컨테이너와-db-계약)의 `prod` 프로필을 사용한다. DB 파일·자격 증명을 커밋하지 않는다.

## 기능 변경

1. 명령·조회·성공·실패·동시 실행 조건을 확인하고 [경계 선정 절차](ddd-architecture.md#새-기능을-분류하는-절차)로 소유 BC·루트를 정한다.
2. 도메인 규칙과 [기본 범위](#테스트-범위)의 테스트를 작성한다. 새 BC·루트는 `HexagonalArchitectureTest.contextAggregates`와 아키텍처 문서의 목록에도 등록한다.
3. 필요한 입력·출력 포트와 유스케이스를 변경하고 [트랜잭션 계약](ddd-architecture.md#트랜잭션)을 적용한다.
4. 어댑터와 `config`의 빈 조립을 변경한다. HTTP·DB를 바꾸면 아래 해당 절차도 수행한다.
5. [변경 설명](ddd-architecture.md#변경-설명에-남길-최소-기록)을 남기고 [변경별 검증과 전체 빌드](#실행과-검증)를 실행한다.

## 테스트 범위

테스트 대상과 수준은 이 절을 원본으로 한다.

- 테스트 대상 함수가 본문에서 서로 다른 함수를 2개 이상 호출할 때만 그 함수의 테스트를 작성한다. 다른 함수 하나에 처리를 넘기는 단순 위임은 작성 대상에서 제외한다.
- 호출 수는 테스트 코드가 아니라 대상 함수 본문에서 센다. 같은 함수의 반복 호출은 하나로 보고, 테스트 준비·단언에서 부르는 함수는 세지 않는다.
- 생성자·`init` 블록에도 같은 기준을 적용한다. 내장·표준·라이브러리 함수와 생성자 호출도 호출로 센다.
- 트랜잭션 동작은 프레임워크를 신뢰하고 테스트 작성 대상에서 제외한다.

작성 대상은 다음 방식으로 검증한다.

- 사람이 특정 대상의 내부 호출 검증을 명확히 지시한 경우에만 그 검증을 허용한다. 그 외의 테스트는 반환값·예외·공개 상태 변화·저장 결과·HTTP 요청/응답 계약처럼 외부에서 관찰 가능한 결과를 검증한다.
- 내부의 특정 함수·의존성을 호출했는지, 호출하지 않았는지, 몇 번·어떤 순서·인자로 호출했는지는 검증하지 않는다. MockK의 `verify`·`verifySequence`·`confirmVerified`뿐 아니라 spy·호출 기록·카운터·인자 캡처로 같은 검증을 우회하지 않는다. MockK는 의존성의 입력과 응답을 준비하는 데 사용한다.
- 기존 테스트에 결과 단언이 있으면 호출 검증만 제거한다. 호출 검증만 있는 테스트는 같은 단위의 결과 검증으로 바꾸고, 변경이 어려우면 삭제한다. 테스트를 유지하려고 운영 계약을 바꾸거나 통합 테스트·가짜 저장소를 추가하지 않는다.


| 대상 | 방식 |
| --- | --- |
| 도메인 모델·값 | 프레임워크와 모킹 없이 순수 단위 테스트로 검증한다. |
| 순수 Kotlin 객체가 아닌 의존이 필요한 단위(유스케이스 등) | 출력 포트와 외부 효과를 MockK로 대체한다. 도메인 객체·값·`Clock.fixed`는 실제 값을 쓴다. |
| RDB 출력 어댑터 | 어댑터 하나를 H2와 Flyway 스키마에 연결해 저장·조회·매핑·제약을 검증한다. |
| HTTP 입력 어댑터·외부 API 어댑터 | 입력 포트와 외부 의존을 MockK로 대체하고 MockMvc 등 전용 기술로 어댑터 하나를 검증한다. |

- 그 밖의 통합 시나리오·동시성 테스트는 사람이 명확히 지시할 때만 작성한다.
- ArchUnit·detekt 규칙과 fixture 테스트는 구조·규칙 단위 검사로 기본 빌드에 유지한다. 범위 밖 테스트를 `@Disabled`·태그 제외로 남기지 않는다.

## API 변경

- `*ApiDocumentationTest`에서 입력 포트를 MockK로 대체하고 MockMvc로 실제 컨트롤러·`OperatorTokenFilter`·요청 검증·직렬화·예외 변환의 요청·응답·상태 코드와 필요한 검증 오류·없는 리소스 응답을 검증한다. DB와 서비스 구현을 연결하지 않는다.
- API 변경과 함께 REST Docs 필드 설명을 수정하고 `src/docs/asciidoc/index.adoc`에 생성 스니펫을 연결한다.
- HTTP 상세를 README·개발 가이드에 재작성하지 않는다. 업무 규칙은 [아키텍처 문서](ddd-architecture.md#현재-업무-규칙)에서 관리한다.
- `build/generated-snippets`와 `build/docs/asciidoc/index.html`을 직접 편집하지 않는다.
- 생성 문서는 전체 빌드가 만든 `build/docs/asciidoc/index.html`과 `application.jar`의 `BOOT-INF/classes/static/docs/index.html`로 확인한다. 화면으로 볼 때는 `java -jar build/libs/application.jar`로 실행해 `http://localhost:8080/docs/index.html`만 열고 `bootRun`을 사용하지 않는다. 이 확인은 HTTP 업무 시나리오 검증이 아니다.

## 스키마 변경

- `src/main/resources/db/migration`의 마지막 버전 다음 번호로 `V<번호>__describe_change.sql`을 추가한다. 배포된 마이그레이션은 수정하지 않는다.
- Flyway로만 DDL을 적용하고 `spring.exposed.generate-ddl=false`를 유지한다.
- 기존 데이터 변환·새 제약의 테스트 작성 여부와 방식은 [테스트 범위](#테스트-범위)를 따른다. 여러 저장 작업에는 [트랜잭션 계약](ddd-architecture.md#트랜잭션)을 적용한다.
- 배포된 `V1__create_todos.sql`과 `V2__create_collection_configuration.sql`을 보존하고 새 변경은 V3 이후로 추가한다. Todo API는 없지만 기존 테이블·데이터를 삭제하지 않는다.

## 실행과 검증

로컬 앱이 필요할 때 실행한다. 앱 실행은 테스트 검증 절차에 포함하지 않는다.

```sh
java -version
export SCRAPING_API_TOKEN="$(openssl rand -hex 32)"
./gradlew bootRun
```

변경에 해당하는 검증을 먼저 실행한다. 애플리케이션 테스트를 선별할 때는 `:test` 태스크를 지정한다.

- 도메인·유스케이스: `./gradlew :test --tests '*CollectionTaskServiceTest'`처럼 대상 테스트를 지정한다.
- RDB 어댑터: `./gradlew :test --tests '*ExposedCollectionTaskRepositoryTest'`처럼 대상 어댑터 테스트를 지정한다.
- HTTP·문서: `./gradlew :test --tests '*ApiDocumentationTest'`.
- 형식: 수정은 `./gradlew ktlintFormat`, 확인은 `./gradlew ktlintCheck`.
- 검사 규칙: `./gradlew detektMain detektTest`, `./gradlew :quality-rules:test`. [검사기 지침](quality-maintenance.md)의 회귀 범위도 확인한다.
- 배포 스크립트: [배포 없이 검증](deployment.md#배포-없이-검증)의 Python 테스트도 실행한다.

마지막에 전체 빌드와 diff를 확인한다.

```sh
./gradlew build
git diff --check
git diff
```

- 전체 빌드의 규칙 테스트·REST Docs 생성을 생략하지 않는다.
- 검증 후 ktlint 자동 수정도 검토한다. 커밋할 때 자동 수정 파일을 빠뜨리지 않는다.
- 실패 시 `build/reports/tests/test/`, `build/reports/detekt/`, `quality-rules/build/reports/tests/test/`를 확인한다.

## 템플릿 적용

1. [Use this template](https://github.com/robinjoon-homelab/kotlin-springboot-exposed-template/generate)로 서비스 저장소를 만들고 복제한다. 첫 `main`·`master` 푸시 전에 [배포 전 설정](deployment.md#배포-전-설정)을 끝낸다.
2. 프로젝트명·패키지를 바꾸면 `settings.gradle.kts`, 소스·테스트의 선언·경로, `HexagonalArchitectureTest`의 `ROOT`와 시작 클래스 단언, 애플리케이션 이름을 함께 변경한다. 시작 클래스명을 바꾸면 `ArchitectureRules`의 `applicationClass` 인자도 맞춘다.
3. 공용 배포 파일과 `application.jar` 이름을 유지하고 앱 이름·이미지 경로는 [배포 변수](deployment.md#배포-이름과-이미지-설정)로 지정한다.
4. `./gradlew build`로 확인한다.
