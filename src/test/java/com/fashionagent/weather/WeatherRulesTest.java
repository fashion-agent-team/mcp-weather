package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WeatherRulesTest {

    @Test
    @DisplayName("날씨 코드를 한국어로 바꾼다")
    void conditionFromWmoCode() {
        assertThat(WeatherRules.condition(0)).isEqualTo("맑음");
        assertThat(WeatherRules.condition(2)).isEqualTo("구름 조금");
        assertThat(WeatherRules.condition(3)).isEqualTo("흐림");
        assertThat(WeatherRules.condition(45)).isEqualTo("안개");
        assertThat(WeatherRules.condition(53)).isEqualTo("이슬비");
        assertThat(WeatherRules.condition(63)).isEqualTo("비");
        assertThat(WeatherRules.condition(81)).isEqualTo("비");
        assertThat(WeatherRules.condition(73)).isEqualTo("눈");
        assertThat(WeatherRules.condition(86)).isEqualTo("눈");
        assertThat(WeatherRules.condition(95)).isEqualTo("뇌우");
        assertThat(WeatherRules.condition(90)).isEqualTo("알 수 없음");
    }

    @Test
    @DisplayName("체감 온도별 옷차림 힌트")
    void clothingAdviceByApparentTemperature() {
        assertThat(WeatherRules.clothingAdvice(30)).contains("반팔과 반바지");
        assertThat(WeatherRules.clothingAdvice(23)).contains("얇은 셔츠");
        assertThat(WeatherRules.clothingAdvice(20)).contains("얇은 긴팔");
        assertThat(WeatherRules.clothingAdvice(17)).contains("맨투맨");
        assertThat(WeatherRules.clothingAdvice(12.8)).isEqualTo("자켓이나 가디건을 걸치세요.");
        assertThat(WeatherRules.clothingAdvice(10.6)).contains("트렌치코트");
        assertThat(WeatherRules.clothingAdvice(5)).contains("코트와 히트텍");
        assertThat(WeatherRules.clothingAdvice(-7)).contains("패딩");
    }

    @Test
    @DisplayName("강수 확률 50 이상이거나 비가 오면 우산")
    void umbrellaWhenRainLikely() {
        assertThat(WeatherRules.needsUmbrella(50, "맑음")).isTrue();
        assertThat(WeatherRules.needsUmbrella(49, "맑음")).isFalse();
        assertThat(WeatherRules.needsUmbrella(0, "비")).isTrue();
        assertThat(WeatherRules.needsUmbrella(0, "뇌우")).isTrue();
        assertThat(WeatherRules.needsUmbrella(10, "눈")).isFalse();
    }

    @Test
    @DisplayName("체감 20도 미만이거나 일교차 10도 이상이면 겉옷")
    void outerWhenColdOrBigRange() {
        assertThat(WeatherRules.needsOuter(19.9, 5)).isTrue();
        assertThat(WeatherRules.needsOuter(20, 5)).isFalse();
        assertThat(WeatherRules.needsOuter(25, 10)).isTrue();
        assertThat(WeatherRules.needsOuter(25, 9.6)).isTrue();   // 앱처럼 반올림해서 본다
        assertThat(WeatherRules.needsOuter(25, 9.4)).isFalse();
    }
}
