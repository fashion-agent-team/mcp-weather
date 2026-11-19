package com.fashionagent.weather;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 오늘 날씨. 같은 위치(소수점 2자리, 약 1km)는 30분 캐시한다.
 *
 * <p>Open-Meteo가 안 되면 MET Norway로 넘어간다. Open-Meteo가 한 번 실패하면 {@code skipFor} 동안은 바로 MET Norway를 쓴다
 * (2026-11-13 Open-Meteo가 연결 시간 초과라 요청마다 세 번 다시 묻느라 13초씩 걸려 첫 추천이 17~19초였음).
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    public static final String CACHE = "weatherToday";
    public static final String FORECAST_CACHE = "weatherForecast";

    private final OpenMeteoClient openMeteo;
    private final MetNoClient metNo;
    private final Duration skipFor;
    private final Clock clock;
    private volatile Instant openMeteoDownUntil = Instant.MIN;

    @Autowired
    public WeatherService(OpenMeteoClient openMeteo, MetNoClient metNo,
            @Value("${weather.fallback.skip-for:5m}") Duration skipFor) {
        this(openMeteo, metNo, skipFor, Clock.systemUTC());
    }

    WeatherService(OpenMeteoClient openMeteo, MetNoClient metNo, Duration skipFor, Clock clock) {
        this.openMeteo = openMeteo;
        this.metNo = metNo;
        this.skipFor = skipFor;
        this.clock = clock;
    }

    /** 테스트용: 대체 출처 없이. */
    WeatherService(OpenMeteoClient openMeteo) {
        this(openMeteo, null, Duration.ZERO, Clock.systemUTC());
    }

    /** 좌표를 소수점 2자리로 줄인 뒤 부른다. 캐시 키와 실제 조회 위치가 같아야 해서 여기서 줄인다. */
    @Cacheable(cacheNames = CACHE, key = "T(com.fashionagent.weather.WeatherService).cacheKey(#lat, #lon)")
    public WeatherToday today(double lat, double lon) {
        double a = round2(lat), b = round2(lon);
        return WeatherToday.from(withFallback(() -> openMeteo.forecast(a, b),
                () -> metNo.forecast(a, b, LocalDate.now(clock.withZone(MetNoClient.SEOUL)))));
    }

    /** 날짜 하루의 예보. 같은 위치·날짜는 30분 캐시한다. */
    @Cacheable(cacheNames = FORECAST_CACHE,
            key = "T(com.fashionagent.weather.WeatherService).cacheKey(#lat, #lon) + ',' + #date")
    public WeatherDay day(double lat, double lon, LocalDate date) {
        double a = round2(lat), b = round2(lon);
        return WeatherDay.from(withFallback(() -> openMeteo.daily(a, b, date), () -> metNo.daily(a, b, date)));
    }

    <T> T withFallback(Supplier<T> primary, Supplier<T> fallback) {
        if (metNo == null) {
            return primary.get();
        }
        if (clock.instant().isBefore(openMeteoDownUntil)) {
            return fallback.get();
        }
        try {
            return primary.get();
        } catch (RuntimeException e) {
            openMeteoDownUntil = clock.instant().plus(skipFor);
            log.warn("Open-Meteo 실패, MET Norway로 넘어가요({} 동안 Open-Meteo 건너뜀): {}", skipFor, e.getMessage());
            try {
                return fallback.get();
            } catch (RuntimeException f) {
                log.warn("MET Norway도 실패: {}", f.getMessage());
                e.addSuppressed(f);
                throw e;
            }
        }
    }

    public static String cacheKey(double lat, double lon) {
        return round2(lat) + "," + round2(lon);
    }

    static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
