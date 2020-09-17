package com.turbulence6th.fileshare.config;

import com.turbulence6th.fileshare.dto.FileShareWrapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class ShareConfig {

    @Bean
    public Map<String, FileShareWrapper> shareMap() {
        return new HashMap<>();
    }
}
