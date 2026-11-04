package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

class OpenMeteoClientTest {

    private MockRestServiceServer server;
    private OpenMeteoClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://api.open-meteo.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new OpenMeteoClient(builder.build());
    }

    @Test
    @DisplayName("좌표로 Open Meteo를 호출한다")
    void callsOpenMeteoWithCoordinates() {
        server.expect(requestTo(startsWith("https://api.open-meteo.com/v1/forecast")))
                .andExpect(queryParam("latitude", "37.5"))
                .andExpect(queryParam("longitude", "127.0"))
                .andExpect(queryParam("timezone", "auto"))
                .andExpect(queryParam("forecast_days", "1"))
                .andRespond(withSuccess(Samples.JSON, MediaType.APPLICATION_JSON));

        var forecast = client.forecast(37.5, 127.0);

        server.verify();
        assertThat(forecast.current().weatherCode()).isZero();
        assertThat(WeatherToday.from(forecast).condition()).isEqualTo("맑음");
    }

    @Test
    @DisplayName("서버 오류면 예외를 던진다")
    void throwsOnServerError() {
        server.expect(requestTo(startsWith("https://api.open-meteo.com/v1/forecast"))).andRespond(withServerError());
        assertThatThrownBy(() -> client.forecast(37.5, 127.0)).isInstanceOf(HttpServerErrorException.class);
    }
}
