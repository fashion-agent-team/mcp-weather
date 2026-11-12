package com.fashionagent.weather;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Open-Meteo(https://open-meteo.com) 예보 API를 부른다. API 키가 필요 없다.
 * 앱의 lib/services/weather_service.dart와 같은 값을 요청한다.
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    /**
     * 연결이 중간에 끊기면(connection reset 등) 이만큼 쉬고 다시 묻는다. 시간 초과는 다시 묻지 않는다(agent가 25초 안에 답해야 함).
     * 2026-11-12 시뮬: 클라우드 컨테이너에서 Open-Meteo 연결이 세 번에 두 번꼴로 끊겼다.
     */
    static final long[] BACKOFF_MS = {200, 600};

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
        return withRetry(() -> restClient.get()
                .uri(uri -> uri.path("/v1/forecast")
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lon)
                        .queryParam("current", "temperature_2m,apparent_temperature,weather_code")
                        .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_probability_max")
                        .queryParam("timezone", "auto")
                        .queryParam("forecast_days", 1)
                        .build())
                .retrieve()
                .body(OpenMeteoForecast.class));
    }

    /** 날짜 하루의 예보(daily). Open-Meteo는 오늘부터 16일 안쪽만 준다. */
    public OpenMeteoDaily daily(double lat, double lon, LocalDate date) {
        return withRetry(() -> restClient.get()
                .uri(uri -> uri.path("/v1/forecast")
                        .queryParam("latitude", lat)
                        .queryParam("longitude", lon)
                        .queryParam("daily", "weather_code,temperature_2m_max,temperature_2m_min,"
                                + "apparent_temperature_max,apparent_temperature_min,precipitation_probability_max")
                        .queryParam("timezone", "Asia/Seoul")
                        .queryParam("start_date", date.toString())
                        .queryParam("end_date", date.toString())
                        .build())
                .retrieve()
                .body(OpenMeteoDaily.class));
    }

    static <T> T withRetry(Supplier<T> call) {
        for (int attempt = 0; ; attempt++) {
            try {
                return call.get();
            } catch (ResourceAccessException e) {
                if (attempt >= BACKOFF_MS.length || e.getCause() instanceof SocketTimeoutException) {
                    throw e;
                }
                log.info("Open-Meteo 연결이 끊겨 다시 물어요 ({}번째): {}", attempt + 1, e.getMessage());
                try {
                    Thread.sleep(BACKOFF_MS[attempt]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
