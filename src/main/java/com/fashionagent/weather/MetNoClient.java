package com.fashionagent.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 노르웨이 기상청 MET Norway Locationforecast 2.0(https://api.met.no, 키 없음, CC BY 4.0). Open-Meteo가 안 될 때만 쓴다.
 * 2026-11-13 api.open-meteo.com(51.81.167.44)이 젯슨·맥·클라우드 어디서나 연결 시간 초과였고, api.met.no는 젯슨에서 닿았다.
 *
 * <p>응답을 Open-Meteo 모양(OpenMeteoForecast·OpenMeteoDaily)으로 바꿔서 WeatherToday·WeatherDay를 그대로 쓴다.
 * 체감 온도는 Open-Meteo와 같은 Steadman 식(기온·습도·바람)으로 계산한다. 이용 약관상 User-Agent에 앱 이름과 주소를 넣는다.
 */
@Component
public class MetNoClient {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final String USER_AGENT = "fashion-agent-mcp-weather/0.1 github.com/fashion-agent-team/mcp-weather";

    private final RestClient restClient;

    @Autowired
    public MetNoClient(RestClient.Builder builder,
            @Value("${weather.met-no.base-url:https://api.met.no}") String baseUrl,
            @Value("${weather.met-no.timeout:4s}") Duration timeout) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.restClient = builder.clone().baseUrl(baseUrl).requestFactory(requestFactory)
                .defaultHeader("User-Agent", USER_AGENT).build();
    }

    /** 테스트용. */
    MetNoClient(RestClient restClient) {
        this.restClient = restClient;
    }

    MetNoResponse fetch(double lat, double lon) {
        return restClient.get()
                .uri(uri -> uri.path("/weatherapi/locationforecast/2.0/complete")
                        .queryParam("lat", String.format(Locale.ROOT, "%.2f", lat))
                        .queryParam("lon", String.format(Locale.ROOT, "%.2f", lon))
                        .build())
                .retrieve()
                .body(MetNoResponse.class);
    }

    /** 지금 날씨 + 오늘(서울 시간) 하루. */
    public OpenMeteoForecast forecast(double lat, double lon, LocalDate today) {
        MetNoResponse r = fetch(lat, lon);
        List<MetNoResponse.Step> steps = steps(r);
        MetNoResponse.Step now = steps.getFirst();
        var d = now.data().instant().details();
        double t = d.airTemperature();
        Day day = day(steps, today);
        var current = new OpenMeteoForecast.Current(t, round1(apparent(t, d.relativeHumidity(), d.windSpeed())),
                wmo(symbol(now)));
        var daily = new OpenMeteoForecast.Daily(List.of(day.max), List.of(day.min), List.of(day.rain));
        return new OpenMeteoForecast(current, daily);
    }

    /** 날짜 하루(서울 시간). 예보가 없는 날(보통 9~10일 뒤)이면 IllegalArgumentException. */
    public OpenMeteoDaily daily(double lat, double lon, LocalDate date) {
        Day day = day(steps(fetch(lat, lon)), date);
        return new OpenMeteoDaily(new OpenMeteoDaily.Daily(List.of(date.toString()), List.of(day.code),
                List.of(day.max), List.of(day.min), List.of(day.apparentMax), List.of(day.apparentMin), List.of(day.rain)));
    }

    private static List<MetNoResponse.Step> steps(MetNoResponse r) {
        if (r == null || r.properties() == null || r.properties().timeseries() == null
                || r.properties().timeseries().isEmpty()) {
            throw new IllegalArgumentException("MET Norway 응답에 timeseries가 없습니다.");
        }
        return r.properties().timeseries();
    }

    record Day(double min, double max, double apparentMin, double apparentMax, int rain, int code) {
    }

    static Day day(List<MetNoResponse.Step> steps, LocalDate date) {
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, aMin = Double.MAX_VALUE, aMax = -Double.MAX_VALUE;
        int rain = 0;
        int code = -1;
        int found = 0;
        for (MetNoResponse.Step s : steps) {
            if (s.time() == null || s.data() == null || s.data().instant() == null) {
                continue;
            }
            if (!OffsetDateTime.parse(s.time()).atZoneSameInstant(SEOUL).toLocalDate().equals(date)) {
                continue;
            }
            var d = s.data().instant().details();
            if (d == null || d.airTemperature() == null) {
                continue;
            }
            found++;
            double t = d.airTemperature();
            double a = apparent(t, d.relativeHumidity(), d.windSpeed());
            min = Math.min(min, t);
            max = Math.max(max, t);
            aMin = Math.min(aMin, a);
            aMax = Math.max(aMax, a);
            for (var p : java.util.Arrays.asList(s.data().next1Hours(), s.data().next6Hours())) {
                if (p != null && p.details() != null && p.details().probabilityOfPrecipitation() != null) {
                    rain = Math.max(rain, (int) Math.round(p.details().probabilityOfPrecipitation()));
                }
            }
            code = Math.max(code, wmo(symbol(s)));
        }
        if (found == 0) {
            throw new IllegalArgumentException("MET Norway 예보에 " + date + "이(가) 없습니다.");
        }
        return new Day(min, max, round1(aMin), round1(aMax), rain, Math.max(code, 0));
    }

    /** 다음 1시간 날씨 기호, 없으면 6시간, 12시간. */
    static String symbol(MetNoResponse.Step s) {
        for (var p : new ArrayList<>(java.util.Arrays.asList(s.data().next1Hours(), s.data().next6Hours(),
                s.data().next12Hours()))) {
            if (p != null && p.summary() != null && p.summary().symbolCode() != null) {
                return p.summary().symbolCode();
            }
        }
        return null;
    }

    /** MET 날씨 기호(clearsky_day, lightrainshowers_night 등) → WMO 코드. 하루에서는 가장 큰 코드(가장 궂은 날씨)를 쓴다. */
    static int wmo(String symbol) {
        if (symbol == null) {
            return 3;
        }
        String s = symbol.replaceAll("_(day|night|polartwilight)$", "");
        if (s.contains("thunder")) return 95;
        if (s.contains("sleet")) return 66;
        if (s.contains("snow")) return s.contains("showers") ? 85 : s.startsWith("heavy") ? 75 : s.startsWith("light") ? 71 : 73;
        if (s.contains("rain")) return s.contains("showers") ? 80 : s.startsWith("heavy") ? 65 : s.startsWith("light") ? 61 : 63;
        return switch (s) {
            case "clearsky" -> 0;
            case "fair" -> 1;
            case "partlycloudy" -> 2;
            case "fog" -> 45;
            default -> 3;
        };
    }

    /** Open-Meteo와 같은 Steadman 체감 온도: Ta + 0.33e − 0.70ws − 4.00 (e: 수증기압 hPa, ws: m/s). 습도·바람이 없으면 기온. */
    static double apparent(double t, Double humidity, Double wind) {
        if (humidity == null || wind == null) {
            return t;
        }
        double e = humidity / 100 * 6.105 * Math.exp(17.27 * t / (237.7 + t));
        return t + 0.33 * e - 0.70 * wind - 4.00;
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MetNoResponse(Properties properties) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record Properties(List<Step> timeseries) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Step(String time, Data data) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Data(Instant instant, @JsonProperty("next_1_hours") Period next1Hours,
                @JsonProperty("next_6_hours") Period next6Hours, @JsonProperty("next_12_hours") Period next12Hours) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Instant(InstantDetails details) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record InstantDetails(@JsonProperty("air_temperature") Double airTemperature,
                @JsonProperty("relative_humidity") Double relativeHumidity,
                @JsonProperty("wind_speed") Double windSpeed) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Period(Summary summary, PeriodDetails details) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record Summary(@JsonProperty("symbol_code") String symbolCode) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        record PeriodDetails(@JsonProperty("probability_of_precipitation") Double probabilityOfPrecipitation) {
        }
    }
}
