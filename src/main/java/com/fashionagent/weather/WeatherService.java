package com.fashionagent.weather;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/** 오늘 날씨. 같은 위치(소수점 2자리, 약 1km)는 30분 캐시한다. */
@Service
public class WeatherService {

    public static final String CACHE = "weatherToday";

    private final OpenMeteoClient openMeteo;

    public WeatherService(OpenMeteoClient openMeteo) {
        this.openMeteo = openMeteo;
    }

    /** 좌표를 소수점 2자리로 줄인 뒤 부른다. 캐시 키와 실제 조회 위치가 같아야 해서 여기서 줄인다. */
    @Cacheable(cacheNames = CACHE, key = "T(com.fashionagent.weather.WeatherService).cacheKey(#lat, #lon)")
    public WeatherToday today(double lat, double lon) {
        return WeatherToday.from(openMeteo.forecast(round2(lat), round2(lon)));
    }

    public static String cacheKey(double lat, double lon) {
        return round2(lat) + "," + round2(lon);
    }

    static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
