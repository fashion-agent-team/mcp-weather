package com.fashionagent.weather;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
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

    /** Open-Meteo가 주는 예보 날짜 수(오늘 포함). 그 뒤 날짜는 아직 알 수 없다. */
    static final int FORECAST_DAYS = 16;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final WeatherService weatherService;
    private final Clock clock;

    @Autowired
    public WeatherTools(WeatherService weatherService) {
        this(weatherService, Clock.system(SEOUL));
    }

    WeatherTools(WeatherService weatherService, Clock clock) {
        this.weatherService = weatherService;
        this.clock = clock;
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

    @Tool(name = "weather_forecast", description = """
            오늘이 아닌 날짜 하루의 예보를 숫자로 돌려줍니다: 날씨 상태, 하루 최저·최고 기온, 일교차, 강수 확률(%), \
            체감 온도(그날 최고·최저 체감의 가운데 값). 참고값으로 needsOuter, needsUmbrella, clothingAdvice도 줍니다. \
            "11월 20일 일정에 맞는 옷 추천해 줘", "내일 뭐 입지?", "토요일 날씨 어때?"처럼 다른 날을 말할 때 부르세요. \
            오늘부터 16일 안쪽만 알 수 있고, 그보다 먼 날이나 지난 날은 error.code=out_of_range를 돌려줍니다.""")
    public Object weatherForecast(
            @ToolParam(description = "위도. 예: 37.5665 (서울시청)") double lat,
            @ToolParam(description = "경도. 예: 126.9780 (서울시청)") double lon,
            @ToolParam(description = "날짜 YYYY-MM-DD (Asia/Seoul 기준). 예: 2026-11-20") String date) {
        if (!Double.isFinite(lat) || !Double.isFinite(lon) || lat < -90 || lat > 90 || lon < -180 || lon > 180) {
            return error("bad_request", "lat은 -90~90, lon은 -180~180이어야 해요.");
        }
        LocalDate day;
        try {
            day = LocalDate.parse(date == null ? "" : date.strip());
        } catch (DateTimeParseException e) {
            return error("bad_request", "date는 2026-11-20처럼 YYYY-MM-DD여야 해요.");
        }
        LocalDate today = LocalDate.now(clock.withZone(SEOUL));
        if (day.isBefore(today) || !day.isBefore(today.plusDays(FORECAST_DAYS))) {
            return error("out_of_range", "그날 날씨는 아직 알 수 없어요. 예보는 오늘부터 " + FORECAST_DAYS + "일 안쪽만 있어요.");
        }
        try {
            return weatherService.day(lat, lon, day);
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
