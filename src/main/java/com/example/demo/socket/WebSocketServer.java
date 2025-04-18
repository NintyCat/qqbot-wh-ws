package com.example.demo.socket;

import jakarta.annotation.PreDestroy;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.*;

@Component
@Slf4j
@ServerEndpoint("/ws/{secret}/{groupId}")
public class WebSocketServer {
    private String secret;
    private String groupId;

    private static final ConcurrentHashMap<String, ConcurrentHashMap<String, Session>> sessionMap = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService scheduler =
            new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setDaemon(true);
                t.setName("ws-heartbeat-daemon");
                return t;
            });
    private final Map<Session, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();

    // 增强心跳发送稳定性
    private void sendPing(Session session) {
        try {
            if (session != null && session.isOpen()) {
                // 同步发送+超时控制
                session.getBasicRemote().sendPing(ByteBuffer.wrap("HB".getBytes()));
            }
        } catch (IOException | IllegalStateException e) {
            log.debug("心跳发送终止[{}]：连接已关闭", session.getId());
        }
    }

    private void scheduleHeartbeat(Session session) {
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            if (session.isOpen()) {
                sendPing(session);
            } else {
                cancelHeartbeatTask(session);
            }
        }, 30, 30, TimeUnit.SECONDS);
        heartbeatTasks.put(session, task);
    }

    private void cancelHeartbeatTask(Session session) {
        ScheduledFuture<?> task = heartbeatTasks.remove(session);
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
    }

    private void cleanupSession(Session session) {
        sessionMap.computeIfPresent(secret, (k, v) -> {
            Session removed = v.remove(groupId);
            if (removed != null && removed.equals(session) && removed.isOpen()) {
                try {
                    removed.close();
                } catch (IOException e) {
                    log.warn("关闭会话异常：{}", e.getMessage());
                }
            }
            return v.isEmpty() ? null : v;
        });
        cancelHeartbeatTask(session);
    }

    @OnOpen
    @SuppressWarnings("unused")
    public void onOpen(Session session, @PathParam("secret") String secret,
                       @PathParam("groupId") String groupId) {
        this.secret = secret;
        this.groupId = groupId;
        session.setMaxIdleTimeout(60_000);

        scheduleHeartbeat(session);

        sessionMap.compute(secret, (k, v) -> {
            ConcurrentHashMap<String, Session> map = (v != null) ? v : new ConcurrentHashMap<>();
            Session oldSession = map.put(groupId, session);
            if (oldSession != null && oldSession.isOpen()) {
                cleanupSession(oldSession);
            }
            return map;
        });

        log.info("密钥 {} 的群组 {} 连接成功，当前在线数：{}", secret, groupId, getTotalConnections());
    }

    @OnClose
    @SuppressWarnings("unused")
    public void onClose(Session session) {
        cleanupSession(session);
        log.info("密钥 {} 的群组 {} 断开成功，剩余在线数：{}", secret, groupId, getTotalConnections());
    }

    // 增强异常处理逻辑
    @OnError
    @SuppressWarnings("unused")
    public void onError(Session session, Throwable error) {
        if (error instanceof EOFException) {
            log.debug("客户端[{}]非正常断开连接", session.getId());
        } else {
            log.error("连接异常[{}]：{}", session.getId(), error.getMessage());
        }
        cleanupSession(session); // 确保资源回收
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
        sessionMap.forEach((secretKey, groups) ->
                groups.forEach((gid, session) -> {
                    try {
                        if (session.isOpen()) session.close();
                    } catch (IOException e) {
                        log.warn("应用关闭时清理异常：{}", e.getMessage());
                    }
                })
        );
    }


    public void sendMessage(String secret, String groupId, String message) {
        ConcurrentHashMap<String, Session> groupSessions = sessionMap.get(secret);
        if (groupSessions == null) return;
        Session session = groupSessions.getOrDefault(groupId, groupSessions.get("0"));
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                log.error("发送消息失败：{}", e.getMessage());
            }
        }
    }

    private int getTotalConnections() {
        return sessionMap.values().stream().mapToInt(Map::size).sum();
    }
}
