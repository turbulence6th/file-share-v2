package com.turbulence6th.fileshare.service;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.stereotype.Service;

@Service
public class HashService {

    public String generateHash() {
        return RandomStringUtils.randomAlphanumeric(4).toString();
    }
}
