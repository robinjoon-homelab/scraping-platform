# 배포 작업 지침

## 공용 파일 사용

- 모든 앱에서 [.github/workflows/ci.yml](../.github/workflows/ci.yml), [scripts/deploy.py](../scripts/deploy.py), `scripts/tests`, [Dockerfile](../Dockerfile), `.dockerignore`, Gradle 빌드 구성을 공통으로 유지한다.
- 앱별 값은 아래 변수로 지정한다. 기능 추가를 위해 공용 배포 파일을 개별 수정하지 않는다.
- 기본 구성을 사용할 때는 `.github/deployment.json`을 만들지 않는다.
- CI 변경 시 PR 소스 커밋 검사를 유지한다. 신뢰하지 않는 PR에 배포 자격 증명을 제공하거나 `pull_request_target`으로 실행하지 않는다.

## 배포 전 설정

- 서비스 저장소의 **Template repository 설정을 끈다**.
- 플랫폼의 SMS에 저장소·`.github/workflows/ci.yml`·사용할 `refs/heads/main` 또는 `refs/heads/master`가 등록되어 있고 `zot`·`harness` 자격 증명을 받을 수 있는 상태에서만 배포한다.
- **첫 `main`·`master` 푸시 전에** 배포 변수를 설정한다. 두 브랜치 중 운영 기본 브랜치를 하나만 사용한다.
- 자격 증명을 코드·Variables에 넣지 않는다. SMS 거절은 등록·권한 설정을 바로잡고 저장소 Secrets로 우회하지 않는다.

## 배포 이름과 이미지 설정

GitHub Actions Variables에 다음 값을 설정한다.

| 변수 | 설정할 값 |
| --- | --- |
| `HOMELAB_APP_NAME` | `scraping-platform`. 소문자 영숫자·하이픈 1~50자, 시작·끝은 영숫자. |
| `HOMELAB_REGISTRY_HOST` | `registry.homelab.robinjoon.xyz`. 스킴·경로·포트 없는 호스트. 점이 있는 주소 또는 `localhost`. |
| `HOMELAB_REGISTRY_IMAGE` | `apps/scraping-platform`. 호스트·태그를 제외한 이미지 경로. |

- 기존 앱은 하네스의 앱 이름·이미지 repository를 사용한다. 저장소 이름·ID로 대상을 추측하거나 불일치를 앱 삭제·검사 우회로 해결하지 않는다.

## 컨테이너와 DB 계약

- `build/libs/application.jar`, JRE 25, 컨테이너 포트 **8080**, 비루트 UID/GID `10001`, `linux/amd64`를 유지한다.
- `prod`에서 PostgreSQL을 사용하고 `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`를 설정한다. H2로 대체하지 않는다.
- 앱 JVM에 힙·OOM 옵션을 임의로 추가하거나 하네스 메모리 제한을 바꾸지 않는다.
- Kubernetes 프로브 연결을 보고할 때는 실제 워크로드 설정을 확인한다.
- [접근 제어](../README.md#사용-범위)를 구현하기 전에는 공개 앱에 실제 사용자 데이터를 넣지 않는다.

## 배포 없이 검증

- 공용 배포 파일을 변경하면 다음 검증을 수행한다. 로컬 검증에서 `scripts/deploy.py apply`를 실행하지 않는다.

```sh
APP_NAME=scraping-platform REGISTRY_HOST=registry.homelab.robinjoon.xyz \
  REGISTRY_IMAGE=apps/scraping-platform python3 scripts/deploy.py plan
python3 -m unittest discover -s scripts/tests -v
./gradlew build
docker build -t scraping-platform:local .
```

## 배포 실행과 확인

- 검증만 필요하면 PR을 사용한다. 실제 배포에는 서비스 저장소의 `main`·`master` 푸시 또는 해당 브랜치의 수동 실행을 사용한다.
- 워크플로가 만든 이미지 태그를 사용하고 `latest`나 임의 태그로 바꾸지 않는다.
- 배포 단계가 건너뛰어지지 않았는지, 하네스의 이미지 태그가 대상 커밋과 일치하는지, 실제 앱 URL과 `/actuator/health` 응답이 정상인지 확인한다. 태그 반영만 확인했다면 정상 가동으로 보고하지 않는다.
- 실패 후 재실행하기 전에 하네스 실행과 현재 태그를 확인한다. 원격 작업 중단이나 자동 롤백을 가정하지 않는다.

## 최초 워크로드 생성 설정

- DB·도메인·환경변수·ServiceAccount의 변경 요구가 있을 때만 `.github/deployment.json`을 작성한다. [deploy.py](../scripts/deploy.py)의 `identity`·`default_environment`와 위 `plan` 출력에서 기본값을 확인한다.
- 허용 필드는 `database`, `host`, `env`, `serviceAccountName`이다. **최초 생성에만 적용**하며 기존 앱의 설정 변경은 하네스에서 처리한다. 앱 이름을 바꿔 데이터 이전을 대신하지 않는다.
- `env`에는 **교체할 전체 목록**을 쓰고 prod·DB 설정을 포함한다. URL의 DB명은 `database`와 맞추며, `DB_HOST`는 설정하지 않고 `DB_PORT`는 이를 참조하는 URL보다 먼저 선언한다.
- 비밀값은 `secretKeyRef`로 지정한다. 참조할 Secret·ServiceAccount를 먼저 준비하고 `plan`으로 최종 설정을 확인한다.
- 이 앱은 [deployment.json](../.github/deployment.json)으로 DB `scraping_platform`·도메인 `scraping-platform.homelab.robinjoon.xyz`·전체 prod/DB 환경변수와 `SCRAPING_API_TOKEN`을 지정한다. 워크로드 namespace의 Secret `scraping-platform-runtime`, key `api-token`을 준비한다. helper는 비밀값을 조회·생성하지 않는다. 기존 워크로드에는 이 파일이 재적용되지 않으며 토큰 미설정 API는 503으로 닫힌다.
