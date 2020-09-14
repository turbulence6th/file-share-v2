package com.turbulence6th.fileshare.dto;

import lombok.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CyclicBarrier;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileStreamWrapper {

    private CyclicBarrier barrier;
    private InputStream inputStream;
    private OutputStream outputStream;
}
