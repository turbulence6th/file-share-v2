package com.turbulence6th.fileshare.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShareRequestDTO {

    private String filename;
    private Long size;
}
