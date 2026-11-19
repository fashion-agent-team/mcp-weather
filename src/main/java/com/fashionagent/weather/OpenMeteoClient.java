package com.fashionagent.weather;

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
     * 연결이 끊기거나(connection reset) 시간이 넘으면 이만큼 쉬고 다시 묻는다. 한 번에 기다리는 시간을 4초로 줄여
     * 세 번 물어도 13초 안팎이라 agent가 25초 안에 답할 수 있다. Open-Meteo는 보통 1초 안에 답한다.
     * 2026-11-12 시뮬: 클라우드 컨테이너에서 연결이 세 번에 두 번꼴로 끊기고, 가끔 10초 동안 답이 없었다.
     */
    static final long[] BACKOFF_MS = {200, 600};

    private final RestClient restClient;

    @Autowired
    public OpenMeteoClient(RestClient.Builder builder,
            @Value("${weather.open-meteo.base-url:https://api.open-meteo.com}") String baseUrl,
            @Value("${weather.open-meteo.timeout:3s}") Duration timeout) {
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
                // 시간 초과는 다시 묻지 않고 바로 MET Norway로 넘깁니다. 2026-11-13 젯슨: Open-Meteo가 늦을 때
                // 세 번(4초씩) 다 기다리느라 첫 옷 추천이 20초 걸렸음. 끊김(connection reset)은 빨리 끝나서 그대로 다시 묻습니다.
                if (attempt >= BACKOFF_MS.length || e.getCause() instanceof java.net.SocketTimeoutException) {
                    throw e;
                }
                log.info("Open-Meteo 연결이 끊기거나 늦어 다시 물어요 ({}번째): {}", attempt + 1, e.getMessage());
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
