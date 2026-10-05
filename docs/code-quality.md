# 코드 품질 규칙

이 문서는 Kotlin·Spring 프로젝트의 코드 품질 기준과 검사 범위를 정한다. 숫자는 보편적인 정답이 아니라 이 템플릿의 기본 정책이다. 작은 함수와 명확한 경계를 만들되, 숫자를 맞추기 위한 의미 없는 분리나 불필요한 추상화는 피한다.

## 크기와 복잡도

- 운영 함수 본문은 최대 **30줄**이다. detekt가 세는 코드 줄 기준이며 빈 줄·주석은 제외하고 본문 중괄호 줄은 포함한다.
- 테스트 함수와 사유를 명시하여 승인한 개별 선언형 DSL 함수만 최대 **80줄**을 허용한다. Exposed 어댑터 전체나 DSL 호출이 있는 모든 함수를 면제하지 않는다.
- 일반 함수는 매개변수 최대 **4개**, 일반 생성자는 최대 **6개**다. 기본값이 있는 인자도 센다. DTO·설정 객체의 생성자는 사유를 승인한 개별 선언만 예외로 둔다. 외부 계약의 `override`는 detekt가 인자 제한에서 제외하므로 실제 계약에 필요한 예외인지 리뷰한다. `ignoreDataClasses=false`로 모든 `data class`를 일괄 면제하지 않는다.
- `if`, `when`, `for`·`while`·`do/while`의 통합 중첩은 최대 **2단계**다. Kotlin 컬렉션·시퀀스의 `forEach`·`forEachIndexed`도 타입 분석으로 식별하여 반복문처럼 1단계로 센다. 이름만 같은 사용자 정의 DSL은 반복문으로 취급하지 않는다.
- `if → for`는 허용하지만 그 안에 `if`를 더하면 실패한다. `else if`는 같은 깊이로 계산하고 지역 함수는 별도 함수로 다시 센다. `try`·`catch`·`finally` 래퍼 자체는 깊이에 더하지 않으며 내부 제어 흐름은 검사한다.
- `transaction { ... }` 같은 DSL과 `map`·`flatMap` 변환 함수의 람다 래퍼는 깊이에 더하지 않는다. 사용자 정의 규칙은 모든 람다 본문을 순회하므로 그 내부의 분기·반복도 검사한다. 복잡한 컬렉션 변환 연결은 리뷰한다.
- `let`, `apply`, `run`, `also`, `with` 스코프 함수는 최대 **1단계**다. 다른 스코프 함수 안에 중첩하지 않는다.
- 함수의 인지 복잡도는 최대 **10**이다. 함수를 분리할 때 이름만 바꾸거나 인자를 임의 객체로 포장해 제한을 우회하지 않는다.

클래스가 **300줄**을 넘으면 책임 분리를 리뷰한다. 이것만으로 빌드를 차단하지 않으며 클래스당 함수 개수에도 강제 상한을 두지 않는다. 짧은 private 함수를 만들었다는 이유로 불이익을 주지 않는다.

detekt `LongMethod`는 중첩된 지역 함수의 본문을 바깥 함수의 30줄 계산에서 제외하고 별도로 검사한다. 사용자 정의 `MaximumFunctionLength`의 **80줄 상한은 지역 함수를 포함한 전체 본문**을 센다. 지역 함수로 코드를 옮겨 전체 함수의 상한을 피할 수 없다.

## 이름과 표현

- 클래스는 `PascalCase`, 함수·변수는 `camelCase`, 패키지는 소문자, 상수는 `UPPER_SNAKE_CASE`를 쓴다.
- 웹 컨트롤러는 `*Controller`로 이름 짓고 웹 어댑터에 둔다. 저장소 구현은 저장소 어댑터에 둔다. 역할과 위치는 아키텍처 테스트로 검사한다.
- `id`, `url`, 짧은 반복문의 `i`, 타입 매개변수 `T`를 허용한다. 이름 길이에 일률적인 최솟값·최댓값을 두지 않는다.
- JUnit의 `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@TestTemplate` 함수에는 한글·공백 포함 백틱 이름을 허용하며 공백 없는 한글 이름도 허용한다. 테스트 파일의 일반 보조 함수나 운영 함수로 명명 예외를 확대하지 않는다.
- 이름은 업무 의미와 책임을 드러낸다. `Manager`, `Helper`, `Utils`, `Impl`은 더 구체적인 이름을 검토하는 신호이며 무조건 금지하는 접미사가 아니다.
- Boolean은 긍정적인 의미를 우선한다. 접두사 규칙만 맞추려고 `completed` 같은 기존 API 계약을 바꾸지 않는다.

단일 책임, 이름의 의미, 적절한 추상화 수준은 리뷰로 판단한다. 정규식이나 줄 수 검사가 이 판단을 대신하지 않는다.

## 상태와 금지 문법

- 운영 코드에서 `!!`와 `lateinit`을 사용하지 않는다. 필요한 값을 생성 시 받거나 nullable 상태를 명시적으로 처리한다. 테스트의 `lateinit`은 프레임워크 주입 필드에만 허용한다.
- DTO 프로퍼티는 `val`을 사용한다. `DataClassShouldBeImmutable`이 `data class`의 `var`를 검사하며 일반 클래스 DTO의 불변성도 리뷰한다. 다시 대입하지 않는 지역 변수는 `val`로 바꾼다.
- `public`·`protected` 프로퍼티, 함수 인자·반환값·확장 수신자에 `MutableCollection`, `MutableList`, `MutableMap`, `MutableSet` 같은 변경 가능한 컬렉션을 노출하지 않는다. 추론된 타입·중첩 제네릭·상속한 제네릭과 공개 타입 별칭·클래스·인터페이스 선언 자체도 검사한다. `ArrayList`나 사용자 정의 하위 타입으로 감춘 변경 가능 계약도 금지한다. `private`·`internal` 내부 구현은 허용하지만 외부 공유를 리뷰한다.
- `val`과 읽기 전용 `List`는 깊은 불변성을 보장하지 않는다. 외부에서 받은 변경 가능한 컬렉션을 보관할 때는 방어적 복사와 요소의 변경 가능성도 검토한다.
- 운영 코드의 `TODO()`, `NotImplementedError`, `println`·직접 표준 출력, `printStackTrace`를 금지한다. 진단은 필요한 맥락을 포함해 로거로 남기고 민감한 값은 출력하지 않는다.
- 정적 분석은 Kotlin과 Java `PrintStream`의 `print`·`println`을 차단한다. `printf`·`write` 등 다른 직접 표준 출력도 같은 금지 정책으로 리뷰한다.
- `System.exit`, `Runtime.exit`·`halt`, `exitProcess`로 프로세스를 강제로 종료하지 않는다. Spring의 정상 종료 흐름을 사용한다.
- 빈 `catch`, 원인을 잃는 예외 변환, 무분별한 `catch (Exception)`·`catch (Throwable)`, `finally` 안의 반환을 금지한다. HTTP 최종 오류 처리 경계는 정확한 위치와 사유를 밝힌 예외로 둔다.
- 예외 변수 이름을 `ignored`로 바꾸거나 `InterruptedException`이라는 이유로 삼키지 않는다. 중단 예외는 재전파하거나 필요한 정리 후 중단 상태를 복구한다. 로그만 남겼다는 이유로 복구가 올바른 것은 아니다.

`else`, `when`, `for`, 조기 `return`, 필요한 `var`, 모든 `as`, `runCatching`을 일괄 금지하지 않는다. `runCatching`이 잡는 실패의 범위와 인터럽트 전파는 사용 지점에서 검토한다. `UnsafeCast`는 성공할 수 없는 캐스트를 검출하며 모든 캐스트를 금지하는 규칙이 아니다. 도메인 `data class`의 비즈니스 함수도 허용한다.

## Spring과 헥사고날 경계

의존 방향은 `adapter → application → domain`이다. 순수 Kotlin 도메인과 애플리케이션은 Spring, Exposed, HTTP, JDBC, 파일·네트워크 I/O를 직접 참조하지 않는다. 기술 작업은 출력 포트를 통해 요청하고 어댑터에서 실행한다.

- 운영 코드의 의존성 주입은 생성자로 한다. 필드·세터의 `@Autowired`, `@Inject`, `@Resource`, `@Value` 주입은 아키텍처 테스트로 차단한다. 생성자로 받은 의존성을 `private val`로 보관하는지는 코드 리뷰에서도 확인한다. Spring 테스트의 프레임워크 주입은 운영 코드 규칙과 구분한다.
- 포트는 계약이며 서비스 구현을 참조하지 않는다. 어댑터는 대응하는 포트를 구현하거나 입력 포트를 호출한다. 어댑터 간 직접 의존을 만들지 않는다.
- Exposed `Table`, `Query`, `ResultRow` 등의 타입을 포트·도메인·웹 요청/응답에 노출하지 않는다. 기술 설정을 위한 `config`의 의존과 저장소 어댑터 내부 사용은 허용한다.
- 새 운영 클래스를 검사 대상 패키지 밖에 두어 검사를 피하지 않는다. 엔트리포인트, 설정, 허용된 계층 중 어느 곳에 속하는지 아키텍처 테스트가 확인한다.

아키텍처 검사는 Gradle이 전달한 전체 운영 클래스 출력 경로를 읽는다. 포트 패키지에서 `*UseCase`, `*Repository`, `*Port` 역할의 타입은 인터페이스로 선언한다. `Command`·`Result` 같은 데이터 계약과 입력 포트의 예외 타입은 허용하며 순수성·불변성 규칙은 유지한다. Kotlin이 생성한 companion·기본 구현 보조 클래스는 일반 구현과 구분한다. 포트 간 계약 참조는 허용하되 서비스·어댑터·설정 참조는 막는다.

`@Configuration`은 `config`의 `*Config`, `@Controller`·`@RestController`와 합성 컨트롤러 애너테이션은 웹 어댑터의 `*Controller`에 둔다. 저장소 구현과 출력 포트 구현은 이름의 접미사와 관계없이 저장소 어댑터에 둔다. 패키지 간 순환 의존도 검사한다.

도메인·애플리케이션에서는 `java.io`(`Serializable` 제외), `java.sql`, `javax.sql`, `java.net`, `java.nio.file`, `java.nio.channels`, `kotlin.io` 의존을 금지한다. JVM 기본 타입 전체를 허용한다는 이유로 외부 I/O가 통과하지 않도록 별도로 검사한다.

여러 저장소 변경이 하나의 업무라면 트랜잭션 경계를 먼저 설계하고 **중간 실패 시 전체 롤백되는 통합 테스트**를 추가한다. 저장소 메서드별 트랜잭션이나 순차 호출만으로 여러 변경의 원자성이 보장되지 않는다. 현재 단순 예제를 위해 사용하지 않는 트랜잭션 추상화를 미리 추가하지 않는다.

## 검사 도구와 실행

ktlint는 형식과 자동 수정, detekt는 소스·타입 분석, ArchUnit은 컴파일된 코드의 아키텍처 경계를 담당한다. detekt는 **`2.0.0-alpha.6`으로 고정**한다. 정식 릴리스 전 버전이므로 업그레이드할 때 JDK 25·Kotlin 호환성과 사용자 정의 규칙의 정상/위반 예제를 다시 검증한다.

애플리케이션 실행·빌드·테스트·detekt 검증에는 **JDK 25와 저장소의 Gradle Wrapper만 필요하다.** Python이나 별도 억제 검사기는 사용하지 않는다.

사용자 정의 규칙 JAR를 수정하면 다음 분석이 새 코드를 읽어야 하므로 `detekt.use.worker.api=true`로 별도 분석 프로세스를 사용한다. 테스트 분석은 비활성화된 plain JAR 대신 실제 main/test 클래스 출력 디렉터리를 참조한다.

```sh
./gradlew ktlintFormat
./gradlew detektMain detektTest
./gradlew :quality-rules:test
./gradlew test
./gradlew build
git diff
```

`test`와 `check`는 타입 분석을 수행하는 `detektMain`, `detektTest`를 먼저 실행한다. 이 저장소의 `./gradlew detekt`도 두 타입 분석 태스크를 실행하도록 연결했다. 플러그인의 기본 `detekt` 태스크만으로 되돌리면 타입 분석이 필요한 규칙을 놓칠 수 있다. 컴파일 태스크가 `detektMain`에 의존하도록 연결하면 순환 의존성이 생기므로 이 관계를 추가하지 않는다.

공통 설정은 `config/detekt/detekt.yml`, 테스트의 좁은 예외는 `config/detekt/test.yml`, 사용자 정의 규칙과 fixture는 `quality-rules/`에 있다. 보고서는 `build/reports/detekt/`, 사용자 정의 규칙 테스트 결과는 `quality-rules/build/reports/tests/test/`에서 확인한다. `build`는 사용자 정의 규칙 테스트도 실행한다.

`quality-rules`는 **빌드 전용 detekt 플러그인 모듈**이다. detekt는 분석 전에 컴파일된 규칙 JAR를 로드하므로, 애플리케이션 코드와 분리하여 먼저 컴파일한다. 이 구성은 규칙 빌드와 애플리케이션 분석 사이의 순환 의존을 피한다. 애플리케이션의 도메인·서비스를 나눈 모듈이 아니며 **실행 JAR에는 포함하지 않는다.**

애플리케이션 detekt는 `src/main`과 `src/test`를 대상으로 한다. `quality-rules` 자체는 ktlint와 규칙 fixture 테스트로 검증하며 자기 플러그인을 재귀적으로 적용하지 않는다.

사용자 정의 `clean-code` 규칙은 `LogicalNestingDepth`(제어 흐름 깊이), `NoMutableCollectionExposure`(변경 가능한 컬렉션 노출), `MaximumFunctionLength`(예외를 포함한 80줄 상한)이다. 스코프 함수 깊이는 타입 분석을 사용하는 detekt `NestedScopeFunctions`로 따로 검사한다.

`org.jlleitschuh.gradle.ktlint.tasks.KtLintCheckTask`는 `ktlintFormat` 뒤에 실행된다. 로컬 빌드·테스트가 소스 파일을 수정할 수 있으므로 실행 후 diff를 확인하고 수정된 파일을 함께 커밋한다. CI는 빌드 후 `git diff --exit-code`로 자동 수정 발생 여부를 확인한다. 수정된 작업 트리로 테스트가 통과해도 PR 원본이 형식 규칙을 위반했다면 CI는 실패한다.

중첩·컬렉션 노출·함수 길이 같은 사용자 정의 규칙을 수정할 때는 정상 코드, 위반 코드, 경계값, DSL 내부, 예외 허용 범위를 fixture 테스트로 검증한다. 검사를 제거하거나 예외 범위를 넓혀 통과시키지 않는다. 아키텍처 규칙을 바꾸면 금지한 의존성이 실제로 실패하는 회귀 테스트도 함께 확인한다.

## 예외와 검사 설정 변경

새 템플릿은 **baseline 없이 시작**한다. 검사 실패를 무시하지 않고 `ignoreFailures=false`, 강제 규칙의 오류 심각도, 설정 검증과 **설정 경고**의 오류 처리를 유지한다. 설정 오타·폐기된 옵션을 조용히 무시하지 않는다. 클래스 300줄 초과처럼 명시적으로 지정한 리뷰용 warning은 빌드를 차단하지 않는다.

예외는 가장 작은 선언에 적용하고 **규칙 이름·정확한 위치·필요한 이유**를 남긴다. DSL 80줄, DTO·설정 생성자, HTTP 최종 예외 경계 외의 예외는 필요성과 대안을 먼저 리뷰한다. 파일·패키지 전체 제외, 이름으로 예외 무시, 사유 없는 `@Suppress`, baseline 추가로 기존 위반을 숨기지 않는다. 외부 계약의 `override`는 계약을 바꿀 수 없는 이유가 범위를 정한다.

detekt의 **`ForbiddenSuppress`**가 설정에 열거한 억제 문자열을 검사한다. `all`, 규칙 묶음, 강제 규칙과 그 접두사 표기를 금지 목록에 둔다. 별도 소스 검사기는 두지 않으며 **억제의 사유·적용 위치·필요성은 리뷰**한다. 설정되지 않은 표기나 검사 설정 변경까지 자동으로 막는 보안 경계는 아니다.

일반적으로 허용하는 예외는 **`LongMethod`, `LongParameterList`, `TooGenericExceptionCaught`**다. `UNCHECKED_CAST` 같은 다른 억제도 필요성과 범위를 먼저 리뷰한다. 허용된 `@Suppress`의 바로 앞에는 `// quality-exception: 구체적인 사유`를 빈 줄 없이 기록한다. 이 주석과 아래의 적용 위치 규칙은 리뷰 기준이며 기계적으로 검증하지 않는다.

승인된 선언형 DSL 함수에는 사유와 함께 함수에 직접 `@Suppress("LongMethod")`를 쓸 수 있다. 사용자 정의 `MaximumFunctionLength`는 이 경우에도 **80줄 상한**을 검사하므로 무제한 면제가 아니다. 현재 예제의 운영 코드에는 이 예외가 필요하지 않다. DTO·설정 생성자 6개 초과는 **클래스 위가 아닌 명시적 생성자**의 `@Suppress("LongParameterList")`로 한정한다. 다음은 사유와 애너테이션의 위치 예시다.

```kotlin
data class ImportRow
    // quality-exception: 외부 파일의 7개 열을 그대로 표현하는 입력 계약이다.
    @Suppress("LongParameterList")
    constructor(
        val id: String,
        val title: String,
        val category: String,
        val owner: String,
        val status: String,
        val source: String,
        val version: String,
    )
```

`TooGenericExceptionCaught` 예외는 `src/main/kotlin/**/adapter/inbound/web/*ExceptionHandler.kt`의 **함수에만** 허용한다. 이 경계에서도 인터럽트와 원인을 보존하는지 리뷰한다. 테스트 `lateinit` 예외는 `@Autowired`, `@Inject`, `@Resource` 주입 필드에만 적용한다.

검사 설정, 빌드 연결, 사용자 정의 규칙, 아키텍처 테스트, CI, `CODEOWNERS` 자체의 변경은 코드 소유자 리뷰 대상이다. 억제·제외 경로를 추가할 때도 같은 절차를 따른다. 도구 설정 파일은 수정 가능한 코드이므로 CI만으로 규칙 약화를 막을 수 없다.

## GitHub에서 병합 차단 활성화

`.github/rulesets/main-master.json`은 **서버에 가져올 ruleset 초안**이다. 파일을 커밋하거나 템플릿으로 복사하는 것만으로 GitHub에 적용되지 않는다. 템플릿 공개와 ruleset 활성화는 별도 작업이며, 원본 템플릿에는 이 ruleset을 활성화하지 않았다.

새 저장소에 병합 보호를 적용하려면 다음을 진행한다.

1. `.github/CODEOWNERS`의 `@robinjoon`을 새 저장소에서 **write 권한을 가진 실제 담당자 또는 팀**으로 교체한다.
2. 최초 푸시 후 GitHub Actions의 `Build and test`가 한 번 실행되어 통과하는지 확인한다. 템플릿에서 생성한 서비스 저장소의 `main`·`master` 푸시는 배포도 시작하므로 [배포 사전조건](deployment.md#플랫폼-사전조건)을 먼저 준비한다. GitHub의 Template repository 설정이 켜진 원본은 검증만 실행한다.
3. 이후 저장소 **Settings → Rules → Rulesets**에서 `main-master.json`을 가져오고 `main`, `master` 대상과 활성 상태를 확인한다. 이 ruleset은 브랜치 생성 시 검사 면제를 허용하지 않으므로 최초 업로드 전에 활성화하면 브랜치 생성을 막을 수 있다.
4. `Build and test` 필수 검사의 출처가 GitHub Actions인지 확인한다. 대상 브랜치의 최신 변경 반영, PR과 승인 **1개**, 코드 소유자 승인, 새 커밋 후 기존 승인 취소, 마지막 푸시의 다른 사람 승인, 미해결 리뷰 대화 해소를 요구하며 우회 대상은 두지 않는다.

PR 작성자는 자기 PR을 승인할 수 없다. **1인 저장소도 이 정책을 그대로 쓰려면 별도 승인자가 필요하다.** GitHub 요금제·저장소 공개 범위에 따라 ruleset 기능 사용 가능 여부도 확인한다. GitHub 정책의 실제 강제 여부는 서버 설정에서 확인해야 한다.

## 판단 근거

- [Kotlin 코딩 규약](https://kotlinlang.org/docs/coding-conventions.html), [스코프 함수](https://kotlinlang.org/docs/scope-functions.html), [컬렉션](https://kotlinlang.org/docs/collections-overview.html): 표현식·이름·중첩·불변성의 범위.
- [detekt 복잡도 규칙](https://detekt.dev/docs/rules/complexity/), [명명 규칙](https://detekt.dev/docs/rules/naming/), [안전성 규칙](https://detekt.dev/docs/rules/potential-bugs/), [예외 규칙](https://detekt.dev/docs/rules/exceptions/): 검사 가능한 조건과 한계.
- [detekt 타입 분석](https://detekt.dev/docs/gettingstarted/type-resolution/), [호환성](https://detekt.dev/docs/introduction/compatibility/), [기본 중첩 규칙 구현](https://github.com/detekt/detekt/blob/v2.0.0-alpha.6/detekt-rules-complexity/src/main/kotlin/dev/detekt/rules/complexity/NestedBlockDepth.kt): 타입 분석 태스크와 사용자 정의 중첩 검사의 필요성.
- [Spring 생성자 주입](https://docs.spring.io/spring-framework/reference/core/beans/dependencies/factory-collaborators.html), [Exposed 트랜잭션](https://www.jetbrains.com/help/exposed/transactions.html), [ArchUnit](https://www.archunit.org/userguide/html/000_Index.html): 주입·의존 경계·원자성.
- [GitHub ruleset 규칙](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/available-rules-for-rulesets), [ruleset 가져오기](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-rulesets/managing-rulesets-for-a-repository#importing-a-ruleset), [CODEOWNERS와 브랜치 보호](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/about-code-owners#codeowners-and-branch-protection): 서버의 병합 차단과 설정 변경 보호.

수치 제한은 공개 프로젝트마다 다르다. [Gradle의 detekt 설정](https://github.com/gradle/gradle/blob/master/gradle/detekt.yml) 같은 사례는 참고 자료이며, 이 템플릿에서는 위에 명시한 더 작은 기본값을 사용한다.
