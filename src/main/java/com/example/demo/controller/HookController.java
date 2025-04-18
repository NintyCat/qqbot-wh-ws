package com.example.demo.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.entity.Payload;
import com.example.demo.entity.event.GroupMessageEvent;
import com.example.demo.entity.valid.ValidationRequest;
import com.example.demo.entity.valid.ValidationResponse;
import com.example.demo.handler.Handler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Security;
import java.util.HexFormat;

import static com.example.demo.util.ValidUtil.*;

@RestController
@RequestMapping("/webhook")
@Slf4j
public class HookController {
    @Resource
    private Handler handler;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @PostMapping("")
    public ResponseEntity<?> handleValidation(@RequestParam("secret") String secret,
                                              @RequestBody String rawBody,
                                              @RequestHeader("X-Signature-Ed25519") String sig,
                                              @RequestHeader("X-Signature-Timestamp") String timestamp) {
        try {
            String seed = prepareSeed(secret);
            KeyPair keyPair = generateEd25519KeyPair(seed.getBytes(StandardCharsets.UTF_8));
            Payload<?> payload = JSON.parseObject(rawBody, Payload.class);
            switch (payload.getOp()) {
                case 0 -> {
                    boolean isValid = verifySignature(sig, timestamp, rawBody.getBytes(StandardCharsets.UTF_8), keyPair.getPublic().getEncoded());
                    GroupMessageEvent event = JSONObject.parseObject(payload.getD().toString(), GroupMessageEvent.class);
                    handler.handle(secret,event.getGroup_openid(),payload);
                    //处理机器人逻辑
                    return ResponseEntity.ok(isValid);
                }
                case 13 -> {
                    //验证签名
                    log.info("验证有效性...");
                    ValidationRequest validationPayload = objectMapper.convertValue(payload.getD(), ValidationRequest.class);
                    byte[] message = (validationPayload.getEvent_ts() + validationPayload.getPlain_token()).getBytes(StandardCharsets.UTF_8);
                    byte[] signature = signMessage(keyPair.getPrivate(), message);
                    ValidationResponse resp = new ValidationResponse(validationPayload.getPlain_token(), HexFormat.of().formatHex(signature));
                    return ResponseEntity.ok(resp);
                }
                default -> log.info("未处理操作：{}", JSON.toJSONString(payload));
            }
        } catch (Exception e) {
            log.error("验证失败：", e);
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
    }


}
