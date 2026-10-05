# Kotlin · Spring Boot · Exposed 템플릿

JDK 25, Kotlin 2.3.21, Spring Boot 4.1.1, Exposed 1.5.0을 사용하는 헥사고날 아키텍처 템플릿이다. 테스트로 REST Docs를 만들고 빌드 전에 ktlint로 스타일을 자동 수정하며 detekt·ArchUnit으로 코드 품질과 의존 경계를 검사한다.

## 로컬 실행

JDK 25를 설치하고 `JAVA_HOME`을 설정한다. Gradle은 저장소의 Wrapper를 사용한다.

```sh
java -version
./gradlew bootRun
```

기본 주소는 `http://localhost:8080`이다. H2 파일 DB를 `./data/application`에 자동 생성하고 Flyway로 스키마를 적용하므로 별도 DB나 Docker 없이 실행할 수 있다.

```sh
curl -i http://localhost:8080/api/todos \
  -H 'Content-Type: application/json' \
  -d '{"title":"첫 기능 만들기"}'

curl http://localhost:8080/api/todos
```

Todo 생성·조회·목록·완료 API가 포트와 어댑터의 사용 예제를 제공한다. **예제 API에는 인증·인가가 없다. 공개 배포에서는 샘플 데이터만 사용하고 실제 서비스에는 접근 제어를 추가한다.**

## 테스트·코드 품질·API 문서

**JDK 25와 저장소의 Gradle Wrapper만 필요하다.** 빌드·테스트·코드 품질 검사는 Python 없이 실행한다.

```sh
./gradlew test
./gradlew build
java -jar build/libs/application.jar
```

- `test`와 `build` 전에 ktlint 자동 수정·검사와 타입 분석을 사용하는 `detektMain`, `detektTest`를 실행한다. 자동 수정된 소스는 `git diff`로 확인한다. 남은 위반은 빌드를 실패시키며 CI는 자동 수정이 발생해도 실패한다.
- 운영 함수 30줄, 일반 함수 인자 4개·생성자 6개, 제어 흐름 중첩 2단계·스코프 함수 1단계, 인지 복잡도 10을 기본으로 검사한다. 테스트·승인된 DSL 함수 80줄 등 정확한 예외와 검사 방법은 [코드 품질 규칙](docs/code-quality.md)을 따른다.
- API 통합 테스트는 실제 H2 DB와 Flyway를 사용하며 REST Docs 스니펫을 생성한다. 도메인·유스케이스·아키텍처 경계도 검사한다.
- `build`는 테스트 결과로 HTML 문서를 만들고 실행 JAR에 포함한다. JAR 실행 후 [API 문서](http://localhost:8080/docs/index.html)를 연다.
- 기본 실행과 테스트에는 Docker가 필요하지 않다. PostgreSQL 운영 설정은 [배포 가이드](docs/deployment.md)를 참고한다.

API 문서는 테스트에서 생성하는 요청·응답 필드와 예제를 사용한다. 문서 원문은 `src/docs/asciidoc/index.adoc`, 생성 스니펫은 `build/generated-snippets`, HTML은 `build/docs/asciidoc/index.html`에 있다. `bootRun`은 API 개발용이며 생성 문서 확인에는 위의 JAR 실행 명령을 사용한다.

## 프로젝트 구조

```text
src/main/kotlin/com/example/template/
├── domain/                    # 순수 Kotlin 모델·규칙
├── application/
│   ├── port/input/            # 유스케이스 계약
│   ├── port/output/           # 외부 의존성 계약
│   └── service/               # 유스케이스 구현
├── adapter/
│   ├── inbound/web/           # HTTP 입출력
│   └── outbound/persistence/  # Exposed JDBC 저장소
└── config/                    # Spring 빈 조립
src/main/resources/db/migration/ # Flyway 마이그레이션
src/test/                      # 단위·통합·아키텍처 테스트
src/docs/asciidoc/              # REST Docs 원문
quality-rules/                 # 빌드 전용 detekt 확장 규칙
.github/workflows/             # PR 검사와 배포
```

의존 방향은 `adapter → application → domain`이다. 도메인과 유스케이스는 Spring·Exposed·HTTP 타입을 알지 못한다. JDBC 기반 MVC에 가상 스레드를 활성화하고 정상 종료, 상태 확인, 운영용 PostgreSQL 프로필을 제공한다.

`quality-rules`는 중첩 깊이, 변경 가능한 컬렉션 노출, 함수 80줄 상한을 검사하는 detekt 플러그인이다. detekt가 분석 전에 규칙 JAR를 읽을 수 있도록 별도 Gradle 모듈로 컴파일한다. 애플리케이션 계층을 나눈 모듈이 아니며 실행 JAR에는 포함하지 않는다. [검사 범위](docs/code-quality.md#검사-도구와-실행)를 참고한다.

## GitHub 템플릿으로 사용

1. [Use this template](https://github.com/robinjoon-homelab/kotlin-springboot-exposed-template/generate)을 열고 새 저장소를 만든다. 홈랩 조직에서 사용할 때는 **Owner를 `robinjoon-homelab`**으로 선택한다.
2. 생성한 저장소를 로컬에 복제한다. 원본은 이미 GitHub 템플릿이므로 별도로 템플릿 설정을 켤 필요가 없다.
3. 필요하면 패키지, 프로젝트 이름, Todo 예제를 서비스에 맞게 변경한다. 변경 후 `./gradlew build`를 실행한다.
4. `.github/CODEOWNERS`를 실제 담당자로 바꾸고, 병합 보호를 적용하려면 최초 푸시·CI 통과 후 `.github/rulesets/main-master.json`을 GitHub에 가져온다. 파일만으로 병합 차단이 적용되지 않으며, 이 정책은 승인 1개를 요구하므로 1인 저장소도 별도 승인자가 필요하다. [설정 절차](docs/code-quality.md#github에서-병합-차단-활성화)를 따른다.

이 공개 템플릿의 원본 저장소는 CI 검증만 실행하고 배포하지 않는다. 워크플로가 GitHub의 `is_template` 설정을 확인하므로, **생성한 서비스 저장소는 Template repository 설정을 켜지 않는다.**

PR 검사는 **PR 소스 저장소의 head 커밋**을 체크아웃해 실행한다. 템플릿에서 생성한 서비스 저장소의 `main` 또는 `master`에 푸시되면 검증 후 이미지를 빌드하고 홈랩에 배포한다. **저장소별 Secrets/Variables 입력은 필요 없지만, 새 저장소의 SMS 허용 목록 등록은 플랫폼 운영자가 먼저 완료해야 한다.** 이름 생성 규칙과 전체 사전조건은 [배포 가이드](docs/deployment.md)에 있다.

## 개발과 AI 에이전트

- [개발 가이드](docs/development.md): 기능 추가, API 문서 갱신, DB 마이그레이션, 검증 명령.
- [코드 품질 규칙](docs/code-quality.md): 함수·이름·문법 제한, 예외 절차, 정적 분석과 GitHub 병합 차단.
- [배포 가이드](docs/deployment.md): PR 정책, SMS, 컨테이너, 하네스 연동.
- [AGENTS.md](AGENTS.md): AI 에이전트 공통 지침. Claude와 Copilot용 진입 문서도 포함한다.
