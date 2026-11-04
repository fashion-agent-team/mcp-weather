package com.fashionagent.weather;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

/** MCP 도구. 실패해도 예외를 던지지 않고 {"error": {"code", "message"}}를 돌려준다(약속 문서). */
@Component
public class WeatherTools {

    private static final Logger log = LoggerFactory.getLogger(WeatherTools.class);

    private final WeatherService weatherService;

    public WeatherTools(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @Tool(name = "weather_today", description = """
            사용자가 있는 곳의 오늘 날씨를 숫자로 돌려줍니다: 날씨 상태(맑음·비 등), 지금 기온, 체감 온도, \
            최저·최고 기온, 일교차, 강수 확률(%). 참고값으로 겉옷 필요 여부(needsOuter), 우산 필요 여부(needsUmbrella), \
            체감 온도별 옷차림 힌트(clothingAdvice)도 줍니다. \
            옷 추천을 시작할 때("오늘 뭐 입지?")와 사용자가 날씨를 물을 때("오늘 날씨 어때?", "우산 필요해?") 부르세요. \
            위치는 앱이 보낸 lat, lon을 그대로 넣습니다.""")
    public Object weatherToday(
            @ToolParam(description = "위도. 예: 37.5665 (서울시청)") double lat,
            @ToolParam(description = "경도. 예: 126.9780 (서울시청)") double lon) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return error("bad_request", "lat은 -90~90, lon은 -180~180이어야 해요.");
        }
        try {
            return weatherService.today(lat, lon);
        } catch (ResourceAccessException e) {
            log.warn("Open-Meteo 시간 초과 또는 연결 실패: {}", e.getMessage());
            return error("upstream_timeout", "날씨 서버가 응답하지 않아요.");
        } catch (RuntimeException e) {
            log.warn("Open-Meteo 호출 실패: {}", e.toString());
            return error("upstream_failed", "날씨 정보를 가져오지 못했어요.");
        }
    }

    static Map<String, Object> error(String code, String message) {
        return Map.of("error", Map.of("code", code, "message", message));
    }
}
