package com.fashionagent.weather;

import java.util.List;

/** weather_today 도구의 출력. 모양은 앱 레포 docs/contracts/mcp-tools.md를 따른다. */
public record WeatherToday(
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

    public static WeatherToday from(OpenMeteoForecast forecast) {
        if (forecast == null || forecast.current() == null || forecast.daily() == null) {
            throw new IllegalArgumentException("Open-Meteo 응답에 current/daily가 없습니다.");
        }
        var current = forecast.current();
        var daily = forecast.daily();
        double min = first(daily.minTemperature(), "temperature_2m_min");
        double max = first(daily.maxTemperature(), "temperature_2m_max");
        Integer rain = daily.precipitationProbabilityMax() == null || daily.precipitationProbabilityMax().isEmpty()
                ? null : daily.precipitationProbabilityMax().getFirst();
        int precipitation = rain == null ? 0 : rain;
        double range = round1(max - min);
        String condition = WeatherRules.condition(current.weatherCode());

        return new WeatherToday(
                condition,
                current.temperature(),
                current.apparentTemperature(),
                min,
                max,
                range,
                precipitation,
                WeatherRules.needsOuter(current.apparentTemperature(), range),
                WeatherRules.needsUmbrella(precipitation, condition),
                WeatherRules.clothingAdvice(current.apparentTemperature()));
    }

    private static double first(List<Double> values, String name) {
        if (values == null || values.isEmpty() || values.getFirst() == null) {
            throw new IllegalArgumentException("Open-Meteo 응답에 " + name + " 값이 없습니다.");
        }
        return values.getFirst();
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
