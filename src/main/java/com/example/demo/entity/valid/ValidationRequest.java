package com.example.demo.entity.valid;

import lombok.Data;

@Data
public class ValidationRequest {

    private String plain_token;

    private String event_ts;
}
