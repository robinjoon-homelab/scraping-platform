# Scraping Platform

## 작업 시작

- 에이전트는 [AGENTS.md](AGENTS.md)에서 작업에 필요한 절을 선택한다.
- 실행은 [개발 지침](docs/development.md#실행과-검증), 새 저장소는 [템플릿 적용](docs/development.md#템플릿-적용)을 따른다.
- 검토자는 변경된 동작·선택 근거·검증 결과·남은 제약을 코드와 테스트에서 확인한다.

## 사용 범위

- 출처·수집 계획의 생성·조회·수정과 실행·레코드의 도메인 규칙을 제공한다. 실제 커넥터·주기 워커·실행 이력·수집 결과 저장은 구현하지 않았으며 `enabled=true`도 수집을 시작하지 않는다.
- `/api`는 `SCRAPING_API_TOKEN` Bearer 인증을 사용하는 단일 운영자용이다. 사용자별 계정·데이터 분리는 제공하지 않는다. 다중 사용자나 공개 이용을 추가하기 전에 요구에 맞는 인증·인가·데이터 범위를 구현한다.
- URL·일반 매개변수에 비밀값을 넣지 않는다. `credentialRef`에는 참조 이름만 저장하며 현재 비밀 조회 기능은 없다.

## 작업별 원본

- [아키텍처](docs/ddd-architecture.md): BC·어그리게이트 선정과 계층·트랜잭션 규칙.
- [코드 품질](docs/code-quality.md): 수치 제한·예외·금지 사항.
- [개발 절차](docs/development.md): 기능·API·DB 변경, 테스트 범위와 검증.
- [배포](docs/deployment.md): 공용 파일 유지·앱별 설정·실행·확인.
- [검사기 유지보수](docs/quality-maintenance.md): 검사 규칙·빌드 연결을 바꿀 때만 참조.
