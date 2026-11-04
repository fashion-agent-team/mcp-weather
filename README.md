# mcp-weather

**weather MCP 서버 · 오늘 날씨 데이터** · 패션 에이전트(시각장애인용 음성 AI 옷 추천)의 한 부분입니다.

담당: 박창근 (임시 배정, 바꿔도 됨)

## 한눈에

- 포트: 8091, 엔드포인트: `http://mcp-weather:8091/mcp` (Streamable HTTP)
- 기술: Spring Boot, Spring AI MCP Server, Open-Meteo(API 키 없음), RestClient, Caffeine 캐시(30분)
- 상태 확인: `GET /actuator/health`

## MCP 도구

| 도구 | 하는 일 |
| --- | --- |
| `weather_today` | 오늘 기온·체감 온도·최저/최고·일교차·강수 확률·날씨 상태 (+ 날씨 묻기용 참고값 needsOuter, needsUmbrella, clothingAdvice) |

입출력 JSON은 [MCP 도구 입출력](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/mcp-tools.md)를 그대로 따릅니다.

## 이 레포가 지킬 것

- **데이터만 돌려줍니다.** "이 옷이 날씨에 맞나"는 agent가 Gemini로 판단합니다(예전 `suggest_by_weather` 같은 판단 도구는 없음).
- 같은 위치(소수점 2자리)는 30분 캐시.
- 옮길 코드: 앱 레포 `lib/services/weather_service.dart`, `lib/models/weather.dart`(날씨 코드 → 한국어 상태, 옷차림 힌트), 테스트 `test/weather_test.dart`.

## 할 일

- [ ] Spring Boot + Spring AI MCP Server 뼈대, `/mcp`, `/actuator/health`, Dockerfile
- [ ] `weather_today` (Open-Meteo 호출, 앱 코드 옮기기)
- [ ] 단위 테스트 (날씨 코드·옷차림 힌트)

## 공통 약속

모든 레포가 지키는 약속은 앱 레포에 있습니다. **코드보다 약속 문서를 먼저 고칩니다.**

- [서비스 간 약속](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/README.md): 공통 규칙, 포트, 인증(`X-User-Id`), 오류 모양, DB 스키마, 옷 추천 흐름
- [MCP 도구 입출력](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/mcp-tools.md)
- [앱 ↔ 게이트웨이 API (OpenAPI)](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/contracts/openapi.yaml)
- [아키텍처와 기술 스택](https://github.com/fashion-agent-team/fashion-agent/blob/develop/docs/ARCHITECTURE.md)

## 버전

| 항목 | 버전 |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.0.9 |
| Spring Cloud | 2025.1.4 |
| Spring AI (BOM) | 2.0.2 |

## 브랜치

GitFlow를 따릅니다. `develop`에서 `feature/…` 브랜치를 따서 PR로 합칩니다. 규칙은 앱 레포의 `docs/GITFLOW.md`를 봅니다.

## 비밀 값

API 키와 DB 비밀번호는 레포에 넣지 않습니다. 로컬은 `.env`(커밋 금지), 배포는 서버 환경 변수로 둡니다. 목록은 앱 레포 `docs/infra/.env.example`에 있습니다.
