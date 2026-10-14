# 코드 품질 규칙

계층·BC·주입·트랜잭션은 [아키텍처와 도메인 경계](ddd-architecture.md), 검증 명령은 [실행과 검증](development.md#실행과-검증)을 따른다. 검사기 결함은 [품질 검사 유지보수](quality-maintenance.md)에서 고치고 검사를 끄지 않는다.

## 크기와 복잡도

| 대상 | 기준 | 검사 규칙 |
| --- | --- | --- |
| 운영 함수 본문 | 최대 **30줄** | `LongMethod` |
| 테스트 함수, 사유를 기록한 개별 선언형 DSL 함수 | 최대 **80줄** | `LongMethod`, `MaximumFunctionLength` |
| 일반 함수 매개변수 | 최대 **4개** | `LongParameterList` |
| 일반 생성자 매개변수 | 최대 **6개** | `LongParameterList` |

| 대상 | 기준 | 검사 규칙 |
| --- | --- | --- |
| `if`·`when`·반복의 통합 중첩 | 최대 **2단계** | `LogicalNestingDepth` |
| 스코프 함수 중첩 | 최대 **1단계** | `NestedScopeFunctions` |
| 함수의 인지 복잡도 | 최대 **10** | `CognitiveComplexMethod` |
| 클래스 | **300줄** 초과 시 책임 분리 리뷰. 빌드를 차단하지 않는다 | `LargeClass`(warning) |

- 숫자를 맞추려고 의미 없는 분리·추상화를 만들거나, 이름만 바꾸거나, 인자를 임의 객체로 포장하지 않는다. 클래스당 함수 개수 상한은 없다.
- 함수 길이는 detekt 코드 줄 기준이다. 빈 줄·주석은 제외하고 본문 중괄호 줄은 포함한다.
- `LongMethod` 30줄은 중첩된 지역 함수 본문을 바깥 함수에서 빼고 따로 센다. `MaximumFunctionLength` **80줄은 지역 함수를 포함한 전체 본문**을 센다. 지역 함수로 옮겨 80줄 상한을 피할 수 없다.
- 80줄 허용을 Exposed 어댑터 전체나 DSL 호출이 있는 모든 함수로 넓히지 않는다. 표기는 [허용 예외](#허용-예외)를 따른다.
- 기본값이 있는 인자도 센다. `ignoreDataClasses=false`를 유지해 `data class`를 일괄 면제하지 않는다.
- 외부 계약의 `override`는 인자 제한에서 제외된다. 계약에 실제로 필요한 매개변수만 둔다.
- 중첩은 `if`, `when`, `for`·`while`·`do/while`과 Kotlin 컬렉션·시퀀스의 `forEach`·`forEachIndexed`(타입 분석으로 식별)를 1단계씩 센다. 이름만 같은 사용자 정의 DSL은 반복으로 세지 않는다.
- `else if`는 같은 깊이, 지역 함수는 별도 함수로 센다. `try`·`catch`·`finally` 자체는 깊이에 더하지 않고 내부 제어 흐름은 센다.
- `buildList { ... }` 같은 DSL과 `map`·`flatMap`의 람다 래퍼는 깊이에 더하지 않지만 람다 본문의 분기·반복은 센다. 복잡한 컬렉션 변환 연결은 리뷰한다.
- `let`, `apply`, `run`, `also`, `with`를 다른 스코프 함수 안에 중첩하지 않는다.

## 이름과 표현

- 클래스는 `PascalCase`, 함수·변수는 `camelCase`, 패키지는 소문자, 상수는 `UPPER_SNAKE_CASE`를 쓴다.
- `*Controller`, `*Config`, 포트 접미사의 역할과 위치는 [역할과 위치](ddd-architecture.md#역할과-위치)를 따른다.
- `id`, `url`, 짧은 반복문의 `i`, 타입 매개변수 `T`를 허용한다. 이름 길이에 일률적인 최솟값·최댓값을 두지 않는다.
- 이름은 업무 의미와 책임을 드러낸다. `Manager`, `Helper`, `Utils`, `Impl`은 더 구체적인 이름을 검토하는 신호이며 금지 접미사가 아니다.
- Boolean은 긍정적인 의미를 우선한다. 접두사 규칙 때문에 `completed` 같은 기존 API 계약을 바꾸지 않는다.

## 상태와 금지 문법

- 운영 코드에서 `!!`와 `lateinit`을 쓰지 않는다. 생성 시 값을 받거나 nullable을 명시적으로 처리한다.
- DTO·`data class`의 프로퍼티와 다시 대입하지 않는 지역 변수는 `val`로 쓴다. 일반 클래스 DTO의 불변성도 리뷰한다.
- `public`·`protected` 프로퍼티, 함수 인자·반환값·확장 수신자에 `MutableCollection`·`MutableList`·`MutableMap`·`MutableSet` 등 변경 가능한 컬렉션을 노출하지 않는다. 추론 타입·중첩 제네릭·상속·타입 매개변수 상한·공개 타입 별칭·클래스·인터페이스 선언과 `ArrayList`·사용자 정의 하위 타입으로 감춘 경우도 실패한다. `private`·`internal` 구현은 허용하되 외부 공유를 리뷰한다.
- 외부에서 받은 컬렉션을 보관하면 방어적 복사와 요소의 변경 가능성을 확인한다.
- 운영 코드에서 `TODO()`, `NotImplementedError`, `printStackTrace`, 직접 표준 출력을 쓰지 않는다. Kotlin과 Java `PrintStream`의 `print`·`println`·`printf`·`write` 사용도 확인한다. 로거에 맥락을 남기고 민감한 값은 출력하지 않는다.
- `System.exit`, `Runtime.exit`·`halt`, `exitProcess`를 쓰지 않고 Spring 정상 종료 흐름을 쓴다.
- 빈 `catch`, 원인을 잃는 예외 변환, 무분별한 `catch (Exception)`·`catch (Throwable)`, `finally` 안의 반환을 쓰지 않는다. HTTP 최종 오류 처리는 [허용 예외](#허용-예외)의 위치에만 둔다.
- 예외 변수를 `ignored`로 바꾸거나 `InterruptedException`을 삼키지 않는다. 재전파하거나 필요한 정리 후 중단 상태를 복구한다. 로그만 남기고 끝내지 않는다.
- `else`, `when`, `for`, 조기 `return`, 필요한 `var`·`as`, 도메인 `data class`의 비즈니스 함수, `runCatching`을 일괄 금지하지 않는다. 성공할 수 없는 캐스트는 쓰지 않고, `runCatching`은 잡는 실패 범위와 인터럽트 전파를 확인한다.

## 예외와 억제

- baseline 추가, 검사 비활성화, 실패 무시, 강제 규칙의 warning 전환, 설정 오류·경고 무시로 위반을 숨기지 않는다. 빌드를 차단하지 않는 warning은 `LargeClass`처럼 명시적으로 지정한 리뷰 신호뿐이다.
- 파일·패키지 전체 제외, 이름 기반 예외 무시, 사유 없는 `@Suppress`, `all`·규칙 묶음·접두사·별칭 표기를 쓰지 않는다.
- 억제는 가장 작은 선언에 규칙 이름으로 적용하고 정확한 위치와 사유를 남긴다.
- [금지 억제 목록](../config/detekt/detekt.yml)을 확인한다. 억제의 사유·위치·필요성, 목록 밖 표기, 검사 설정 변경은 직접 확인하고 변경 보고에 근거를 남긴다.

### 허용 예외

| 규칙 | 허용 범위 | 적용 위치 |
| --- | --- | --- |
| `LongMethod` | 사유를 기록한 개별 선언형 DSL 함수. `MaximumFunctionLength` **80줄 상한**은 계속 적용된다 | 해당 함수에 직접 `@Suppress("LongMethod")` |
| `LongParameterList` | 사유를 기록한 개별 DTO·설정 객체 생성자의 6개 초과 | 클래스가 아닌 **명시적 생성자**: `@Suppress("LongParameterList") constructor(...)` |
| `TooGenericExceptionCaught` | HTTP 최종 오류 처리 경계. 인터럽트와 원인을 보존한다 | `src/main/kotlin/**/adapter/inbound/web/*ExceptionHandler.kt`의 **함수에만** |

- 허용된 `@Suppress` 바로 앞에 빈 줄 없이 `// quality-exception: 구체적인 사유`를 쓰고 주석·적용 위치를 직접 확인한다.
- 표 밖의 억제(`UNCHECKED_CAST` 등)도 가장 작은 선언에만 둔다.
- 허용 예외나 표 밖의 억제를 쓰면 변경 보고에 필요성·사유·범위·검토한 대안을 남긴다.

## 테스트 코드의 차이

테스트 소스에는 `config/detekt/test.yml`의 다음 차이만 적용하고 나머지는 운영 규칙과 같다.

| 규칙 | 테스트에서의 차이 |
| --- | --- |
| `LongMethod` | 테스트 함수는 80줄까지. 지역 함수를 포함한 80줄 상한은 같다 |
| `FunctionNaming` | `@Test`, `@ParameterizedTest`, `@RepeatedTest`, `@TestFactory`, `@TestTemplate` 함수만 한글·공백 포함 백틱 이름과 공백 없는 한글 이름을 허용한다. 보조 함수·운영 함수로 넓히지 않는다 |
| `LateinitUsage` | `@Autowired`, `@Inject`, `@Resource` 주입 필드만 `lateinit` 허용 |
| `VarCouldBeVal` | `@Autowired`, `@Inject`, `@Resource`, `@LocalServerPort` 필드는 검사하지 않는다 |

이 필드 주입 허용을 운영 코드의 [생성자 주입 규칙](ddd-architecture.md#의존성-주입과-빈-조립)에 적용하지 않는다.
