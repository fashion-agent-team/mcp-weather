package com.fashionagent.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Open-Meteo /v1/forecast 응답 중 날짜 하나(daily)만 쓰는 부분. weather_forecast용. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoDaily(Daily daily) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(
            List<String> time,
            @JsonProperty("weather_code") List<Integer> weatherCode,
            @JsonProperty("temperature_2m_max") List<Double> maxTemperature,
            @JsonProperty("temperature_2m_min") List<Double> minTemperature,
            @JsonProperty("apparent_temperature_max") List<Double> apparentMax,
            @JsonProperty("apparent_temperature_min") List<Double> apparentMin,
            @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax) {
    }
}
