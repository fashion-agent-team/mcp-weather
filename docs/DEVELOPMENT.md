# 개발 방법

## 필요한 것

- Java 21 (Gradle은 `./gradlew`가 받아 옵니다)
- API 키 없음. Open-Meteo는 키 없이 씁니다.

## 실행

```bash
./gradlew bootRun          # http://localhost:8091
./gradlew test             # 단위 테스트 + 실제 /mcp 접속 테스트
```

- MCP 엔드포인트: `http://localhost:8091/mcp` (Streamable HTTP)
- 상태 확인: `curl localhost:8091/actuator/health`

## 손으로 불러 보기

MCP Inspector로 붙는 게 가장 쉽습니다.

```bash
npx @modelcontextprotocol/inspector
# Transport: Streamable HTTP, URL: http://localhost:8091/mcp
# Tools → weather_today → lat 37.5665, lon 126.9780
```

## Docker

```bash
docker build -t ghcr.io/fashion-agent-team/mcp-weather:dev .
docker run --rm -p 8091:8091 ghcr.io/fashion-agent-team/mcp-weather:dev
```

## 코드 구성

| 파일 | 하는 일 |
| --- | --- |
| `WeatherTools` | MCP 도구 `weather_today` (`@Tool`). 실패하면 `{"error": {"code", "message"}}` |
| `WeatherService` | 좌표를 소수점 2자리로 줄이고 30분 캐시(Caffeine) |
| `OpenMeteoClient` | Open-Meteo 호출(RestClient, 10초 제한) |
| `WeatherToday` | 출력 모양(앱 레포 `docs/contracts/mcp-tools.md`) |
| `WeatherRules` | 날씨 코드 → 한국어, 옷차림 힌트, 겉옷·우산 기준 (앱 `lib/models/weather.dart`, `lib/styling/weather_rules.dart`에서 옮김) |

## 설정 (application.yml)

| 값 | 기본 | 설명 |
| --- | --- | --- |
| `server.port` | 8091 | 약속 문서의 포트 |
| `weather.open-meteo.base-url` | https://api.open-meteo.com | 테스트나 프록시용으로 바꿀 수 있음 |
| `weather.open-meteo.timeout` | 10s | 연결·읽기 제한 |
| `spring.cache.caffeine.spec` | maximumSize=1000,expireAfterWrite=30m | 캐시 |
