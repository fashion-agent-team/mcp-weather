package com.fashionagent.weather;

/**
 * 날씨 코드 → 한국어 상태, 옷차림 힌트 같은 고정 규칙.
 * 앱의 lib/models/weather.dart, lib/styling/weather_rules.dart를 옮겼다.
 * 옷이 날씨에 맞는지 판단하는 일은 agent(Gemini)가 하고, 여기서는 참고값만 만든다.
 */
public final class WeatherRules {

    /** 체감 온도가 이보다 낮으면 겉옷이 필요하다. */
    public static final double OUTER_BELOW_APPARENT = 20;

    /** 일교차가 이 이상이면 겉옷을 챙긴다. */
    public static final int BIG_DAILY_RANGE = 10;

    /** 강수 확률이 이 이상이면 우산을 챙긴다. */
    public static final int UMBRELLA_PRECIPITATION = 50;

    private WeatherRules() {
    }

    /** WMO 날씨 코드(https://open-meteo.com/en/docs)를 한국어로. */
    public static String condition(int code) {
        if (code == 0) return "맑음";
        if (code <= 2) return "구름 조금";
        if (code == 3) return "흐림";
        if (code == 45 || code == 48) return "안개";
        if (code >= 51 && code <= 57) return "이슬비";
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return "비";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "눈";
        if (code >= 95) return "뇌우";
        return "알 수 없음";
    }

    /** 체감 온도별 옷차림 힌트. */
    public static String clothingAdvice(double apparentTemperature) {
        double t = apparentTemperature;
        if (t >= 28) return "반팔과 반바지처럼 가볍고 얇은 옷이 좋아요.";
        if (t >= 23) return "반팔이나 얇은 셔츠에 면바지가 좋아요.";
        if (t >= 20) return "얇은 긴팔이나 가디건이 좋아요.";
        if (t >= 17) return "얇은 니트나 맨투맨에 긴바지가 좋아요.";
        if (t >= 12) return "자켓이나 가디건을 걸치세요.";
        if (t >= 9) return "트렌치코트나 야상 같은 겉옷이 필요해요.";
        if (t >= 5) return "코트와 히트텍처럼 따뜻한 옷이 좋아요.";
        return "패딩과 목도리처럼 아주 따뜻한 옷이 필요해요.";
    }

    public static boolean needsUmbrella(int precipitationProbability, String condition) {
        return precipitationProbability >= UMBRELLA_PRECIPITATION
                || condition.equals("비") || condition.equals("뇌우");
    }

    /** 체감 20도 미만이거나 일교차가 10도 이상이면 겉옷. */
    public static boolean needsOuter(double apparentTemperature, double dailyRange) {
        return apparentTemperature < OUTER_BELOW_APPARENT || Math.round(dailyRange) >= BIG_DAILY_RANGE;
    }
}
