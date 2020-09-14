package com.turbulence6th.fileshare.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocketShareDTO {

    private String shareHash;
    private String streamHash;
}
