package com.fashionagent.weather;

import java.util.Collections;
import java.util.List;

/** 앱 test/weather_test.dart의 sampleResponse와 같은 값. */
final class Samples {

    private Samples() {
    }

    static OpenMeteoForecast forecast(double temperature, double apparent, int code, Integer rain) {
        return new OpenMeteoForecast(
                new OpenMeteoForecast.Current(temperature, apparent, code),
                new OpenMeteoForecast.Daily(List.of(16.2), List.of(7.8), Collections.singletonList(rain)));
    }

    static OpenMeteoForecast forecast() {
        return forecast(12.4, 10.6, 0, 10);
    }

    static OpenMeteoDaily daily(String date) {
        return new OpenMeteoDaily(new OpenMeteoDaily.Daily(List.of(date), List.of(61), List.of(14.0), List.of(4.0),
                List.of(12.0), List.of(1.0), List.of(80)));
    }

    static final String JSON = """
            {
              "latitude": 37.56, "longitude": 126.98,
              "current": { "time": "2026-11-04T14:00", "temperature_2m": 12.4, "apparent_temperature": 10.6, "weather_code": 0 },
              "daily": { "time": ["2026-11-04"], "temperature_2m_max": [16.2], "temperature_2m_min": [7.8],
                         "precipitation_probability_max": [10] }
            }
            """;
}
