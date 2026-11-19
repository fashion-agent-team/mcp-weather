package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

/** 실제로 서버를 띄우고 MCP 클라이언트로 /mcp에 붙어 본다(agent가 붙는 방식과 같음). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "weather.fallback.skip-for=0s")
class McpServerTest {

    @LocalServerPort
    int port;

    @MockitoBean
    OpenMeteoClient openMeteo;

    /** 대체 출처는 실제로 부르지 않는다(값 없음 = 실패). */
    @MockitoBean
    MetNoClient metNo;

    @Autowired
    CacheManager cacheManager;

    McpSyncClient client;

    @BeforeEach
    void connect() {
        cacheManager.getCache(WeatherService.CACHE).clear();
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port).endpoint("/mcp").build();
        client = McpClient.sync(transport).build();
        client.initialize();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
    }

    @Test
    @DisplayName("tools list에 weather today가 있다")
    @SuppressWarnings("unchecked")
    void toolsListHasWeatherToday() {
        var tools = client.listTools().tools();
        assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder("weather_today", "weather_forecast");
        var tool = tools.stream().filter(t -> t.name().equals("weather_today")).findFirst().orElseThrow();
        assertThat(tool.description()).contains("오늘 뭐 입지");
        assertThat((Map<String, Object>) tool.inputSchema().get("properties")).containsKeys("lat", "lon");
        assertThat((List<String>) tool.inputSchema().get("required")).contains("lat", "lon");
    }

    @Test
    @DisplayName("tools call로 오늘 날씨를 돌려주고 30분 캐시한다")
    void toolsCallReturnsWeatherAndCaches() {
        when(openMeteo.forecast(anyDouble(), anyDouble())).thenReturn(Samples.forecast());

        var first = call(37.5665, 126.9780);
        var again = call(37.5701, 126.9802); // 소수점 2자리가 같으면 같은 위치로 본다

        assertThat(first.isError()).isFalse();
        var text = ((McpSchema.TextContent) first.content().getFirst()).text();
        assertThat(text).contains("\"condition\":\"맑음\"", "\"dailyRange\":8.4", "\"needsOuter\":true",
                "\"clothingAdvice\":\"트렌치코트나 야상 같은 겉옷이 필요해요.\"");
        assertThat(again.content()).isEqualTo(first.content());
        verify(openMeteo, times(1)).forecast(37.57, 126.98);
    }

    @Test
    @DisplayName("바깥 서비스가 실패하면 error JSON")
    void upstreamFailureGivesErrorJson() {
        when(openMeteo.forecast(anyDouble(), anyDouble())).thenThrow(new IllegalStateException("boom"));
        var text = ((McpSchema.TextContent) call(10, 10).content().getFirst()).text();
        assertThat(text).contains("\"code\":\"upstream_failed\"");
    }

    @Test
    @DisplayName("tools call weather forecast는 날짜를 받는다")
    @SuppressWarnings("unchecked")
    void toolsCallForecastTakesDate() {
        var tool = client.listTools().tools().stream().filter(t -> t.name().equals("weather_forecast")).findFirst()
                .orElseThrow();
        assertThat((List<String>) tool.inputSchema().get("required")).contains("lat", "lon", "date");
        var date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(3);
        when(openMeteo.daily(37.57, 126.98, date)).thenReturn(Samples.daily(date.toString()));

        var result = client.callTool(new McpSchema.CallToolRequest("weather_forecast",
                Map.of("lat", 37.5665, "lon", 126.9780, "date", date.toString())));

        var text = ((McpSchema.TextContent) result.content().getFirst()).text();
        assertThat(text).contains("\"date\":\"" + date + "\"", "\"condition\":\"비\"", "\"needsUmbrella\":true");
    }

    @Test
    @DisplayName("actuator health가 UP")
    void actuatorHealthIsUp() {
        var body = RestClient.create("http://localhost:" + port).get().uri("/actuator/health").retrieve().body(String.class);
        assertThat(body).contains("UP");
    }

    private McpSchema.CallToolResult call(double lat, double lon) {
        return client.callTool(new McpSchema.CallToolRequest("weather_today", Map.of("lat", lat, "lon", lon)));
    }
}
