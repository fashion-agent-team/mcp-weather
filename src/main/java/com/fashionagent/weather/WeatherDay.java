package com.fashionagent.weather;

import java.util.List;

/**
 * weather_forecast 도구의 출력. agent가 weather_today와 같은 자리에서 읽도록 이름을 맞췄다.
 * 그날의 "지금 기온"은 없으니 temperature·apparentTemperature는 하루 최고·최저의 가운데 값이다.
 */
public record WeatherDay(
        String date,
        String condition,
        double temperature,
        double apparentTemperature,
        double minTemperature,
        double maxTemperature,
        double dailyRange,
        int precipitationProbability,
        boolean needsOuter,
        boolean needsUmbrella,
        String clothingAdvice) {

    public static WeatherDay from(OpenMeteoDaily forecast) {
        if (forecast == null || forecast.daily() == null) {
            throw new IllegalArgumentException("Open-Meteo 응답에 daily가 없습니다.");
        }
        var d = forecast.daily();
        String date = d.time() == null || d.time().isEmpty() ? null : d.time().getFirst();
        double min = first(d.minTemperature(), "temperature_2m_min");
        double max = first(d.maxTemperature(), "temperature_2m_max");
        double apparentMin = firstOr(d.apparentMin(), min);
        double apparentMax = firstOr(d.apparentMax(), max);
        Integer code = d.weatherCode() == null || d.weatherCode().isEmpty() ? null : d.weatherCode().getFirst();
        Integer rain = d.precipitationProbabilityMax() == null || d.precipitationProbabilityMax().isEmpty()
                ? null : d.precipitationProbabilityMax().getFirst();
        int precipitation = rain == null ? 0 : rain;
        double range = round1(max - min);
        double apparent = round1((apparentMin + apparentMax) / 2);
        String condition = code == null ? "알 수 없음" : WeatherRules.condition(code);

        return new WeatherDay(
                date,
                condition,
                round1((min + max) / 2),
                apparent,
                min,
                max,
                range,
                precipitation,
                WeatherRules.needsOuter(apparent, range),
                WeatherRules.needsUmbrella(precipitation, condition),
                WeatherRules.clothingAdvice(apparent));
    }

    private static double first(List<Double> values, String name) {
        if (values == null || values.isEmpty() || values.getFirst() == null) {
            throw new IllegalArgumentException("Open-Meteo 응답에 " + name + " 값이 없습니다.");
        }
        return values.getFirst();
    }

    private static double firstOr(List<Double> values, double fallback) {
        return values == null || values.isEmpty() || values.getFirst() == null ? fallback : values.getFirst();
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
