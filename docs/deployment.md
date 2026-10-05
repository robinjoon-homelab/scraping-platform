# 홈랩 배포 가이드

이 템플릿은 [배포 API](https://deploy.homelab.robinjoon.xyz/)와 [Simple-K3S-Herness](https://github.com/robinjoon-homelab/Simple-K3S-Herness)의 배포 계약을 사용한다. 저장소 이름과 GitHub 저장소 ID로 앱·DB·도메인을 생성하므로 프로젝트별 GitHub Secrets/Variables 입력은 필요하지 않다.

**단, 플랫폼 운영자가 새 저장소의 SMS 신뢰 정책을 등록해야 한다. 조직에 속해 있다는 이유만으로 배포 권한이 생기지 않는다.** GitHub 템플릿으로 저장소를 생성해도 SMS 등록은 별도로 필요하다.

[Use this template](https://github.com/robinjoon-homelab/kotlin-springboot-exposed-template/generate)에서 Owner를 `robinjoon-homelab`으로 선택해 서비스 저장소를 만든다. 원본 템플릿은 CI 검증만 실행하며 배포하지 않는다. **서비스 저장소에서는 Template repository 설정을 켜지 않는다.**

## 플랫폼 사전조건

다음 설정은 플랫폼에서 한 번 준비하거나 새 저장소를 등록할 때 처리한다.

- SMS가 해당 저장소, `.github/workflows/ci.yml`, `refs/heads/main` 또는 `refs/heads/master`의 GitHub OIDC 요청을 허용해야 한다. 사용하는 브랜치를 정확히 등록한다.
- `robinjoon-homelab/Simple-K3S-Herness/.github/actions/load-ci-secrets@v1.0.0`의 `app: zot`은 `REGISTRY_USERNAME`·`REGISTRY_PASSWORD`, `app: harness`는 `HARNESS_ACTIONS_TOKEN`을 제공해야 한다.
- 레지스트리 사용자명·비밀번호로 Zot 이미지 푸시가 가능해야 한다. `HARNESS_ACTIONS_TOKEN`에는 배포 API 호출과 하네스의 `apply-workload.yml`·`release-workload-image.yml` 실행 권한이 필요하다. 저장소에서 외부 액션 사용도 허용되어야 한다.
- 클러스터에 `shared-db-app` DB 자격 증명, `registry-credentials` 이미지 풀 자격 증명, PostgreSQL, Argo CD, Ingress와 cert-manager가 준비되어야 한다.
- `*.homelab.robinjoon.xyz` DNS와 HTTPS 발급에 필요한 플랫폼 설정이 준비되어야 한다.

템플릿 파일에는 실제 자격 증명을 넣지 않는다. SMS 정책이 거부하는 경우 저장소 Secrets를 임의로 추가해 우회하지 말고 플랫폼의 신뢰 대상과 토큰 권한을 확인한다.

## GitHub Actions 동작

`.github/workflows/ci.yml`의 PR 이벤트는 `opened`, `synchronize`, `reopened`, `ready_for_review`다. 검사 잡은 `pull_request.head.repo.full_name`과 `pull_request.head.sha`를 사용해 **PR 소스 저장소의 커밋**을 체크아웃한다. GitHub가 합성한 merge 커밋을 검사하는 방식이 아니다.

PR에서는 읽기 전용 저장소 권한으로 배포 스크립트의 Python 단위 테스트와 `./gradlew build`를 실행한다. Gradle 빌드는 Python 없이 ktlint, 타입 분석이 있는 detekt, 사용자 정의 규칙 테스트, 애플리케이션·아키텍처 테스트, REST Docs 생성을 실행한다. 이후 `git diff --exit-code`로 자동 포맷 변경도 실패시킨다. 자격 증명 발급이나 배포는 실행하지 않는다. 포크 PR은 GitHub 설정에 따라 관리자의 첫 실행 승인이 필요할 수 있다.

`main` 또는 `master` 푸시에서는 GitHub API로 저장소의 `is_template` 설정을 확인한다. **`true`이면 검증만 실행하고 배포 잡을 건너뛴다.** `false`인 서비스 저장소에서는 다음 순서로 동작한다. PR 병합도 이 푸시 이벤트를 발생시키며 직접 푸시도 배포를 트리거한다.

병합 보호를 적용하려면 최초 업로드와 CI 통과 후 `.github/rulesets/main-master.json`을 GitHub에 가져온다. 파일이나 템플릿 복사만으로 서버 설정이 바뀌지 않는다. CODEOWNERS 교체, 승인자, 필수 검사 설정은 [코드 품질 규칙](code-quality.md#github에서-병합-차단-활성화)을 따른다.

1. PR과 같은 검증을 실행하고 테스트를 통과한 REST Docs 포함 `application.jar`를 아티팩트로 저장한다.
2. 배포 잡이 해당 JAR를 내려받고 OIDC로 SMS에서 자격 증명을 읽는다.
3. Dockerfile이 검증한 JAR를 JRE 25 이미지에 넣고 `registry.homelab.robinjoon.xyz`에 게시한다.
4. 앱이 없으면 배포 API로 워크로드·DB·Service·HTTPS Ingress를 생성한다. 이미 있으면 하네스의 `release-workload-image.yml`로 이미지 태그만 변경한다.
5. 하네스 `main`에 목표 이미지 태그가 기록될 때까지 기다린 뒤 앱 URL을 Actions 요약에 남긴다.

배포 잡은 저장소 내에서 순차 실행되며, 실행 시점에 해당 브랜치의 최신 커밋이 아니면 배포를 건너뛴다. `main`과 `master`는 같은 앱을 대상으로 하므로 실제 운영 기본 브랜치를 하나로 정해 사용한다.

**Actions의 성공은 하네스에 배포 선언이 기록됐다는 뜻이다. Argo CD 동기화, Pod 준비 상태, DNS와 TLS의 실제 동작까지 확인한 결과는 아니다.** 배포 후 앱 URL과 상태 확인 엔드포인트를 확인한다.

## 자동 생성 이름

저장소가 `owner/my-service`, GitHub 저장소 ID가 `123456789`라면 다음 이름을 사용한다.

| 항목 | 값 |
| --- | --- |
| 앱 | `app-my-service-123456789` |
| DB | `app_my_service_123456789` |
| 공개 URL | `https://app-my-service-123456789.homelab.robinjoon.xyz` |
| 이미지 저장소 | `registry.homelab.robinjoon.xyz/apps/app-my-service-123456789` |

저장소 이름을 소문자로 만들고 영숫자가 아닌 연속 문자를 `-`로 바꾼다. 정규화한 이름을 `45 - 저장소 ID 자릿수`자까지 줄여 `app-<이름>-<저장소 ID>` 전체가 최대 50자가 되게 한다. DB 이름은 앱 이름의 `-`를 `_`로 바꾼다.

이미지 태그는 `sha-<전체 커밋 SHA>-run-<Actions 실행 ID>-<재실행 횟수>`다. `latest`를 사용하지 않아 배포한 코드와 실행을 추적할 수 있다.

**GitHub 저장소 이름을 바꾸면 앱 이름과 DB 이름도 바뀐다. 기존 앱·데이터의 이전이나 삭제는 자동 처리하지 않는다.** 운영 중에는 이름을 유지하거나 별도 이전 계획을 세운다.

## 컨테이너와 DB 계약

Dockerfile은 JRE 25, 비루트 사용자 UID/GID `10001`, HTTP 포트 `8080`을 사용한다. 이미지 대상은 현재 `linux/amd64`다. CPU 아키텍처를 바꾸려면 CI 이미지 빌드 설정과 클러스터 노드를 함께 확인한다.

하네스가 DB 호스트와 자격 증명을 제공한다. 생성 워크로드는 `SPRING_PROFILES_ACTIVE=prod`, `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`를 설정하고 공용 DB 시크릿의 포트를 사용한다. `prod`는 PostgreSQL을 사용하며 H2로 대체하지 않는다.

애플리케이션 시작 시 Flyway가 마이그레이션을 실행한다. 상태 확인 경로는 `/actuator/health`, 프로브용 엔드포인트는 `/actuator/health/liveness`와 `/actuator/health/readiness`다. 공개되는 Actuator 항목은 `health`, `info`이며 민감한 상세 정보는 공개하지 않는다.

현재 하네스 스키마는 `livenessProbe`·`readinessProbe` 설정을 지원하지 않으므로 Kubernetes의 자동 프로브 연결은 포함되지 않는다. 앱의 상태 확인 엔드포인트 제공과 클러스터의 자동 상태 검사는 별개다.

첫 생성 이후 이 워크플로는 이미지 태그만 갱신한다. 환경변수·리소스·Ingress 같은 배포 설정을 바꾸려면 하네스에서 별도로 변경해야 한다.

**Todo API는 인증 없는 예제다. 자동 생성되는 HTTPS 주소에서도 누구나 예제 데이터를 읽고 변경할 수 있으므로 실제 서비스 배포 전 인증·인가를 구현한다.**

## 배포 없이 검증

아래 명령은 생성할 설정을 출력하고 로컬 테스트·이미지 빌드만 수행한다. 원격에 반영하지 않는다.

```sh
GITHUB_REPOSITORY=owner/my-service GITHUB_REPOSITORY_ID=123456789 \
  python3 scripts/deploy.py plan

python3 -m unittest discover -s scripts/tests -v
./gradlew build
docker build -t my-service:local .
```

`scripts/deploy.py apply`는 실제 배포 API와 하네스를 변경하는 명령이다. 로컬 검증에는 사용하지 않는다.

새 서비스 저장소는 SMS 등록과 사용할 기본 브랜치를 확인한 뒤 `main` 또는 `master`에 푸시한다. 이 푸시부터 배포를 시도한다. 병합 보호를 적용하려면 최초 `Build and test` 통과 후 ruleset을 활성화하고 `.github/CODEOWNERS`의 담당자에게 실제 write 권한이 있는지 확인한다. ruleset은 승인 1개를 요구하므로 1인 저장소도 별도 승인자가 필요하다.
