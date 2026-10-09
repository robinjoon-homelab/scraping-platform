# 홈랩 배포 가이드

이 프로젝트는 [배포 API](https://deploy.homelab.robinjoon.xyz/)와 [Simple-K3S-Herness](https://github.com/robinjoon-homelab/Simple-K3S-Herness)의 배포 계약을 사용한다. 앱 이름은 `scraping-platform`으로 고정하고 레지스트리 주소·이미지 경로를 명시한다. 지정한 워크로드가 없으면 생성하며, 기존 워크로드는 설정을 보존하고 이미지 태그만 갱신한다. GitHub 저장소 이름이나 숫자 ID에서 배포 이름을 자동으로 만들지 않는다.

배포 helper·테스트는 2026-10-08 조정자가 제공한 로컬 템플릿 공통 계약 v2.2를 반영했다. 애플리케이션 트랜잭션 정렬 기준인 원격 `ddfbfef` 이후의 미커밋 배포 변경본이며, 아직 원격 템플릿에 게시됐다는 의미는 아니다.

**단, 플랫폼 운영자가 새 저장소의 SMS 신뢰 정책을 등록해야 한다. 조직에 속해 있다는 이유만으로 배포 권한이 생기지 않는다.** GitHub 템플릿으로 저장소를 생성해도 SMS 등록은 별도로 필요하다.

[Use this template](https://github.com/robinjoon-homelab/kotlin-springboot-exposed-template/generate)에서 Owner를 `robinjoon-homelab`으로 선택해 서비스 저장소를 만든다. 원본 템플릿은 CI 검증만 실행하며 배포하지 않는다. **서비스 저장소에서는 Template repository 설정을 켜지 않는다.**

## 플랫폼 사전조건

다음 설정은 플랫폼에서 한 번 준비하거나 새 저장소를 등록할 때 처리한다.

- SMS가 해당 저장소, `.github/workflows/ci.yml`, `refs/heads/main` 또는 `refs/heads/master`의 GitHub OIDC 요청을 허용해야 한다. 사용하는 브랜치를 정확히 등록한다.
- `robinjoon-homelab/Simple-K3S-Herness/.github/actions/load-ci-secrets@v1.0.0`의 `app: zot`은 `REGISTRY_USERNAME`·`REGISTRY_PASSWORD`, `app: harness`는 `HARNESS_ACTIONS_TOKEN`을 제공해야 한다.
- 레지스트리 사용자명·비밀번호로 지정한 Zot 이미지 경로에 푸시가 가능해야 한다. `HARNESS_ACTIONS_TOKEN`으로 배포 API 인증·하네스 main의 Contents 조회·`apply-workload.yml`과 `release-workload-image.yml` 실행·생성 실행 결과 조회가 가능해야 한다. 배포 API는 호출자의 토큰으로 GitHub에 접근한다. 공개 저장소의 읽기 접근은 토큰 종류와 정책에 따라 가능하므로 Contents 권한 미부여만으로 실패한다고 단정하지 말고 실제 접근 조건을 확인한다. 저장소에서 외부 액션 사용도 허용되어야 한다.
- 클러스터에 `shared-db-app` DB 자격 증명, `registry-credentials` 이미지 풀 자격 증명, PostgreSQL, Argo CD, Ingress와 cert-manager가 준비되어야 한다.
- `*.homelab.robinjoon.xyz` DNS와 HTTPS 발급에 필요한 플랫폼 설정이 준비되어야 한다.

템플릿 파일에는 실제 자격 증명을 넣지 않는다. SMS 정책이 거부하는 경우 저장소 Secrets를 임의로 추가해 우회하지 말고 플랫폼의 신뢰 대상과 토큰 권한을 확인한다.

## 배포 이름과 이미지 설정

서비스 저장소의 **Settings → Secrets and variables → Actions → Variables**에 다음 값을 지정한다. 로그인 자격 증명은 Variables에 넣지 않고 SMS에서 받는다.

| 변수 | 예시 | 의미 |
| --- | --- | --- |
| `HOMELAB_REGISTRY_HOST` | `registry.homelab.robinjoon.xyz` | 스킴·포트·경로 없는 FQDN, 로컬 검증은 localhost 허용 |
| `HOMELAB_REGISTRY_IMAGE` | `apps/scraping-platform` | 태그 없는 이미지 경로 |

CI의 `APP_NAME`은 명시 문자열 `scraping-platform`이다. 위 두 Variables를 `REGISTRY_HOST`, `REGISTRY_IMAGE` 환경변수로 전달한다. 미지정 레지스트리 값은 자격 증명 발급 전에 배포 오류로 처리하고 저장소 이름으로 대체하지 않는다. 앱 이름은 소문자 영숫자·하이픈으로 된 1~50자이고 시작과 끝은 영숫자다. 이번 로컬 작업에서 원격 Variables를 생성하거나 변경하지 않았다.

이미지는 `registry.homelab.robinjoon.xyz/apps/scraping-platform:sha-<전체 SHA>-run-<실행 ID>-<재실행 횟수>`다. `latest`를 사용하지 않는다. **기존 앱에 연결할 때는 하네스의 앱 이름과 이미지 repository를 그대로 지정한다.** 다른 repository를 쓰는 기존 대상은 덮어쓰지 않고 실패한다. GitHub 저장소를 옮기거나 이름을 바꾸어도 이 명시 설정은 유지된다.

`apply`는 전달된 태그가 현재 GitHub SHA·실행 ID·재실행 횟수에서 만든 태그와 정확히 일치하는지도 API 호출 전에 검사한다. 다른 실행의 태그를 임의로 넘겨 현재 배포로 처리할 수 없다. Docker Hub 경로로 오인되는 단일 label 레지스트리 이름은 거부한다.

2026-10-08 확인한 하네스 `main`(`11ce95932b56c1b7c08954dce5d75fb643636ab0`)에는 `scraping-platform`과 기존 자동 이름 `app-scraping-platform-1405096134` 선언이 모두 없었다. 유일한 [기존 CI 실행](https://github.com/robinjoon-homelab/scraping-platform/actions/runs/37261749272)은 빌드 중 취소됐으며 배포 단계 실행 증거도 없다. 이를 근거로 새 이름을 선택했지만 실제 클러스터·DB·레지스트리의 부재까지 확인한 것은 아니다. 배포 시점에는 helper가 지정한 앱을 다시 조회한다. 이전 이름의 DB·워크로드가 별도로 존재해도 자동 이전·삭제하지 않는다.

## GitHub Actions 동작

`.github/workflows/ci.yml`의 PR 이벤트는 `opened`, `synchronize`, `reopened`, `ready_for_review`다. 검사 잡은 `pull_request.head.repo.full_name`과 `pull_request.head.sha`를 사용해 **PR 소스 저장소의 커밋**을 체크아웃한다. GitHub가 합성한 merge 커밋을 검사하는 방식이 아니다.

PR에서는 읽기 전용 저장소 권한으로 배포 스크립트의 Python 단위 테스트와 `./gradlew build`를 실행한다. Gradle 빌드는 Python 없이 ktlint, 타입 분석이 있는 detekt, 사용자 정의 규칙 테스트, 애플리케이션·아키텍처 테스트, REST Docs 생성을 실행한다. 이후 `git diff --exit-code`로 자동 포맷 변경도 실패시킨다. 자격 증명 발급이나 배포는 실행하지 않는다. 포크 PR은 GitHub 설정에 따라 관리자의 첫 실행 승인이 필요할 수 있다.

푸시·수동 실행에서는 GitHub API로 저장소의 `is_template` 설정을 확인한다. **`true`이면 검증만 실행하고 배포 잡을 건너뛴다.** `false`인 서비스 저장소의 `main`·`master` 푸시 또는 해당 브랜치 수동 실행에서는 다음 순서로 동작한다. 다른 브랜치의 수동 실행은 검증만 수행한다. PR 병합도 푸시 이벤트를 발생시키며 직접 푸시도 배포를 트리거한다.

병합 보호를 적용하려면 최초 업로드와 CI 통과 후 `.github/rulesets/main-master.json`을 GitHub에 가져온다. 파일이나 템플릿 복사만으로 서버 설정이 바뀌지 않는다. CODEOWNERS 교체, 승인자, 필수 검사 설정은 [코드 품질 규칙](code-quality.md#github에서-병합-차단-활성화)을 따른다.

1. PR과 같은 검증을 실행하고 테스트를 통과한 REST Docs 포함 `application.jar`를 아티팩트로 저장한다.
2. 배포 잡이 해당 JAR를 내려받고 명시한 이름·이미지 경로·최초 생성 설정을 검사한 뒤 OIDC로 SMS에서 자격 증명을 읽는다.
3. Dockerfile이 검증한 JAR를 JRE 25 이미지에 넣고 지정한 레지스트리·이미지 경로에 게시한다.
4. 앱 조회가 HTTP 404일 때만 배포 API로 워크로드·논리 DB·Service·HTTPS Ingress 생성을 요청한다. 생성 실행의 `committed`·`unchanged` 결과를 기다리고 앱을 다시 읽는다. 인증 실패·서버 오류·잘못된 응답을 없는 앱으로 취급하지 않는다.
5. 기존 앱은 `app` 컨테이너와 이미지 repository를 확인하고, 다른 태그라면 하네스의 `release-workload-image.yml`로 태그만 변경한다. 이미 목표 태그라면 추가 요청을 하지 않는다.
6. 하네스 `main`에서 목표 태그를 확인한 뒤 **조회된 Ingress의 실제 URL**을 Actions 요약에 남긴다. 공개 Ingress가 없으면 주소를 만들어서 표시하지 않는다.

배포 잡은 저장소 내에서 순차 실행된다. 이미지 게시 후 해당 브랜치의 최신 커밋이 아니면 하네스 갱신을 건너뛴다. `main`과 `master`는 같은 앱을 대상으로 하므로 실제 운영 기본 브랜치를 하나로 정해 사용한다.

생성 결과와 태그 반영은 각각 최대 60회, 미완료 응답 뒤 10초 간격으로 조회한다. HTTP 요청 제한은 30초, `gh` 호출 제한은 60초다. 요청 시간이 추가되므로 전체 10분 상한은 아니며 배포 잡 전체 제한 30분에는 이미지 빌드·게시도 포함된다. 통신 오류의 자동 재요청이나 실패 시 롤백은 하지 않는다. 요청 수락 후 확인이 실패하면 원격 실행이 계속될 수 있으므로 재실행 전에 하네스 실행과 현재 태그를 확인한다.

**Actions의 성공은 하네스에 배포 선언이 기록됐다는 뜻이다. Argo CD 동기화, Pod 준비 상태, DNS와 TLS의 실제 동작까지 확인한 결과는 아니다.** 배포 후 앱 URL과 상태 확인 엔드포인트를 확인한다.

## 최초 워크로드 생성 설정

기본 생성은 replica 1의 `app` 컨테이너, 포트 8080, Service `web`의 80 포트, HTTPS Ingress다. DB명은 명시한 앱 이름의 `-`를 `_`로 바꾸고, 공개 도메인은 `<앱 이름>.homelab.robinjoon.xyz`를 사용한다. DB는 공유 PostgreSQL 안의 논리 DB이며 새 PostgreSQL 서버나 새 계정·비밀번호를 생성하지 않는다.

프로젝트 고유 설정은 `.github/deployment.json`에 기록했다. DB는 `scraping_platform`, 도메인은 `scraping-platform.homelab.robinjoon.xyz`이며 토큰 참조를 포함한 전체 초기 환경변수를 제공한다. 공통 helper의 허용 필드는 `database`, `host`, `env`, `serviceAccountName` 네 개다. 파일이 없으면 공통 기본값을 사용하므로 **이 프로젝트의 토큰 참조를 제공하는 파일을 제거하지 않는다.** 잘못된 파일은 배포 전에 실패한다.

```json
{
  "database": "scraping_platform",
  "host": "scraping-platform.homelab.robinjoon.xyz",
  "env": [
    {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
    {"name": "DB_PORT", "secretKeyRef": {"name": "shared-db-app", "key": "port"}},
    {"name": "SPRING_DATASOURCE_URL", "value": "jdbc:postgresql://$(DB_HOST):$(DB_PORT)/scraping_platform"},
    {"name": "SPRING_DATASOURCE_USERNAME", "secretKeyRef": {"name": "shared-db-app", "key": "username"}},
    {"name": "SPRING_DATASOURCE_PASSWORD", "secretKeyRef": {"name": "shared-db-app", "key": "password"}},
    {"name": "SCRAPING_API_TOKEN", "secretKeyRef": {"name": "scraping-platform-runtime", "key": "api-token"}}
  ]
}
```

`env`를 지정하면 기본 환경변수 목록을 **전부 교체**한다. 필요한 prod·DB 설정도 함께 넣고, `DB_PORT`는 이를 참조하는 URL보다 앞에 둔다. `$(DB_PORT)`를 정의보다 먼저 참조하면 helper가 거부한다. `DB_HOST`는 플랫폼이 먼저 주입하므로 직접 설정하지 않는다. 비밀값은 `value`가 아닌 `secretKeyRef`로 연결하며 참조 대상 Secret은 별도로 준비해야 한다. `serviceAccountName`도 기존 ServiceAccount를 선택할 뿐 ServiceAccount·RBAC를 생성하지 않는다.

최초 실행 전에 워크로드 namespace에 **Secret `scraping-platform-runtime`, key `api-token`**을 별도로 준비한다. 이 이름은 기존 Secret의 존재를 확인한 값이 아니라 프로젝트 소유자가 정한 연결 계약이다. 실제 토큰 값은 코드·plan·로그에 넣지 않으며 helper는 값을 조회하거나 Secret을 생성하지 않는다. 현재 앱은 Kubernetes API나 런타임 SMS 호출을 하지 않으므로 별도 ServiceAccount·RBAC를 지정하지 않는다.

**이 JSON은 최초 생성에만 사용한다.** 기존 앱의 DB·환경변수·ServiceAccount·Ingress를 재적용하지 않는다. 앱 이름을 다른 값으로 바꾸면 다른 워크로드를 대상으로 하며 기존 데이터 이전·삭제는 자동 수행하지 않는다.

## 컨테이너와 DB 계약

Dockerfile은 JRE 25, 비루트 사용자 UID/GID `10001`, HTTP 포트 `8080`을 사용한다. 이미지 대상은 현재 `linux/amd64`다. CPU 아키텍처를 바꾸려면 CI 이미지 빌드 설정과 클러스터 노드를 함께 확인한다.

Dockerfile은 `JAVA_TOOL_OPTIONS`를 설정하지 않으며 앱의 힙 크기·비율과 OOM 동작은 JVM 기본값을 사용한다. 배포 환경에서 별도 JVM 옵션을 주입하면 해당 설정이 적용된다.

하네스가 DB 호스트와 자격 증명을 제공한다. 생성 워크로드는 `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`를 설정하고 공용 DB 시크릿의 포트를 사용한다. `prod`는 PostgreSQL을 사용하며 H2로 대체하지 않는다.

애플리케이션 시작 시 Flyway가 마이그레이션을 실행한다. 상태 확인 경로는 `/actuator/health`, 프로브용 엔드포인트는 `/actuator/health/liveness`와 `/actuator/health/readiness`다. 공개되는 Actuator 항목은 `health`, `info`이며 민감한 상세 정보는 공개하지 않는다.

현재 하네스 스키마는 `livenessProbe`·`readinessProbe` 설정을 지원하지 않으므로 Kubernetes의 자동 프로브 연결은 포함되지 않는다. 앱의 상태 확인 엔드포인트 제공과 클러스터의 자동 상태 검사는 별개다.

첫 생성 이후 이 워크플로는 이미지 태그만 갱신한다. 환경변수·리소스·Ingress 같은 배포 설정을 바꾸려면 하네스에서 별도로 변경해야 한다.

수집 설정 API는 `SCRAPING_API_TOKEN` Bearer 인증을 사용한다. 기존 워크로드에 토큰 환경변수가 없더라도 이미지 갱신은 이를 추가하지 않는다. 토큰이 없으면 API는 `503`으로 닫히며 `/actuator/health`와 문서는 공개된다. 최초 생성의 Secret 참조가 없거나 대상 key가 준비되지 않은 경우 Pod가 시작하지 못할 수 있으므로 Secret 준비와 배포 후 readiness를 별도로 확인한다.

## 배포 없이 검증

아래 명령은 생성할 설정을 출력하고 로컬 테스트·이미지 빌드만 수행한다. 원격에 반영하지 않는다.

```sh
APP_NAME=scraping-platform REGISTRY_HOST=registry.homelab.robinjoon.xyz \
  REGISTRY_IMAGE=apps/scraping-platform \
  python3 scripts/deploy.py plan

python3 -m unittest discover -s scripts/tests -v
./gradlew build
docker build -t scraping-platform:local .
```

`scripts/deploy.py apply`는 실제 배포 API와 하네스를 변경하는 명령이다. 로컬 검증에는 사용하지 않는다.

새 서비스 저장소는 SMS 등록과 사용할 기본 브랜치를 확인한 뒤 `main` 또는 `master`에 푸시한다. 이 푸시부터 배포를 시도한다. 병합 보호를 적용하려면 최초 `Build and test` 통과 후 ruleset을 활성화하고 `.github/CODEOWNERS`의 담당자에게 실제 write 권한이 있는지 확인한다. ruleset은 승인 1개를 요구하므로 1인 저장소도 별도 승인자가 필요하다.
