# Kotlin · Spring Boot · Exposed 템플릿

## 작업 시작

- 에이전트는 [AGENTS.md](AGENTS.md)에서 작업에 필요한 절을 선택한다.
- 실행은 [개발 지침](docs/development.md#실행과-검증), 새 저장소는 [템플릿 적용](docs/development.md#템플릿-적용)을 따른다.
- 검토자는 변경된 동작·선택 근거·검증 결과·남은 제약을 코드와 테스트에서 확인한다.

## 사용 범위

- 인증·인가·사용자별 데이터 분리를 구현하기 전에는 실제 사용자 데이터를 취급하지 않는다. 공개 예제에는 샘플 데이터만 사용한다.

## 작업별 원본

- [아키텍처](docs/ddd-architecture.md): BC·어그리게이트 선정과 계층·트랜잭션 규칙.
- [코드 품질](docs/code-quality.md): 수치 제한·예외·금지 사항.
- [개발 절차](docs/development.md): 기능·API·DB 변경, 테스트 범위와 검증.
- [화면](docs/frontend.md): 파일 소유·화면 추가·UI 검사·관리자 자격·키트 갱신·화면 제거.
- [배포](docs/deployment.md): 공용 파일 유지·앱별 설정·실행·확인.
- [검사기 유지보수](docs/quality-maintenance.md): 검사 규칙·빌드 연결을 바꿀 때만 참조.
