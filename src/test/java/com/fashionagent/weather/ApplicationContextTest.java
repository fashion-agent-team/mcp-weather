package com.fashionagent.weather;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** 실제 빈 그대로(모킹 없이) 앱이 뜨는지 본다. */
@SpringBootTest
class ApplicationContextTest {

    @Autowired
    OpenMeteoClient openMeteoClient;

    @Test
    @DisplayName("모킹 없이 앱이 뜬다")
    void contextLoads() {
        assertThat(openMeteoClient).isNotNull();
    }
}
