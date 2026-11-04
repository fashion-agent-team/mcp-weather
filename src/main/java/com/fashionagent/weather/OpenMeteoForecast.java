package com.fashionagent.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Open-Meteo /v1/forecast 응답 중 쓰는 부분(current + daily). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenMeteoForecast(Current current, Daily daily) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Current(
            @JsonProperty("temperature_2m") double temperature,
            @JsonProperty("apparent_temperature") double apparentTemperature,
            @JsonProperty("weather_code") int weatherCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Daily(
            @JsonProperty("temperature_2m_max") List<Double> maxTemperature,
            @JsonProperty("temperature_2m_min") List<Double> minTemperature,
            @JsonProperty("precipitation_probability_max") List<Integer> precipitationProbabilityMax) {
    }
}
