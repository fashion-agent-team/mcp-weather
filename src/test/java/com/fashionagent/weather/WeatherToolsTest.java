package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

class WeatherToolsTest {

    private final WeatherService service = mock(WeatherService.class);
    private final WeatherTools tools = new WeatherTools(service);

    @Test
    @DisplayName("정상이면 WeatherToday")
    void returnsWeatherToday() {
        when(service.today(37.5665, 126.978)).thenReturn(WeatherToday.from(Samples.forecast()));
        assertThat(tools.weatherToday(37.5665, 126.978)).isInstanceOf(WeatherToday.class);
    }

    @Test
    @DisplayName("좌표가 틀리면 bad request")
    void badCoordinatesGiveBadRequest() {
        assertThat(code(tools.weatherToday(91, 0))).isEqualTo("bad_request");
        assertThat(code(tools.weatherToday(0, -181))).isEqualTo("bad_request");
    }

    @Test
    @DisplayName("시간 초과면 upstream timeout")
    void timeoutGivesUpstreamTimeout() {
        when(service.today(anyDouble(), anyDouble())).thenThrow(new ResourceAccessException("timeout"));
        assertThat(code(tools.weatherToday(37.5, 127))).isEqualTo("upstream_timeout");
    }

    @Test
    @DisplayName("서버 오류면 upstream failed")
    void serverErrorGivesUpstreamFailed() {
        when(service.today(anyDouble(), anyDouble())).thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        assertThat(code(tools.weatherToday(37.5, 127))).isEqualTo("upstream_failed");
    }

    @SuppressWarnings("unchecked")
    private static String code(Object result) {
        return ((Map<String, Map<String, String>>) result).get("error").get("code");
    }
}
