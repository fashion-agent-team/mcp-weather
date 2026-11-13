package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetNoClientTest {

    /** api.met.no locationforecast 2.0 complete 응답 모양(값은 만든 것). 시간은 UTC, 서울은 +9시간. */
    static final String JSON = """
            {"type":"Feature","geometry":{"type":"Point","coordinates":[126.98,37.57,30]},
             "properties":{"meta":{"updated_at":"2026-11-13T00:30:00Z"},
              "timeseries":[
               {"time":"2026-11-13T01:00:00Z","data":{
                 "instant":{"details":{"air_temperature":9.0,"relative_humidity":60.0,"wind_speed":3.0}},
                 "next_1_hours":{"summary":{"symbol_code":"partlycloudy_day"},"details":{"precipitation_amount":0.0,"probability_of_precipitation":10.0}},
                 "next_6_hours":{"summary":{"symbol_code":"cloudy"},"details":{"probability_of_precipitation":20.0}}}},
               {"time":"2026-11-13T06:00:00Z","data":{
                 "instant":{"details":{"air_temperature":14.0,"relative_humidity":45.0,"wind_speed":2.0}},
                 "next_1_hours":{"summary":{"symbol_code":"lightrainshowers_day"},"details":{"probability_of_precipitation":60.0}}}},
               {"time":"2026-11-13T14:00:00Z","data":{
                 "instant":{"details":{"air_temperature":4.0,"relative_humidity":80.0,"wind_speed":1.0}},
                 "next_6_hours":{"summary":{"symbol_code":"clearsky_night"},"details":{"probability_of_precipitation":0.0}}}},
               {"time":"2026-11-13T15:00:00Z","data":{
                 "instant":{"details":{"air_temperature":2.0,"relative_humidity":85.0,"wind_speed":1.0}},
                 "next_6_hours":{"summary":{"symbol_code":"clearsky_night"},"details":{"probability_of_precipitation":0.0}}}}
              ]}}
            """;

    private MockRestServiceServer server;
    private MetNoClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder().baseUrl("https://api.met.no").defaultHeader("User-Agent", MetNoClient.USER_AGENT);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MetNoClient(builder.build());
    }

    @Test
    @DisplayName("오늘(서울 시간) 값만 모아 Open-Meteo 모양으로 바꾼다")
    void today() {
        server.expect(requestTo(startsWith("https://api.met.no/weatherapi/locationforecast/2.0/complete")))
                .andExpect(queryParam("lat", "37.57"))
                .andExpect(header("User-Agent", startsWith("fashion-agent")))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
        var f = client.forecast(37.57, 126.98, LocalDate.of(2026, 11, 13));
        assertThat(f.current().temperature()).isEqualTo(9.0);
        assertThat(f.current().weatherCode()).isEqualTo(2);
        // 15:00Z는 서울 11월 14일 0시라 빠짐
        assertThat(f.daily().minTemperature()).containsExactly(4.0);
        assertThat(f.daily().maxTemperature()).containsExactly(14.0);
        assertThat(f.daily().precipitationProbabilityMax()).containsExactly(60);
        WeatherToday w = WeatherToday.from(f);
        assertThat(w.condition()).isEqualTo("구름 조금");
        assertThat(w.needsUmbrella()).isTrue();
        assertThat(w.apparentTemperature()).isBetween(5.0, 8.0);
    }

    @Test
    @DisplayName("날짜 하루: 가장 궂은 날씨, 체감 최저·최고")
    void daily() {
        server.expect(ExpectedCount.once(), requestTo(startsWith("https://api.met.no/")))
                .andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
        var d = WeatherDay.from(client.daily(37.57, 126.98, LocalDate.of(2026, 11, 13)));
        assertThat(d.date()).isEqualTo("2026-11-13");
        assertThat(d.condition()).isEqualTo("비");
        assertThat(d.minTemperature()).isEqualTo(4.0);
        assertThat(d.maxTemperature()).isEqualTo(14.0);
    }

    @Test
    @DisplayName("예보가 없는 날은 실패")
    void noDay() {
        server.expect(requestTo(startsWith("https://api.met.no/"))).andRespond(withSuccess(JSON, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.daily(37.57, 126.98, LocalDate.of(2026, 11, 30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("날씨 기호 → WMO 코드")
    void symbols() {
        assertThat(MetNoClient.wmo("clearsky_day")).isZero();
        assertThat(MetNoClient.wmo("fair_night")).isEqualTo(1);
        assertThat(MetNoClient.wmo("heavyrain")).isEqualTo(65);
        assertThat(MetNoClient.wmo("lightsnowshowers_day")).isEqualTo(85);
        assertThat(MetNoClient.wmo("rainandthunder")).isEqualTo(95);
        assertThat(MetNoClient.wmo("fog")).isEqualTo(45);
        assertThat(WeatherRules.condition(MetNoClient.wmo("sleet"))).isEqualTo("비");
    }

    @Test
    @DisplayName("강수 확률이 없으면(한국 좌표) 강수량으로 어림, 체감 온도는 MET 값")
    void rainFromAmount() {
        String json = """
                {"properties":{"timeseries":[
                 {"time":"2026-11-13T01:00:00Z","data":{
                   "instant":{"details":{"air_temperature":11.8,"apparent_air_temperature":11.6,"relative_humidity":83.6,"wind_speed":1.5}},
                   "next_1_hours":{"summary":{"symbol_code":"partlycloudy_day"},"details":{"precipitation_amount":0.0}},
                   "next_6_hours":{"summary":{"symbol_code":"rain"},"details":{"air_temperature_max":15.4,"air_temperature_min":11.0,"precipitation_amount":2.3}}}}
                ]}}
                """;
        server.expect(requestTo(startsWith("https://api.met.no/"))).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        var w = WeatherToday.from(client.forecast(37.57, 126.98, LocalDate.of(2026, 11, 13)));
        assertThat(w.apparentTemperature()).isEqualTo(11.6);
        assertThat(w.maxTemperature()).isEqualTo(15.4);
        assertThat(w.minTemperature()).isEqualTo(11.0);
        assertThat(w.precipitationProbability()).isEqualTo(80);
        assertThat(w.needsUmbrella()).isTrue();
    }

    @Test
    @DisplayName("실제 api.met.no 응답(로컬 파일이 있을 때만)")
    void realSample() throws Exception {
        var path = java.nio.file.Path.of(System.getProperty("metno.sample", "/tmp/claude-0/metno-real.json"));
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.exists(path));
        server.expect(requestTo(startsWith("https://api.met.no/")))
                .andRespond(withSuccess(java.nio.file.Files.readString(path), MediaType.APPLICATION_JSON));
        var w = WeatherToday.from(client.forecast(37.57, 126.98, LocalDate.of(2026, 11, 13)));
        System.out.println("REAL today " + w);
        server.reset();
        server.expect(requestTo(startsWith("https://api.met.no/")))
                .andRespond(withSuccess(java.nio.file.Files.readString(path), MediaType.APPLICATION_JSON));
        var d = WeatherDay.from(client.daily(37.57, 126.98, LocalDate.of(2026, 11, 20)));
        System.out.println("REAL 11-20 " + d);
        assertThat(w.maxTemperature()).isGreaterThanOrEqualTo(w.minTemperature());
    }
}
