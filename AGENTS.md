# AI 에이전트 개발 지침

## 템플릿 기준과 프로젝트 적용

- 2026-10-08 작업 시작 시 원격 기본 브랜치 `master`의 HEAD를 재확인했다. 기준은 [kotlin-springboot-exposed-template@ddfbfef26e7af1ab3ee3fc4f00bc137fdb0a4a87](https://github.com/robinjoon-homelab/kotlin-springboot-exposed-template/tree/ddfbfef26e7af1ab3ee3fc4f00bc137fdb0a4a87)다. 같은 SHA의 로컬 원본을 읽었으며 다른 기준이나 기억으로 대체하지 않는다.
- 트랜잭션·아키텍처·품질 검사 기준의 프로젝트별 예외는 없다. 서비스의 선언적 트랜잭션, Exposed 스타터의 단일 매니저, `@Bean` 조립과 최신 아키텍처 규칙을 따른다.
- 템플릿의 Todo 예제 대신 출처·수집 계획·실행·레코드 모델과 기존 API를 유지한다. 근거는 여러 분야의 대상을 설정해 수집하려는 기존 사용자 요구다. 도메인·유스케이스·REST Docs·DB 동시성 테스트로 계약을 검증한다. 기존 V1·V2 마이그레이션과 데이터는 템플릿 예제로 치환하지 않는다.
- 단일 운영자의 개인 데이터 설정을 보호하는 `SCRAPING_API_TOKEN` 인증과 서비스 식별자 `scraping-platform`을 유지한다. 인증 없는 Todo API로 되돌리지 않는다. 실제 HTTP의 토큰·경로 정규화 회귀 테스트로 접근 차단을 검증하며 운영 Secret 주입은 [배포 계약](docs/deployment.md)을 따른다.
- 기본 빌드는 템플릿처럼 Docker 없이 H2에서 실행한다. 트랜잭션 변경의 실제 PostgreSQL 검증은 별도 환경변수로 활성화하는 통합 테스트로 수행한다. H2 결과를 PostgreSQL 결과로 보고하지 않는다. 적용 근거·검증 방법은 [개발 가이드](docs/development.md#템플릿-적용-기준과-차이)에 기록한다.
- 배포 자동화는 2026-10-08 로컬 템플릿 공통 계약 v2.2의 후속 변경을 반영한다. `APP_NAME=scraping-platform`과 명시 레지스트리 입력을 사용하며 GET 404일 때만 `.github/deployment.json`으로 최초 생성한다. 기존 앱의 DB·도메인·env·ServiceAccount는 태그 갱신 과정에서 재적용하지 않는다.
- 최초 생성의 `SCRAPING_API_TOKEN`은 `scraping-platform-runtime/api-token` Secret 참조만 코드에 둔다. Secret·ServiceAccount·RBAC는 helper가 생성하지 않으며 실제 값이나 존재를 조회했다고 가정하지 않는다. 설정 근거·사전조건·로컬 검증은 [배포 가이드](docs/deployment.md)를 따른다.
- 아키텍처 검사는 2026-10-09 로컬 템플릿의 공통 계약 v5를 적용한다. 계층 우선·기능 우선 구조를 같은 위치 해석기로 검사하며 현재 앱의 운영 패키지는 이동하지 않는다. 공통 엔진을 앱별로 분기하거나 기존 업무 검사를 제거하지 않는다.

## 먼저 확인할 것

- [README.md](README.md), [코드 품질 규칙](docs/code-quality.md), 변경할 코드, 관련 테스트를 먼저 읽는다. 배포 작업은 [docs/deployment.md](docs/deployment.md)도 읽는다.
- JDK 25와 저장소의 Gradle Wrapper를 사용한다. 빌드·테스트·detekt에는 Python이 필요하지 않다. 전역 Gradle에 의존하지 않는다.
- 작업 전에 `git status --short`로 기존 변경을 확인한다. 사용자의 변경을 덮어쓰거나 되돌리지 않는다.
- 요청을 재현 가능한 완료 조건으로 바꾼다. 필요한 범위만 변경하고 확인하지 않은 동작을 완료했다고 보고하지 않는다.
- 독립된 작업은 파일 소유권을 정해 서브에이전트로 병렬 진행한다. 같은 파일을 동시에 수정하지 않는다.

## 아키텍처 경계

의존 방향은 `adapter → application → domain`이다.

- `domain`: 순수 Kotlin 모델과 비즈니스 규칙. Spring, Exposed, HTTP, DB 타입을 참조하지 않는다.
- `application/port/input`: 외부에 제공하는 유스케이스 계약. Spring·Exposed에 의존하지 않는다.
- `application/port/output`: 유스케이스가 필요로 하는 저장소 등의 계약. Spring·Exposed에 의존하지 않는다.
- `application/model`: 유스케이스의 순수 데이터 계약. 언어 기본 타입·도메인·다른 애플리케이션 모델만 참조한다.
- `application/service`: 유스케이스 구현. 외부 기능은 출력 포트로 호출한다. Spring 의존성은 `org.springframework.transaction.annotation`의 `Transactional`과 선언 옵션 타입 `Propagation`·`Isolation`만 허용한다.
- `adapter/inbound/web`: HTTP 입출력, 요청 검증, 상태 코드, 오류 응답 변환.
- `adapter/outbound/persistence`: Exposed 매핑과 쿼리. 유스케이스의 Spring 트랜잭션에 참여하며 Exposed 타입을 포트나 도메인에 노출하지 않는다.
- `config`: Spring 빈 조립과 기술 설정. 어댑터 간 직접 호출을 만들지 않는다.

계층 앞에 임의 깊이의 기능 경로를 두거나 계층 아래에 역할·구현 하위 패키지를 둘 수 있다. 포트 역할의 전체 경로가 구현 경로에 연속된 완전한 세그먼트로 포함되어야 한다. 루트 포트는 공용 계약이며 다른 명시 기능의 포트 구현은 금지한다. 어댑터 격리 단위는 기능 경로·입출력 방향·첫 그룹 이름이다. 세부 예시와 공유 계약 범위는 [패키지 경계](docs/code-quality.md#하위-패키지와-기능-경계)를 따른다.

빈 조립은 `config`의 `@Bean`으로 한다. 유스케이스에 `@Service`·`@Component`, `TransactionTemplate`·트랜잭션 매니저 의존성을 추가하지 않는다. `@Transactional`은 Spring이 관리하는 빈의 외부 호출에 적용되며 같은 객체 내부 호출은 새 경계를 만들지 않는다.

모든 입력·출력 어댑터의 클래스와 메서드에는 `@Transactional`을 붙이지 않는다. 아키텍처 테스트는 합성 애너테이션과 상위 클래스·인터페이스에서 상속한 Spring 트랜잭션 선언도 금지한다. 어댑터의 트랜잭션 인프라 의존, Exposed 수동 트랜잭션, JDBC·Exposed의 직접 커밋·롤백 호출과 메서드 참조도 차단한다. JTA 도입 전에는 공통 탐지와 직접·합성·상속 회귀 검사를 먼저 추가한다.

트랜잭션은 Exposed Spring Boot 4 스타터의 매니저를 사용한다. 저장소에 `transaction {}`를 다시 넣거나 별도 트랜잭션 데코레이터·공통 추상화를 만들지 않는다. JDBC 트랜잭션 안에서 비동기 작업·코루틴으로 스레드를 전환하지 않는다. 자세한 롤백·읽기 전용 규칙은 [개발 가이드](docs/development.md#트랜잭션)를 따른다.

운영 코드의 의존성은 생성자 `private val`로 주입한다. 필드·세터 주입, 포트에서 서비스 구현 참조, 도메인·애플리케이션에서 JDBC·파일·네트워크 직접 접근을 만들지 않는다. 새 클래스를 검사 대상 패키지 밖에 두어 아키텍처 검사를 피하지 않는다.

## 코드 품질 기준

- 운영 함수는 30줄, 테스트·승인된 개별 선언형 DSL 함수는 80줄 이하다. 본문 코드 줄 기준이며 빈 줄·주석은 제외하고 본문 중괄호는 포함한다. 30줄 계산은 지역 함수 본문을 따로 세지만 80줄 전체 상한에는 지역 함수도 포함한다.
- 일반 함수 인자는 4개, 일반 생성자 인자는 6개 이하다. DTO·설정 생성자는 사유를 승인한 선언만 예외로 두며 모든 `data class`를 면제하지 않는다. 외부 계약의 `override`는 계약에 필요한 범위인지 리뷰한다.
- 분기·반복의 통합 중첩은 2단계, 스코프 함수는 1단계, 인지 복잡도는 10 이하다. Kotlin 컬렉션·시퀀스의 `forEach`·`forEachIndexed`도 반복으로 센다. `try`·DSL·`map`·`flatMap` 래퍼가 깊이에서 제외되어도 내부 분기·반복은 검사한다.
- 클래스 300줄 초과는 책임 분리 리뷰 신호다. 클래스당 함수 개수에 강제 상한을 두지 않으며 숫자만 맞추는 의미 없는 추상화를 만들지 않는다.
- 운영 코드에서 `!!`, `lateinit`, `TODO()`, 직접 표준 출력, `printStackTrace`를 사용하지 않는다. 테스트 `lateinit`은 프레임워크 주입 필드에만 허용한다.
- DTO는 `val`, 공개 컬렉션은 읽기 전용 타입을 사용한다. 외부 변경 가능한 컬렉션을 보관할 때는 방어적 복사도 검토한다. 불필요한 `var`를 남기지 않는다.
- 빈 `catch`, 원인을 잃는 예외 처리, 무분별한 포괄 `catch`, `finally` 반환을 금지한다. HTTP 최종 오류 경계의 예외는 좁게 명시한다. 인터럽트를 삼키지 않는다.
- 이름 형태는 검사 도구를 따르고 이름의 의미·단일 책임은 리뷰한다. 테스트의 한글 백틱 이름, 의미 있는 짧은 이름, 필요한 `else`·`when`·`for`·조기 반환·`var`·캐스트·`runCatching`·도메인 `data class` 함수는 일괄 금지하지 않는다.
- baseline, 사유 없는 `@Suppress`, 파일·패키지 제외, 검사 비활성화로 실패를 숨기지 않는다. 허용된 `@Suppress` 바로 앞에 `// quality-exception: 구체적인 사유`를 빈 줄 없이 기록하고 리뷰한다. `all`·규칙 묶음·접두사·별칭으로 우회하지 않는다. DSL의 `LongMethod` 예외도 80줄 상한은 유지한다. 생성자 예외를 클래스 위에 붙이지 않으며 정확한 허용 목록과 위치는 코드 품질 규칙을 따른다.

## 변경 절차

1. 비즈니스 규칙을 도메인에 작성하고 필요한 단위 테스트를 추가한다.
2. 입력/출력 포트와 유스케이스를 변경한다. 단순한 기능에 미래 확장을 위한 계층을 더하지 않는다.
3. 웹/저장소 어댑터를 구현한다. API 계약을 변경하면 REST Docs 테스트도 함께 변경한다.
4. 스키마 변경은 `src/main/resources/db/migration`에 새 Flyway 마이그레이션을 추가한다. 이미 배포된 마이그레이션 파일은 수정하지 않으며 `spring.exposed.generate-ddl=false`를 유지한다.
   여러 저장 작업이 하나의 업무라면 유스케이스의 `@Transactional` 경계 안에 두고 실제 커밋·중간 실패 시 전체 롤백을 통합 테스트한다. 모의 저장소나 테스트 자체의 자동 롤백만으로 원자성을 주장하지 않는다.
5. 변경 범위에 맞는 검증을 실행하고 마지막에 `./gradlew build`로 통합 확인한다.
6. 가능하면 구현자와 다른 에이전트가 아키텍처 경계, 실패 경로, 배포 계약을 검토한다.

## 실행과 검증

```sh
./gradlew bootRun
./gradlew detektMain detektTest
./gradlew :quality-rules:test
./gradlew test
./gradlew build
```

- `test`, `check`, `build`는 ktlint 자동 수정·검사와 타입 분석이 있는 `detektMain`, `detektTest`를 실행한다. 이 저장소의 `detekt` 집계 태스크도 두 분석을 실행한다. 자동 수정 후 `git diff`를 확인한다. CI는 포맷 변경이 남으면 실패한다.
- 린트만 수정할 때는 `./gradlew ktlintFormat`, 검사할 때는 `./gradlew ktlintCheck`를 사용한다.
- 금지한 억제 문자열은 detekt의 `ForbiddenSuppress`로 검사한다. 허용한 예외의 사유·적용 위치·필요성은 리뷰하며 별도 억제 검사기를 추가하지 않는다.
- `quality-rules`는 detekt가 분석 전에 로드할 규칙 JAR를 만드는 빌드 전용 모듈이다. 애플리케이션 실행 JAR에는 포함하지 않는다. ktlint와 자체 fixture로 검증하며 애플리케이션 detekt를 재귀 적용하지 않는다.
- 사용자 정의 규칙 변경은 `./gradlew :quality-rules:test`로 정상·위반·경계값·DSL 내부·예외 범위를 확인한다. 타입 분석 태스크를 컴파일의 선행 작업으로 연결해 순환 의존성을 만들지 않는다.
- detekt는 `2.0.0-alpha.6`으로 고정한다. 버전 변경 시 JDK 25·Kotlin 호환성과 사용자 정의 규칙의 회귀를 확인한다. 설정 검증·오류 심각도·`ignoreFailures=false`를 유지한다.
- 배포 스크립트를 변경하면 `python3 -m unittest discover -s scripts/tests -v`도 실행한다. Python은 별도 배포 도구에만 사용하며 로컬 검증에서 실제 배포 명령은 사용하지 않는다.
- REST Docs 스니펫은 테스트가 생성한다. `build/generated-snippets`와 `build/docs`의 결과물을 직접 편집하지 않는다.
- 테스트 명령, 성공/실패 결과, 실행하지 못한 검증과 이유를 최종 보고에 짧게 남긴다.
- 실패를 숨기려고 테스트나 린트를 비활성화하거나 검증 기준을 약화하지 않는다.

## 배포와 보안

- 앱 컨테이너는 JVM 기본 힙·OOM 동작을 사용한다. 이미지에 메모리 비율이나 OOM 종료 옵션을 강제하지 않는다. Gradle의 `org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8`과 공식 Wrapper 옵션은 빌드 설정으로 유지한다. 이 정책을 이유로 하네스의 리소스 요청·제한이나 클러스터 설정을 변경하지 않는다.
- 배포 워크플로는 PR 소스 커밋을 검사한다. 신뢰하지 않는 PR 코드에서 배포 자격 증명을 읽거나 `pull_request_target`으로 실행하지 않는다.
- 로컬 작업 요청만으로 커밋·푸시·PR 생성·배포·하네스 수정 권한을 추정하지 않는다. 사용자가 이미 명시한 범위는 다시 묻지 않고 진행한다.
- 토큰, 비밀번호, `.env`를 커밋하거나 로그에 출력하지 않는다. SMS/레지스트리/하네스 자격 증명은 배포 워크플로가 받는다.
- 배포 이름, 도메인, DB 환경변수, 컨테이너 포트 변경은 워크플로와 [배포 계약](docs/deployment.md)을 함께 검토한다.
- 예제 API를 실제 서비스로 확장할 때 인증·인가, 데이터 접근 범위, 운영 설정을 요구사항에 맞게 구현한다.
- `.github/rulesets/main-master.json`은 서버에 가져올 설정이다. 로컬 파일만으로 병합 보호가 활성화됐다고 보고하지 않는다. 원격 작업이 승인되면 최초 푸시·CI 통과 후 ruleset을 가져오고 CODEOWNERS의 실제 담당자·write 권한·별도 승인자를 확인한다.

## 커뮤니케이션

한국어로 결과부터 짧게 설명한다. 바뀐 동작, 검증 결과, 남은 제약을 빠뜨리지 않는다. 코드 주석은 이유와 주의점을 설명하며 채팅용 화살표나 강조 형식을 넣지 않는다.
