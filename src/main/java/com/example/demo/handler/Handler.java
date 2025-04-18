package com.example.demo.handler;

import com.alibaba.fastjson2.JSONObject;
import com.example.demo.entity.Payload;
import com.example.demo.socket.WebSocketServer;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Handler {
    @Resource
    private WebSocketServer webSocketServer;

    public void handle(String secret,String groupId,Payload<?> payload) {
        webSocketServer.sendMessage(secret,groupId, JSONObject.toJSONString(payload));
    }
}
