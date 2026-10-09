# Scraping Platform

주식·경제·시사·개발 등 다양한 분야의 온라인 데이터를 주기적으로 모아 개인 DB에 보관하기 위한 Kotlin 백엔드다. 분야를 enum으로 고정하지 않고 **출처, 수집 대상·범위, 일정, 실행, 수집 결과**를 나눈다.

현재는 **수집 설정을 관리하는 실행 가능한 초기 뼈대**다. 출처·계획 API와 DB 저장, 일정 계산, 실행 상태·레코드·체크포인트의 도메인 규칙을 구현했다. **실제 외부 요청, 주기 실행 워커, 실행 이력·수집 결과 저장은 아직 구현하지 않았다. `enabled=true`로 바꿔도 수집은 시작되지 않는다.** `HTTP_JSON`, `RSS_ATOM`, `HTML`은 설정 분류이며 동작하는 수집기 목록이 아니다.

설계와 근거는 [도메인 모델](docs/domain-model.md), [공식 사례 조사와 분야별 설정 예시](docs/collection-research.md)에 정리했다.

## 실행

JDK 25와 저장소의 Gradle Wrapper를 사용한다. 기본 H2 파일 DB와 Flyway 덕분에 별도 DB나 Docker 없이 시작한다.

```sh
java -version
export SCRAPING_API_TOKEN="$(openssl rand -hex 32)"
./gradlew bootRun
```

관리 API는 단일 운영자용이며 모든 `/api` 요청에 `Authorization: Bearer <토큰>`이 필요하다. 토큰을 설정하지 않으면 API는 `503`, 누락·불일치는 `401`을 반환한다. 토큰을 파일에 커밋하거나 공유하지 않는다. 운영에서는 HTTPS와 비밀 저장소를 사용한다. 사용자별 데이터 분리·다중 사용자 권한은 제공하지 않는다.

```sh
curl -i http://localhost:8080/api/sources \
  -H "Authorization: Bearer $SCRAPING_API_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"GitHub","kind":"HTTP_JSON","baseUrl":"https://api.github.com","credentialRef":"github-personal"}'

curl http://localhost:8080/api/sources \
  -H "Authorization: Bearer $SCRAPING_API_TOKEN"
```

응답의 `id`를 아래 `sourceId`에 넣으면 특정 저장소의 릴리스를 매시간 수집할 계획을 등록한다. 출처의 `baseUrl`에 대상의 `resource` 경로와 `parameters`를 조합하는 계약이다. `credentialRef`는 미래 수집기가 비밀 저장소에서 해결할 참조 이름이며 현재는 저장만 한다. 실제 API 키를 URL이나 parameters에 넣지 않는다.

```json
{
  "name": "Kotlin 릴리스",
  "sourceId": "생성된-출처-UUID",
  "target": {
    "resource": "/repos/JetBrains/kotlin/releases",
    "parameters": {"per_page": "100"}
  },
  "schedule": {
    "everySeconds": 3600,
    "anchorAt": "2026-10-05T00:00:00Z"
  }
}
```

`POST /api/collection-tasks`에 위 JSON을 전송한다. 새 계획은 `enabled=false`, `version=0`이다.

## 관리 API

- 출처: `POST /api/sources`, `GET /api/sources`, `GET /api/sources/{id}`. 출처는 이번 단계에서 생성 후 불변이다.
- 계획: `POST /api/collection-tasks`, `GET /api/collection-tasks`, `GET /api/collection-tasks/{id}`.
- 계획 수정: `PUT /api/collection-tasks/{id}`에 `{"expectedVersion":0,"definition":{...}}`을 전송한다.
- 활성 설정: `PATCH /api/collection-tasks/{id}/enabled`에 `{"expectedVersion":0,"enabled":true}`를 전송한다. 매 수정마다 version이 증가하며 오래된 version은 `409`로 거부한다.

목록은 `?offset=0&limit=50`이며 limit은 **1~100**이다. 고정 간격은 **60~31,536,000초**, 기준 시각은 UTC다. cron·시간대·누락 실행 재처리는 후속 범위다. 잘못된 설정은 `400`, 없는 리소스는 `404`로 응답한다. 삭제 API는 제공하지 않는다.

## 검증과 API 문서

```sh
./gradlew test
./gradlew build
java -jar build/libs/application.jar
```

`build`는 ktlint 자동 수정·검사, 타입 분석 detekt, 사용자 정의 규칙 테스트, 도메인·서비스·DB·API·아키텍처 검증과 REST Docs 생성을 실행한다. 실행 후 `git diff`로 포맷 변경을 확인한다. 애플리케이션 빌드에는 Python이 필요하지 않다.

빌드한 JAR를 실행하면 [API 문서](http://localhost:8080/docs/index.html)를 볼 수 있다. 문서와 `/actuator/health`는 공개된다. `bootRun`에서는 문서 생성 결과가 패키징되지 않는다. 기본 DB 테스트는 H2 PostgreSQL 모드에서 수행한다. 실제 PostgreSQL 트랜잭션 검증은 [개발 가이드](docs/development.md#실제-postgresql-검증)의 별도 실행 방법을 따른다.

## 구조와 다음 구현

의존 방향은 `adapter → application → domain`이다. 도메인과 포트는 Spring·Exposed·HTTP 타입을 참조하지 않는다. 서비스는 클래스의 `@Transactional(readOnly = true)`와 쓰기 메서드의 `@Transactional`로 업무 경계를 선언한다. Exposed Spring Boot 4 스타터의 단일 트랜잭션 매니저를 사용하며 저장소 DSL은 서비스 트랜잭션에 참여한다. 빈은 `@Bean`으로 조립하고 JDBC 기반 MVC를 가상 스레드로 실행한다.

템플릿 기준은 2026-10-08 원격에서 확인한 `ddfbfef26e7af1ab3ee3fc4f00bc137fdb0a4a87`이다. 적용 범위와 프로젝트 고유 차이는 [개발 가이드](docs/development.md#템플릿-적용-기준과-차이)에 기록한다.

아키텍처 검사는 2026-10-09 로컬 공통 계약을 추가 반영해 계층 우선·기능 우선 패키지를 모두 지원한다. 현재 앱 구조는 유지하며 역할 경로·기능·어댑터 그룹의 경계를 검사한다. 앱 컨테이너의 힙과 OOM 동작은 JVM 기본값을 사용하고 Gradle 빌드 설정은 유지한다.

```text
src/main/kotlin/com/example/template/
├── domain/                      # 출처·계획·고정 간격 규칙
│   └── collection/              # 실행·레코드·체크포인트·배치 규칙
├── application/port/input/      # 관리 유스케이스
├── application/port/output/     # 설정 저장소·수집기 계약
├── application/service/         # 관리 서비스
├── adapter/inbound/web/         # 인증·검증·HTTP 계약
├── adapter/outbound/persistence/# Exposed 저장소
└── config/                      # 조립·DB·운영자 토큰 설정
```

다음 구현은 허용된 출처의 수집기 하나와 실행 워커다. 실행 선점, 원본·레코드·체크포인트의 원자적 저장, 중복 방지, 속도 제한·재시도·SSRF 방어를 함께 검증해야 한다. [도메인 모델의 후속 완료 조건](docs/domain-model.md#다음-구현의-완료-조건)을 따른다.

기존 Todo 예제 API는 제거했다. 배포된 DB와의 호환성을 위해 `V1__create_todos.sql`과 기존 테이블은 보존하고 새 설정 테이블은 V2에서 추가한다. 기존 패키지명은 아키텍처 검사와 함께 유지한다.

[개발 가이드](docs/development.md) · [코드 품질 규칙](docs/code-quality.md) · [배포 계약](docs/deployment.md) · [에이전트 지침](AGENTS.md)

## 홈랩 배포 설정

배포 대상 이름은 `scraping-platform`으로 고정한다. GitHub Variables에 `HOMELAB_REGISTRY_HOST=registry.homelab.robinjoon.xyz`, `HOMELAB_REGISTRY_IMAGE=apps/scraping-platform`을 준비한다. 최초 생성은 `.github/deployment.json`의 DB·도메인·환경변수를 사용하고, 기존 대상이 있으면 이미지 태그만 갱신한다.

최초 실행에는 별도로 준비한 runtime Secret **`scraping-platform-runtime`의 `api-token` key**가 필요하다. 이 참조로 `SCRAPING_API_TOKEN`을 주입하며 비밀 값은 코드에 넣지 않는다. 실제 배포·Secret 준비·워크로드 존재 검증의 범위와 절차는 [배포 가이드](docs/deployment.md)를 따른다.
