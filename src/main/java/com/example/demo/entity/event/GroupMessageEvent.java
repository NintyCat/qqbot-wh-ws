package com.example.demo.entity.event;

import lombok.Data;

import java.util.Date;

@Data
public class GroupMessageEvent {
    private String id;
    private Date timestamp;
    private String group_id;
    private String group_openid;
    private String message_type;
}


