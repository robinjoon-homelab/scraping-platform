# 수집 플랫폼 도메인 조사와 설계 근거

검토일: **2026-10-05**. 공급자와 표준·제품의 공식 문서를 기준으로 조사했다. 외부 서비스의 제한과 계약은 변경될 수 있으므로 실제 커넥터를 연결할 때 다시 확인한다.

## 이번 구현의 범위

이번 단계는 **수집 출처와 작업 설정을 DB에 저장하고 관리하는 백엔드 골격**이다. 출처 생성·조회, 작업 생성·조회·수정·활성 상태 변경과 순수 실행·레코드 도메인, 커넥터 계약을 제공한다.

**실제 HTTP 요청, HTML·JSON·피드 추출, 일정 실행, 실행 이력·수집 결과·체크포인트의 영속화는 구현하지 않는다.** `enabled=true`는 설정 상태이며 백그라운드 수집을 시작하지 않는다. `HTTP_JSON`, `RSS_ATOM`, `HTML`도 출처의 기술 형식을 분류하는 값이며, 해당 수집기가 동작한다는 의미가 아니다.

현재 설정 API는 범용 형식과 길이를 검증한다. FRED의 지표 ID나 GitHub의 저장소 존재 여부, 공급자별 파라미터 의미와 실제 접근 가능 여부는 확인하지 않는다. 커넥터별 검증·미리보기는 후속 구현 범위다. 정확한 모델 규칙은 [도메인 모델](domain-model.md), 사용 방법은 [README](../README.md)를 참고한다.

## 공식 사례에서 확인한 규칙

### SEC EDGAR: 기업, 공시, 재무 수치를 구분한다

SEC의 submissions API는 앞자리 0을 포함한 10자리 CIK로 기업의 공시 이력을 조회한다. 최근 응답에는 최소 1년 또는 최근 1,000건 중 더 많은 공시가 포함되며, 추가 과거 이력은 별도 JSON 파일 목록으로 연결된다. 출처 URL 하나를 받아 한 번 저장하는 것만으로 기업의 전체 이력을 수집했다고 볼 수 없다. [SEC EDGAR APIs](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)

Company Facts는 기업별 XBRL 수치를 제공한다. 수치는 taxonomy·concept·unit·보고 기간에 따라 의미가 달라지고, 기업별 회계 기간도 같지 않을 수 있다. 따라서 기업 공시와 재무 수치를 별도 레코드 스키마로 다루고 CIK, 공시 accession number, 원천 보고 기간을 보존한다. 티커나 표시 이름만으로 영구 식별자를 만들지 않는다. [SEC Company Facts와 Frames](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)

SEC의 현재 Fair Access 한도는 **사용자 전체 합계 초당 10회 이하이며 머신 수와 무관**하다. 작업마다 10회를 허용하면 여러 작업의 합계가 정책을 위반할 수 있다. 수집 실행 시 공급자·인증 주체에 걸친 공유 제한이 필요하다. 대량 초기 적재에 쓸 수 있는 야간 벌크 파일도 제공된다. [SEC Developer Resources](https://www.sec.gov/about/developer-resources), [SEC Bulk Data](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)

### FRED·ALFRED: 과거 관측치도 수정된다

FRED는 지표의 대상 날짜인 `observation.date`와 정보가 알려져 있던 기간인 `realtime_start`·`realtime_end`를 구분한다. 과거 관측치의 값도 수정될 수 있으며, 기본 조회는 현재 알려진 정보를 보여 준다. 따라서 마지막 관측 날짜 이후만 조회하는 방식은 과거 값의 정정을 놓칠 수 있다. [FRED Real-Time Periods](https://fred.stlouisfed.org/docs/api/fred/realtime_period.html)

`series_id`, 관측 기간, 빈도, 단위 변환, 집계 방식, vintage 날짜는 수집 결과의 의미를 결정한다. 관측 날짜와 값만 저장하지 않고 이러한 의미를 식별자나 레코드 스키마에 반영해야 한다. API 키는 필요하지만 작업의 일반 `parameters`에 저장하지 않는다. [FRED Series Observations](https://fred.stlouisfed.org/docs/api/fred/series_observations.html)

### GitHub: 페이지 이동, 캐시, 요청 한도가 독립된 계약이다

GitHub 목록 API는 응답의 `Link`로 다음 페이지를 알려 준다. URL을 직접 추측하거나 첫 페이지만 조회해서 전체 목록이라고 처리하지 않는다. 페이지 크기와 안정적인 정렬은 페이지 사이의 누락·중복과 캐시 적중률에도 영향을 준다. [GitHub Pagination](https://docs.github.com/en/rest/using-the-rest-api/using-pagination-in-the-rest-api)

`ETag`·`Last-Modified`를 이용한 조건부 요청은 응답이 바뀌지 않았는지 확인한다. **인증된 조건부 요청이 304로 응답하면 기본 요청 한도를 소비하지 않는다.** 재요청에는 같은 필터·페이지 크기 등의 표현 조건을 유지해야 한다. 304는 새 데이터가 없는 정상 결과로 처리하며 오류 재시도와 구분한다. [GitHub Best Practices](https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api)

기본 한도는 비인증 요청이 출발 IP 기준 시간당 60회, 일반 사용자 인증이 시간당 5,000회다. 인증 방식·엔드포인트별 예외와 별도 제한이 있으므로 고정 숫자만으로 실행을 제어하지 않는다. `Retry-After`, `x-ratelimit-remaining`, `x-ratelimit-reset` 등 응답을 반영해야 한다. [GitHub Rate Limits](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)

### RSS·Atom: 식별자, 링크, 발표 시각을 구분한다

Atom의 `id`는 영구 식별자이며 재게시·수정 후에도 유지된다. 읽을 수 있는 URL이라고 가정하면 안 된다. `published`와 `updated`도 다르다. 특히 `updated`는 발행자가 중요하다고 판단한 변경 시점이므로 모든 내용 변경을 나타내지는 않는다. [Atom RFC 4287](https://www.rfc-editor.org/rfc/rfc4287.html)

RSS의 `guid`와 `link` 역시 항상 같지 않다. 원천 ID를 우선 사용하고, ID가 없는 피드는 커넥터가 대체 키 정책을 정의한다. 내용 해시는 변경 탐지에 쓰며 원천 식별자를 무조건 대체하지 않는다. [RSS 2.0 Specification](https://www.rssboard.org/rss-specification)

연준은 보도자료·연설·연구·통계 등 서로 다른 대상의 피드를 제공한다. 같은 기술 형식의 피드라도 수집할 대상과 범위는 별도의 설정이다. 피드의 링크·제목·요약을 저장하는 것과 링크된 본문 전체를 수집하는 것도 구분해야 한다. [Federal Reserve RSS Feeds](https://www.federalreserve.gov/feeds/feeds.htm)

### Airbyte·Scrapy·Apify: 설정, 실행, 저장 완료를 분리한다

Apify의 Task는 특정 목적에 맞게 저장한 입력 설정이다. 같은 Task로 여러 번 실행할 수 있다. 이 구분은 변경 가능한 `CollectionTask`와 한 번의 시도를 나타내는 `CollectionRun`을 나누는 근거다. 실행 시 설정 스냅샷을 고정해야 나중에 작업 설정이 바뀌어도 당시의 수집 조건을 설명할 수 있다. [Apify Actor Tasks](https://docs.apify.com/actors/running/tasks)

Scrapy의 Item Pipeline은 추출 뒤 정제·검증·중복 검사·DB 저장을 별도로 처리한다. 우리 구조에서도 네트워크 수집, 분야별 변환, 결과 저장의 책임을 구분한다. 초기에는 필요한 출력 포트만 두고 별도 처리 엔진을 미리 만들지 않는다. [Scrapy Item Pipeline](https://docs.scrapy.org/en/latest/topics/item-pipeline.html)

Airbyte의 증분 수집은 경계가 모호한 커서를 포함해 다시 읽는 것을 허용한다. 최소 한 번 전달은 중복 가능성을 전제로 한다. 수정 시 커서가 갱신되지 않거나 수집 사이에 여러 번 바뀐 데이터는 단순 증분 조회만으로 완전한 이력을 얻을 수 없다. 우리 시스템도 무조건적인 정확히 한 번 수집이나 모든 중간 변경 보존을 약속하지 않는다. [Airbyte Incremental Append](https://docs.airbyte.com/platform/using-airbyte/core-concepts/sync-modes/incremental-append)

Airbyte는 원천이 다음 상태를 내보냈다는 사실만으로 그 위치에서 재개하지 않는다. 해당 상태 앞의 레코드가 목적지에 저장되었음을 확인해야 한다. 우리 시스템에 결과 저장을 추가할 때에도 **레코드 저장과 체크포인트 전진을 같은 완료 경계로 처리**해야 한다. [Airbyte Protocol: State and Checkpointing](https://docs.airbyte.com/platform/understanding-airbyte/airbyte-protocol#state--the-whole-sync)

## Source → Task → Run → Record 모델의 근거

아래 구분은 조사 결과를 이 저장소의 요구에 적용한 설계 판단이다. 특정 제품의 API나 저장 구조를 그대로 복제하지 않는다.

| 모델 | 답하는 질문 | 경계 |
| --- | --- | --- |
| `Source` | 어디에서 가져오는가? | 출처 이름, 기술 형식, 기본 URL, 비밀 값 참조 |
| `CollectionTask` | 무엇을 어떤 주기로 가져오는가? | 경로·파라미터, 일정, 활성 상태, 설정 버전 |
| `CollectionRun` | 어느 설정으로 한 번 실행했는가? | 작업 스냅샷, 실행 상태와 시각 |
| `CollectedRecord` | 어떤 원천 항목의 어떤 내용을 관측했는가? | 원천 키·수정 해시, payload, 스키마 버전, 출처·실행·수집 시각 |

출처와 작업만 현재 DB에 저장한다. 출처는 생성 후 변경하지 않고, 작업 수정·활성 상태 변경에는 `expectedVersion`을 사용한다. 오래된 버전으로 갱신하면 충돌을 반환해 다른 수정 내용을 덮어쓰지 않는다. 새 작업은 `enabled=false`, `version=0`에서 시작한다.

실행 모델과 커넥터 계약은 작업 설정에서 독립적인 순수 Kotlin 객체다. 커넥터는 수집 요청과 이전 체크포인트를 받아 레코드 배치와 다음 체크포인트를 반환하는 경계를 갖는다. 현재는 계약만 있으며 이를 호출하는 실행 유스케이스와 저장소는 없다.

주식·경제·시사·개발을 고정된 도메인 enum으로 만들지 않는다. 수집 대상은 임의 분야로 확장될 수 있고 한 자료가 여러 분야에 속할 수도 있기 때문이다. 현재 이름과 경로·파라미터로 대상을 표현하며, 검색·분류가 필요해질 때 태그 등의 별도 기능을 추가한다.

### 공통 메타데이터와 분야별 payload

공통 모델은 출처와 수집 이력을 설명하고 분야별 의미는 payload에 보존한다. 모든 자료를 `title`, `value`, `date` 한 벌로 맞추지 않는다.

- 금융 공시: CIK, 공시 accession number, form, taxonomy·concept, unit, 보고 기간.
- 경제 관측치: series ID, 관측 날짜, vintage·real-time 기간, 값, 단위·빈도.
- 기사·피드: 원천 ID, 정식 URL, 제목·내용·작성자, 발표·수정 시각.
- 개발 자료: 저장소와 리소스 ID, release·tag·issue·commit에 맞는 개별 구조.

원천 키에는 리소스 종류와 대상 범위를 포함해 같은 출처의 다른 항목이 충돌하지 않게 한다. 향후 중복 저장 방지 키는 출처·원천 키·내용 버전의 의미를 기준으로 정한다. 같은 항목을 다른 실행에서 다시 봤다는 사실과 새 내용 버전을 발견했다는 사실은 별개다.

수집 시각은 우리 시스템이 관측한 시점이다. 발표 시각·수정 시각·보고 기간을 대신하지 않는다. 특히 경제지표의 날짜를 근거 없이 UTC 자정 시각으로 바꾸지 않는다. 현재 공통 provenance의 수집 시각과 선택적인 원천 수정 시각 외의 시간 의미는 payload에 남긴다.

원본 HTTP 응답과 정규화 레코드도 구분해야 한다. 응답 상태·최종 URL·콘텐츠 유형·본문 해시·추출기 버전을 보존하면 파서 오류를 추적하고 재처리할 수 있다. **원본 아카이브와 이 메타데이터의 영속화는 후속 설계이며 현재 레코드 모델만으로 제공되지 않는다.**

## 네 분야의 설정 예시

각 출처 JSON은 `POST /api/sources`, 작업 JSON은 `POST /api/collection-tasks`의 요청 본문이다. 먼저 출처를 생성한 뒤 작업의 예시 `sourceId`를 응답의 실제 `id`로 바꾼다. 모든 관리 API는 `Authorization: Bearer <SCRAPING_API_TOKEN>`이 필요하다.

아래 주기는 설정 형태를 설명하는 예시이며 공급자의 권장 주기나 수집 가능성을 검증한 값이 아니다. `everySeconds`는 60~31,536,000초, `anchorAt`은 UTC 기준 시각이다. 간격은 기준 시각의 위상을 유지하며 다음 시각 계산은 기준보다 엄격히 뒤의 시각을 반환한다. 지난 실행 구간을 자동 재생하지 않는다.

**API 키·토큰·비밀번호는 URL이나 `parameters`에 넣지 않는다.** `credentialRef`는 비밀 저장소의 항목 이름으로 사용할 참조 문자열이다. 현재는 참조만 저장하며 실제 비밀 조회 기능도 없다.

### 주식 관련 기업 공시: SEC

출처:

```json
{
  "name": "SEC EDGAR 기업 공시",
  "kind": "HTTP_JSON",
  "baseUrl": "https://data.sec.gov",
  "credentialRef": null
}
```

작업:

```json
{
  "name": "CIK 0000320193 공시 이력",
  "sourceId": "00000000-0000-4000-8000-000000000001",
  "target": {
    "resource": "/submissions/CIK0000320193.json",
    "parameters": {}
  },
  "schedule": {
    "everySeconds": 3600,
    "anchorAt": "2026-10-05T00:00:00Z"
  }
}
```

이는 기업 공시 대상 설정이며 주식 시세 수집기가 아니다. 실제 SEC 커넥터에는 공시별 추출, 추가 과거 이력 파일 처리, 요청자 식별과 Fair Access 제어가 필요하다. Company Facts를 모으려면 별도 작업의 경로를 `/api/xbrl/companyfacts/CIK0000320193.json`으로 구성하고 그 응답에 맞는 파서를 구현한다. [SEC API 계약](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)

### 경제지표: FRED

출처:

```json
{
  "name": "FRED 경제지표",
  "kind": "HTTP_JSON",
  "baseUrl": "https://api.stlouisfed.org",
  "credentialRef": "fred-default"
}
```

작업:

```json
{
  "name": "GNPCA 2020년 이후 관측치",
  "sourceId": "00000000-0000-4000-8000-000000000002",
  "target": {
    "resource": "/fred/series/observations",
    "parameters": {
      "series_id": "GNPCA",
      "file_type": "json",
      "observation_start": "2020-01-01",
      "units": "lin",
      "sort_order": "asc"
    }
  },
  "schedule": {
    "everySeconds": 86400,
    "anchorAt": "2026-10-05T00:00:00Z"
  }
}
```

원천 API의 `api_key`는 미래의 자격증명 어댑터가 `fred-default`를 해석해 요청 직전에 주입해야 한다. 현재 JSON에는 키가 없다. `realtime_start/end`를 생략한 위 예시는 현재 알려진 값의 조회 조건이며 전체 vintage 이력을 의미하지 않는다. 과거 수정본을 보존하려면 그 수집 정책과 레코드 키를 별도로 정한다. [FRED 요청 파라미터](https://fred.stlouisfed.org/docs/api/fred/series_observations.html)

### 개발: GitHub 공개 릴리스

출처:

```json
{
  "name": "GitHub 공개 개발 자료",
  "kind": "HTTP_JSON",
  "baseUrl": "https://api.github.com",
  "credentialRef": "github-public-reader"
}
```

작업:

```json
{
  "name": "Kubernetes 릴리스",
  "sourceId": "00000000-0000-4000-8000-000000000003",
  "target": {
    "resource": "/repos/kubernetes/kubernetes/releases",
    "parameters": {
      "per_page": "100"
    }
  },
  "schedule": {
    "everySeconds": 3600,
    "anchorAt": "2026-10-05T00:00:00Z"
  }
}
```

실제 커넥터는 다음 페이지와 조건부 요청 상태를 처리해야 한다. 릴리스 목록은 모든 Git tag 목록과 같지 않으며, 접근 토큰의 권한에 따라 보이는 자료도 달라질 수 있다. `per_page=100`은 전체 수집 완료를 뜻하지 않는다. [GitHub Releases API](https://docs.github.com/en/rest/releases/releases#list-releases)

### 시사·정책 발표: 연준 RSS

출처:

```json
{
  "name": "연준 공식 발표 피드",
  "kind": "RSS_ATOM",
  "baseUrl": "https://www.federalreserve.gov",
  "credentialRef": null
}
```

작업:

```json
{
  "name": "연준 전체 보도자료",
  "sourceId": "00000000-0000-4000-8000-000000000004",
  "target": {
    "resource": "/feeds/press_all.xml",
    "parameters": {}
  },
  "schedule": {
    "everySeconds": 1800,
    "anchorAt": "2026-10-05T00:00:00Z"
  }
}
```

같은 형태로 다른 분야의 RSS·Atom 대상도 표현할 수 있다. 다만 피드 등록만으로 링크된 본문을 따라가거나 과거 모든 기사를 얻지는 않는다. 전체 기사 수집과 과거 적재는 별도 범위·접근 정책이 필요하다. [연준 공식 피드 목록](https://www.federalreserve.gov/feeds/feeds.htm)

## 실제 수집기를 추가할 때의 완료 조건

다음 항목은 현재 제공 기능이 아니라 실행 계층을 구현할 때 확인할 요구사항이다.

### 진행 상태와 데이터 일관성

1. 공급자가 정의한 다음 페이지·커서를 따르고, 커서 만료와 페이지 사이 자료 이동을 다룬다. HTTP 캐시 검증자와 데이터 증분 커서는 별도 상태다.
2. 원천 변경 특성에 맞춰 겹쳐 읽는 기간, 정기 전체 재조회, 과거 적재 범위를 정한다. 최신 대상 날짜만으로 FRED 같은 수정 가능한 자료를 증분 처리하지 않는다.
3. 배치의 레코드 저장과 체크포인트 전진을 하나의 원자적 완료 경계로 묶는다. 중간 실패 시 롤백과 재시도 후 중복 방지를 통합 테스트한다.
4. 작업 선점과 실행 생성을 원자적으로 처리한다. 중복 스케줄 실행, 프로세스 중단, 오래된 실행 복구, 재시도 횟수와 지연을 명시한다.
5. 파서·payload 스키마 버전을 남기고, 유효하지 않은 레코드를 실패·격리·부분 성공 중 어떤 방식으로 다룰지 정한다. 오류를 무시한 채 성공 처리하지 않는다.

### 요청 제어와 출처 추적

공급자와 인증 주체가 공유하는 요청 한도, 동시 요청 수, timeout, 응답·압축 해제 크기 제한을 둔다. 429와 공급자별 제한 응답은 `Retry-After` 등을 따르고 일시적 오류에만 제한된 재시도를 적용한다. 고정 간격만으로 외부 요청 한도를 지켰다고 판단하지 않는다.

원본 응답·최종 URL·수집 시각·콘텐츠 해시·파서 버전을 연결하되 Authorization, API 키가 포함된 쿼리와 쿠키는 아카이브·로그에서 제거한다. 원본 보존 기간과 삭제 정책은 실제 자료의 사용 조건과 저장량에 맞춰 정한다.

### URL 수집의 보안 경계

관리 API의 URL 형식 검증은 외부 요청의 안전성을 보장하지 않는다. 커넥터 구현 시 허용 호스트와 프로토콜을 정하고, loopback·사설·link-local·클라우드 메타데이터 주소 접근을 차단한다. IPv4와 IPv6를 모두 검사하며 DNS 검증 후 다른 주소로 연결되는 rebinding을 막아야 한다. 리디렉션은 자동 추적을 끄거나 각 목적지에 같은 검증을 적용한다. [OWASP SSRF Prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html)

일반 웹 크롤러는 robots 정책을 처리해야 한다. RFC 9309에서 robots 파일이 서버·네트워크 오류로 불가용하면 전체 금지로 가정하며, 캐시는 일반적으로 24시간을 넘겨 사용하지 않는다. **robots 허용은 접근 권한이나 재사용 허가를 부여하지 않는다.** 출처의 API 정책·접근 권한·자료 사용 조건은 별도로 확인한다. [RFC 9309 Robots Exclusion Protocol](https://www.rfc-editor.org/rfc/rfc9309.html)

## 다음 구현 단위

첫 실행 경로는 허용한 공급자의 읽기 전용 커넥터 하나로 제한한다. 수동 실행, 실행 이력, 배치 결과·체크포인트의 원자적 저장과 실패 복구를 먼저 검증한 뒤 주기 실행을 연결한다. 브라우저 렌더링, 임의 스크립트·추출 DSL, 분산 큐, 분야별 통합 데이터마트는 실제 필요가 확인된 뒤 추가한다.
