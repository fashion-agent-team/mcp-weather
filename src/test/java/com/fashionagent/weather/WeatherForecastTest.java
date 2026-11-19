package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class WeatherForecastTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    /** 2026-11-09 오전 10시(서울). */
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-09T01:00:00Z"), SEOUL);
    private final WeatherService service = mock(WeatherService.class);
    private final WeatherTools tools = new WeatherTools(service, clock);

    @Test
    @DisplayName("하루 예보를 weather today와 같은 이름으로 만든다")
    void buildsDayFromDaily() {
        var day = WeatherDay.from(Samples.daily("2026-11-20"));
        assertThat(day.date()).isEqualTo("2026-11-20");
        assertThat(day.condition()).isEqualTo("비");
        assertThat(day.minTemperature()).isEqualTo(4.0);
        assertThat(day.maxTemperature()).isEqualTo(14.0);
        assertThat(day.dailyRange()).isEqualTo(10.0);
        assertThat(day.apparentTemperature()).isEqualTo(6.5);
        assertThat(day.needsOuter()).isTrue();
        assertThat(day.needsUmbrella()).isTrue();
        assertThat(day.clothingAdvice()).contains("코트");
    }

    @Test
    @DisplayName("16일 안쪽 날짜는 예보를 돌려준다")
    void returnsForecastInRange() {
        var date = LocalDate.of(2026, 11, 20);
        when(service.day(37.5, 127, date)).thenReturn(WeatherDay.from(Samples.daily("2026-11-20")));
        assertThat(tools.weatherForecast(37.5, 127, "2026-11-20")).isInstanceOf(WeatherDay.class);
        when(service.day(37.5, 127, LocalDate.of(2026, 11, 24))).thenReturn(WeatherDay.from(Samples.daily("2026-11-24")));
        assertThat(tools.weatherForecast(37.5, 127, "2026-11-24")).isInstanceOf(WeatherDay.class);
    }

    @Test
    @DisplayName("지난 날이나 16일 뒤는 out of range")
    void outOfRange() {
        assertThat(code(tools.weatherForecast(37.5, 127, "2026-11-08"))).isEqualTo("out_of_range");
        assertThat(code(tools.weatherForecast(37.5, 127, "2026-11-25"))).isEqualTo("out_of_range");
    }

    @Test
    @DisplayName("날짜 모양이 틀리면 bad request")
    void badDate() {
        assertThat(code(tools.weatherForecast(37.5, 127, "11월 20일"))).isEqualTo("bad_request");
        assertThat(code(tools.weatherForecast(37.5, 127, null))).isEqualTo("bad_request");
        assertThat(code(tools.weatherForecast(91, 127, "2026-11-20"))).isEqualTo("bad_request");
    }

    @Test
    @DisplayName("시간 초과면 upstream timeout")
    void timeout() {
        when(service.day(anyDouble(), anyDouble(), any())).thenThrow(new ResourceAccessException("timeout"));
        assertThat(code(tools.weatherForecast(37.5, 127, "2026-11-20"))).isEqualTo("upstream_timeout");
    }

    @SuppressWarnings("unchecked")
    private static String code(Object result) {
        return ((Map<String, Map<String, String>>) result).get("error").get("code");
    }
}
