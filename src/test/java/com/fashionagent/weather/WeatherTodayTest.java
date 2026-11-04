package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherTodayTest {

    @Test
    @DisplayName("Open Meteo 응답을 읽는다")
    void readsOpenMeteoResponse() {
        var w = WeatherToday.from(Samples.forecast());
        assertThat(w.condition()).isEqualTo("맑음");
        assertThat(w.temperature()).isEqualTo(12.4);
        assertThat(w.apparentTemperature()).isEqualTo(10.6);
        assertThat(w.minTemperature()).isEqualTo(7.8);
        assertThat(w.maxTemperature()).isEqualTo(16.2);
        assertThat(w.dailyRange()).isEqualTo(8.4);
        assertThat(w.precipitationProbability()).isEqualTo(10);
        assertThat(w.needsOuter()).isTrue();
        assertThat(w.needsUmbrella()).isFalse();
        assertThat(w.clothingAdvice()).contains("트렌치코트");
    }

    @Test
    @DisplayName("강수 확률이 없으면 0으로 본다")
    void missingPrecipitationIsZero() {
        assertThat(WeatherToday.from(Samples.forecast(12.4, 10.6, 0, null)).precipitationProbability()).isZero();
    }

    @Test
    @DisplayName("영하와 비 소식")
    void belowZeroAndRain() {
        var w = WeatherToday.from(Samples.forecast(-3.2, -7, 61, 80));
        assertThat(w.condition()).isEqualTo("비");
        assertThat(w.needsUmbrella()).isTrue();
        assertThat(w.clothingAdvice()).contains("패딩");
    }

    @Test
    @DisplayName("daily가 없으면 예외")
    void throwsWhenDailyMissing() {
        assertThatThrownBy(() -> WeatherToday.from(new OpenMeteoForecast(null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
