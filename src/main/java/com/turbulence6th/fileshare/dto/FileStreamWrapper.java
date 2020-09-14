package com.turbulence6th.fileshare.dto;

import lombok.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CountDownLatch;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileStreamWrapper {

    private CountDownLatch latch;
    private InputStream inputStream;
    private OutputStream outputStream;
    private int status;
}
