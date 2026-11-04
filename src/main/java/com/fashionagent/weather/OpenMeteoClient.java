package com.fashionagent.weather;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Open-Meteo(https://open-meteo.com) 예보 API를 부른다. API 키가 필요 없다.
 * 앱의 lib/services/weather_service.dart와 같은 값을 요청한다.
 */
@Component
public class OpenMeteoClient {

    private final RestClient restClient;

    @Autowired
    public OpenMeteoClient(RestClient.Builder builder,
            @Value("${weather.open-meteo.base-url:https://api.open-meteo.com}") String baseUrl,
            @Value("${weather.open-meteo.timeout:10s}") Duration timeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /** 테스트용: 이미 설정된 RestClient를 그대로 쓴다. */
    OpenMeteoClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public OpenMeteoForecast forecast(double lat, double lon) {
        return restClient.get()
                .uri(uri -> uri.path("/v1/forecast")
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lon)
                        .queryParam("current", "temperature_2m,apparent_temperature,weather_code")
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                        .queryParam("timezone", "auto")
                        .queryParam("forecast_days", 1)
                        .build())
                .retrieve()
                .body(OpenMeteoForecast.class);
    }
}
