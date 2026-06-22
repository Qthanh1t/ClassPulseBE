package com.classpulse.session;

/**
 * Phản hồi cấp LiveKit access token cho client.
 *
 * @param token JWT (HS256) chứa video grant — client truyền vào room.connect()
 * @param url   URL WebSocket của LiveKit server (browser kết nối trực tiếp, không qua proxy)
 * @param identity Định danh participant trong LiveKit = userId (để FE map participant ↔ presence)
 */
public record LiveKitTokenResponse(String token, String url, String identity) {}
