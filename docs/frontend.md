# 화면 작업 지침

Thymeleaf 화면·공용 UI 키트·관리자 인증을 바꿀 때 읽는다. 업무 기능은 [기능 변경](development.md#기능-변경), 테스트 수준은 [테스트 범위](development.md#테스트-범위), 전체 검증은 [실행과 검증](development.md#실행과-검증), 검사기 변경은 [UI 검사기](quality-maintenance.md#ui-검사기)를 따른다.

## 파일 소유

| 소유 | 경로 | 규칙 |
| --- | --- | --- |
| 키트 | `package.json`, `pnpm-lock.yaml`, `config/ui/`, `src/main/resources/static/ui/`, `src/main/resources/templates/ui/`, `src/main/resources/templates/error/`, `src/test/ui/` | 앱에서 고치지 않는다. 고치면 `UiKitManifestTest`가 빌드를 실패시킨다. [키트 갱신](#키트-갱신)으로만 받는다. |
| 앱 | `src/main/resources/static/app/`, `src/main/resources/templates/app/`, `src/main/resources/templates/admin/` | 앱에서 고친다. 동기화 스크립트가 건드리지 않는다. |

- 키트 파일에 앱 URL을 넣지 않는다. 로그인·로그아웃·메뉴 경로는 `templates/app/`과 Kotlin에만 둔다.
- 기존 URL을 유지하는 앱은 `templates/app/` 파일과 Kotlin의 경로(`AdminSecurityConfig`, `AdminWebConfig`, `WebSecurityHeadersFilter`의 `ADMIN_ROOT`)만 바꾼다. 키트 파일을 고치지 않는다.

## 화면 추가

1. 업무 동작은 [기능 변경](development.md#기능-변경)에 따라 입력 포트에 둔다.
2. 컨트롤러는 `<bc>/adapter/inbound/web/<이름>PageController.kt`에 `@Controller`로 두고 입력 포트만 호출한다.
3. 뷰 모델은 `<bc>/adapter/inbound/web/view/`에 둔다. 도메인 객체를 템플릿에 넘기지 않는다. 생성자 인자가 6개를 넘으면 새 예외를 만들지 않고 [허용 예외](code-quality.md#허용-예외)의 개별 DTO 생성자 절차를 쓴다.
4. 템플릿은 `src/main/resources/templates/admin/<이름>.html`에 두고 아래 fragment로 구성한다. 메뉴는 `templates/app/nav.html`의 `items(current)`에 추가한다.
5. 변경 요청은 `method="post"`와 `th:action`을 쓴 폼으로 보낸다. 처리 뒤 `redirect:`로 응답하고 결과는 `RedirectAttributes`의 flash 속성 `notice`, `noticeError`로 넘겨 `notice` fragment로 표시한다.
6. 입력 검증은 `@Valid`와 `BindingResult`로 받고, 업무 예외는 같은 컨트롤러의 `@ExceptionHandler`에서 받아 flash 알림으로 바꾼다.
7. `<이름>PageControllerTest`를 [HTTP 입력 어댑터 방식](development.md#테스트-범위)으로 작성하고 `pageMockMvc(컨트롤러)`와 `renderedPage(경로, 스냅샷)`으로 렌더링 결과를 검증한다. 스냅샷 이름은 테스트마다 다르게 짓는다.
8. 스냅샷은 `build/rendered-ui/<스냅샷>.html`로 저장되어 `uiHtmlCheck`의 검사 대상이 된다. `renderedPage`를 쓰지 않고 렌더링한 HTML은 `saveRenderedPage(스냅샷, html)`로 저장한다.

| `ui/layout` fragment | 용도 |
| --- | --- |
| `head(title)` | meta, CSS 링크, 보안 체인을 거친 요청의 CSRF meta |
| `shell(current, content)` | 사이드바와 본문 |
| `loginPanel(action)` | 로그인 폼과 실패·잠금·로그아웃 안내. 제출 경로는 `templates/app/login.html`이 넘긴다 |
| `notice(text, isError)` | 값이 있을 때만 알림 표시 |
| `badge(label, tone)` | 상태 배지. `tone`은 `success`, `warning`, `danger` |
| `emptyState(title, body)` | 빈 목록 |
| `errorPage(code, title, detail)` | 오류 화면 본문 |

- 키트의 `ui-` 클래스를 먼저 쓰고 앱 전용 스타일만 `static/app/app.css`에 둔다. 색·글꼴은 `var(--ui-*)`로 참조한다.
- 1280px과 390px에서 페이지에 가로 스크롤이 없어야 한다. 표는 `ui-table-wrap` 안에 둔다.
- `fetch`가 필요한 페이지는 `static/app/`의 ES 모듈에서 `/ui/api.js`의 `requestJson`, `csrfHeaders`, `problemMessage`, `ApiError`를 import한다. CSRF 읽기와 오류 변환을 페이지마다 다시 만들지 않는다.

## 토큰 덮어쓰기

- 토큰 이름은 `static/ui/tokens.css`에서 확인하고 `static/app/theme.css`의 `:root`에서 `--*`와 `color-scheme`만 덮어쓴다. `tokens.css`를 고치지 않는다.
- 색은 `light-dark(밝은 값, 어두운 값)`으로 함께 지정한다. 어두운 화면만 쓰는 앱은 `color-scheme: dark`로 지정한다.
- 글꼴은 시스템 글꼴만 쓴다. 외부 글꼴과 CDN을 쓰지 않는다.

## 검사 규칙

### Thymeleaf 원본 규칙

`ThymeleafSourceRules`가 `src/main/resources/templates/**/*.html` 원본에 적용한다. 허용 목록을 두지 않는다. `data-th-*`는 `th:*`와 같게 검사한다.

| 규칙 ID | 위반 |
| --- | --- |
| `inline-style` | `style`, `th:style` 속성, `<style>` 요소 |
| `inline-script` | `src`·`th:src` 없는 `<script>`, `th:inline` 속성 |
| `event-handler` | `on*`, `th:on*` 속성 |
| `external-asset` | `<link>`, `<script>`의 `http:`, `https:`, `//` 주소 |
| `unescaped-output` | `th:utext`, 인라인 `[(...)]` |
| `post-without-th-action` | `method="post"`인 폼에 `th:action` 없음 |
| `missing-layout` | `ui/`, `app/` 밖의 템플릿이 `ui/layout :: head`를 쓰지 않음 |

### 린트와 게이트

모두 `./gradlew build`의 `check`에 연결되고 파일을 수정하지 않는다. 검사 대상 템플릿이나 렌더링 결과가 0개이면 실패한다.

| 도구 | 대상 | 막는 것 |
| --- | --- | --- |
| ESLint | `static/**/*.js` | recommended 규칙, `eval`류, `innerHTML`·`outerHTML` 대입, `insertAdjacentHTML`, `document.write` |
| Prettier | JS, CSS, UI 설정, UI 테스트 | 포맷 위반. Thymeleaf HTML은 대상이 아니다 |
| Stylelint | `static/**/*.css` | `tokens.css`, `theme.css` 밖의 색·글꼴 직접 입력, `!important`, `@import`, 외부 `url()`. `theme.css`는 `:root`의 `--*`·`color-scheme`만 허용 |
| `node --test` | `api.js`, 린트 규칙 | 공용 JS 회귀, 각 린트 규칙이 위반을 잡는지 |
| JUnit + jsoup | `templates/**/*.html` 원본 | [Thymeleaf 원본 규칙](#thymeleaf-원본-규칙), `ui/`·`error/` 템플릿의 `/admin` 경로 |
| html-validate | `build/rendered-ui/**/*.html` | 구조 오류, label·alt 누락 |
| MockMvc | 페이지 응답 | 렌더링 실패, 로그인, CSRF, 헤더, 이스케이프 |
| JUnit | `config/ui/kit.sha256` | 키트 파일의 변경·누락·미등록 |

## 검증

```sh
./gradlew uiCheck
./gradlew pnpm_format
```

- `uiCheck`는 린트·포맷 확인·`node --test`를 실행하고, 전체 `test`로 만든 렌더링 결과를 html-validate로 검사한다.
- 포맷 위반은 `pnpm_format`으로 고치고 결과를 검토해 커밋한다. `uiCheck`는 파일을 고치지 않는다.
- Node와 pnpm은 Gradle이 `config/ui/tool-versions.properties`의 버전으로 내려받는다. 전역 Node·pnpm을 쓰지 않는다.

## 관리자 자격과 닫힘 동작

| 경로 | 자격 설정됨 | 자격 미설정 |
| --- | --- | --- |
| `/admin/login` | 공개 | 403 |
| `/admin`, `/admin/**`, `/docs/**` | 미로그인이면 `/admin/login`으로 302 | 403 |
| `/api/**`, `/actuator/**`, `/ui/**`, `/app/**` | 보안 체인 없음 | 보안 체인 없음 |

- 자격은 환경변수 `ADMIN_USERNAME`, `ADMIN_PASSWORD`로 설정한다. 배포 설정은 [최초 워크로드 생성 설정](deployment.md#최초-워크로드-생성-설정)을 따른다.
- 둘 중 하나라도 비면 앱은 정상 기동하고 위 표의 닫힌 상태가 된다. 로그인 폼도 표시하지 않는다. 기본 계정을 만들지 않는다.
- `ADMIN_PASSWORD`는 `{bcrypt}...` 해시를 권장한다. 정확히 `{bcrypt}`로 시작하는 값만 해시로 보고, 그 밖의 값은 다른 `{id}` 접두사를 포함해 모두 평문으로 보아 기동 시 해시로 바꾼다.
- 평문 `ADMIN_PASSWORD`는 UTF-8로 72바이트까지 쓴다. 넘으면 앱은 정상 기동하고 위 표의 닫힌 상태가 된다. 72바이트보다 긴 비밀값은 `{bcrypt}` 해시로 넣는다.
- 닫힌 상태로 기동하면 이유(자격 미설정, 비밀번호 길이 초과)를 WARN 로그 한 줄로 남긴다. 값과 길이는 로그에 남기지 않는다. 화면이 403이면 이 로그부터 확인한다.
- 로그인 실패는 `/admin/login?error`로 보낸다. 앱 전체 기준 5회 실패하면 10분 동안 잠그고 `/admin/login?locked`로 보낸다. 로그아웃은 `POST /admin/logout` 뒤 `/admin/login?logout`이다.
- 세션 쿠키는 `http-only`, `same-site: lax`, cookie 추적만 쓴다. `prod`에서는 `secure`를 켜고 `server.forward-headers-strategy: native`를 유지한다.

## 응답 계약

헤더 값의 원본은 `WebSecurityHeadersFilter`다. CSP를 약화하지 않는다.

| 경로 | CSP | `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`, `Permissions-Policy` | `Cache-Control: no-store`, `Pragma: no-cache` |
| --- | --- | --- | --- |
| `/api`, `/api/**`, `/actuator`, `/actuator/**` | 없음 | 없음 | 없음 |
| `/docs/**` | 없음 | 있음 | 없음 |
| `/admin`, `/admin/**` | 있음 | 있음 | 있음 |
| 그 밖의 경로(화면, 정적 파일, HTML 404) | 있음 | 있음 | 없음 |

| 요청 | 응답 |
| --- | --- |
| `Accept`에 `text/html`을 명시한 `/api/` 밖의 없는 경로 | 404, HTML `error/404` |
| `Accept`가 `*/*`·JSON인 없는 경로, `/api/`로 시작하는 없는 경로 | 404, `application/problem+json` |
| 보안 체인의 403. 유효한 세션의 CSRF 누락과 세션 쿠키 없는 요청을 포함한다 | 브라우저에 `error/403` |
| 만료된 세션 쿠키로 보낸 CSRF 실패 요청 | `/admin/login`으로 302 |
| 화면 경로의 잘못된 UUID(400), 잘못된 메서드(405) | `application/problem+json`. 앱이 만든 링크로는 발생하지 않는다 |
| 화면 처리 중 처리되지 않은 예외 | 브라우저에 `error/5xx` |

## 키트 갱신

- 템플릿 저장소에서 키트 파일을 고치면 `python3 scripts/sync-ui-kit.py --write-manifest`를 실행해 `config/ui/kit.sha256`을 다시 만든다.
- 앱에서는 `python3 scripts/sync-ui-kit.py --source <템플릿 체크아웃 경로>`로 받는다. 스크립트는 원본의 매니페스트와 파일이 일치하는지 확인한 뒤 키트 파일과 매니페스트를 복사한다. 앱의 기존 매니페스트에 있고 원본에서 사라진 파일만 지우고 지운 경로를 `removed:`로 출력한다. 기존 매니페스트가 없으면 아무것도 지우지 않는다. 앱 소유 파일은 건드리지 않는다.
- 키트 디렉터리에 매니페스트에 없는 파일이 있으면 지우지 않고 `unlisted:`로 보고하며 실패한다. 그 파일을 키트 디렉터리 밖으로 옮기거나 지운 뒤 다시 실행한다.
- `.DS_Store`는 매니페스트와 동기화 대상에서 제외한다.
- 앱에서 키트 파일을 고친 뒤 `--write-manifest`로 검사를 통과시키지 않는다.
- 받은 뒤 `./gradlew build`와 `git diff`로 확인한다.

## 알려진 한계

- 매니페스트는 Kotlin을 보호하지 않는다. 보안 설정·검사 규칙은 앱마다 패키지 이름이 달라 해시로 비교할 수 없다. `build.gradle.kts`와 `scripts/sync-ui-kit.py`도 대상이 아니다. 이 파일들의 변경은 코드 리뷰로 확인하고, 키트 갱신으로 전달되지 않으므로 템플릿의 변경을 직접 옮긴다.
- Spring Security의 기본 방화벽은 보안 체인과 무관하게 모든 요청에 적용된다. `;`, `//`, 인코딩된 `..`가 있는 비정상 URL과 `PROPFIND` 같은 표준이 아닌 HTTP 메서드는 `/api/**`에서도 400이 된다. 관리 경로의 경로 혼동 방어를 약화하지 않기 위해 유지한다.
- 실제 Traefik 뒤의 동작은 템플릿 저장소에서 확인할 수 없다. 키트를 처음 배포하는 앱에서 로그인과 폼 제출로 확인한다.

## 화면이 필요 없는 앱의 제거

`<패키지>`는 `com/example/template`처럼 앱의 패키지 경로다. 아래를 모두 실행한다.

1. 다음 파일과 디렉터리를 지운다.
   - `package.json`, `pnpm-lock.yaml`, `config/ui/`, `src/test/ui/`
   - `src/main/resources/static/`, `src/main/resources/templates/`, `src/test/resources/templates/`
   - `scripts/sync-ui-kit.py`, `scripts/tests/test_sync_ui_kit.py`
   - `src/main/kotlin/<패키지>/config/Admin*.kt`, `src/main/kotlin/<패키지>/adapter/inbound/web/`
   - `src/main/kotlin/<패키지>/todo/adapter/inbound/web/TodoPageController.kt`, `src/main/kotlin/<패키지>/todo/adapter/inbound/web/view/`
   - `src/test/kotlin/<패키지>/config/Admin*.kt`, `src/test/kotlin/<패키지>/adapter/inbound/web/`, `src/test/kotlin/<패키지>/todo/adapter/inbound/web/TodoPageControllerTest.kt`
   - `src/test/kotlin/<패키지>/support/web/PageRendering.kt`, `src/test/kotlin/<패키지>/ui/`
2. `build.gradle.kts`에서 다음을 지운다.
   - import `com.github.gradle.node.pnpm.task.PnpmInstallTask`, `com.github.gradle.node.pnpm.task.PnpmTask`, `java.util.Properties`
   - 플러그인 `com.github.node-gradle.node`
   - 의존성 `spring-boot-starter-thymeleaf`, `spring-boot-starter-security`, `spring-boot-starter-security-test`, `org.jsoup:jsoup`
   - `renderedUiDir`, `uiToolVersions`, `node { ... }`, `pnpmInstall` 설정, `uiLint`, `uiHtmlCheck`, `uiCheck`
   - `tasks.check`의 `"uiCheck"`, `tasks.withType<Test>`의 `outputs.dir(renderedUiDir)`와 `systemProperty("ui.renderedOutput", ...)`
   - `tasks.test`의 `delete(snippetsDir, renderedUiDir)`는 `delete(snippetsDir)`로 바꾸고 바로 위 주석에서 html-validate 언급을 지운다.
3. `application.yaml`에서 `app.admin`과 `server.servlet.session`을, `application-prod.yaml`에서 `server` 전체(`server.forward-headers-strategy`, `server.servlet.session`)를 지운다.
4. `.gitignore`의 `node_modules/`와 `docs/frontend.md`를 지우고, 이 문서를 가리키는 항목을 정리한다: `AGENTS.md`의 화면 변경 읽기 경로, `README.md`의 화면 항목, `docs/development.md`의 `ADMIN_*`·`uiCheck`·`build/rendered-ui/`·템플릿 적용 안내, `docs/deployment.md`의 관리자 자격, `docs/quality-maintenance.md`의 UI 검사기 절과 첫 문단의 대상, `docs/ddd-architecture.md`의 웹 어댑터·`view` 행.
5. `./gradlew build`로 확인한다.

- 제거한 앱은 화면을 추가하기 전 템플릿과 같아진다. `/docs/**`는 지금과 달리 로그인 없이 공개된다.
- `/docs/**`를 보호해야 하는 앱은 위 절차를 실행하지 않고 Security 부분을 남긴다. 로그인 화면이 Thymeleaf와 키트를 쓰므로 `config/Admin*`, `adapter/inbound/web/`, Thymeleaf·Security 의존성, `app.admin`, `server.servlet.session`, `server.forward-headers-strategy`, 키트와 `templates/app/`, `static/app/`, 대응 테스트를 유지하고 예제(`TodoPageController.kt`, `view/`, `TodoPageControllerTest.kt`, `templates/admin/`)만 지운다. `AdminWebConfig`의 `/admin` 리다이렉트 대상은 `/docs/index.html`로 바꾼다.
