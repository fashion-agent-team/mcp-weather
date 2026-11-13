package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class WeatherFallbackTest {

    static class MovableClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-13T01:00:00Z"));

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(now.get(), zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    @DisplayName("Open-Meteo가 안 되면 MET Norway로, 5분 동안은 Open-Meteo를 건너뛰고 그 뒤 다시 시도")
    void fallsBackAndSkips() {
        var open = mock(OpenMeteoClient.class);
        var met = mock(MetNoClient.class);
        when(open.forecast(anyDouble(), anyDouble())).thenThrow(new ResourceAccessException("Read timed out"));
        when(met.forecast(anyDouble(), anyDouble(), any())).thenReturn(Samples.forecast());
        var clock = new MovableClock();
        var service = new WeatherService(open, met, Duration.ofMinutes(5), clock);

        assertThat(service.today(37.57, 126.98).condition()).isEqualTo("맑음");
        service.today(37.57, 126.98);
        verify(open, times(1)).forecast(anyDouble(), anyDouble());
        verify(met, times(2)).forecast(anyDouble(), anyDouble(), any());

        clock.now.set(clock.now.get().plus(Duration.ofMinutes(6)));
        service.today(37.57, 126.98);
        verify(open, times(2)).forecast(anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("Open-Meteo가 되면 MET Norway는 부르지 않는다")
    void primaryFirst() {
        var open = mock(OpenMeteoClient.class);
        var met = mock(MetNoClient.class);
        when(open.forecast(anyDouble(), anyDouble())).thenReturn(Samples.forecast());
        new WeatherService(open, met, Duration.ofMinutes(5), new MovableClock()).today(37.57, 126.98);
        verify(met, never()).forecast(anyDouble(), anyDouble(), any());
    }

    @Test
    @DisplayName("둘 다 안 되면 Open-Meteo 오류를 그대로 (도구가 upstream_timeout으로 답함)")
    void bothFail() {
        var open = mock(OpenMeteoClient.class);
        var met = mock(MetNoClient.class);
        when(open.forecast(anyDouble(), anyDouble())).thenThrow(new ResourceAccessException("Read timed out"));
        when(met.forecast(anyDouble(), anyDouble(), any())).thenThrow(new ResourceAccessException("met down"));
        assertThatThrownBy(() -> new WeatherService(open, met, Duration.ofMinutes(5), new MovableClock()).today(37.57, 126.98))
                .isInstanceOf(ResourceAccessException.class).hasMessageContaining("Read timed out");
    }
}
