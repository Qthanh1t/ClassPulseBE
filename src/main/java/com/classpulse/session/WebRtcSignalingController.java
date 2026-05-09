package com.classpulse.session;

import com.classpulse.common.security.StompPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebRtcSignalingController {

    private final SessionBroadcastService broadcastService;

    @MessageMapping("/session/{sessionId}/webrtc/offer")
    public void handleOffer(
            @DestinationVariable UUID sessionId,
            @Payload WebRtcSdpRequest request,
            Principal principal) {
        StompPrincipal sp = (StompPrincipal) principal;
        broadcastService.sendToUser(request.getTargetId(), "webrtc_offer",
                Map.of("fromId", sp.userId(), "sdp", request.getSdp()));
        log.debug("WebRTC offer: session={} from={} to={}", sessionId, sp.userId(), request.getTargetId());
    }

    @MessageMapping("/session/{sessionId}/webrtc/answer")
    public void handleAnswer(
            @DestinationVariable UUID sessionId,
            @Payload WebRtcSdpRequest request,
            Principal principal) {
        StompPrincipal sp = (StompPrincipal) principal;
        broadcastService.sendToUser(request.getTargetId(), "webrtc_answer",
                Map.of("fromId", sp.userId(), "sdp", request.getSdp()));
        log.debug("WebRTC answer: session={} from={} to={}", sessionId, sp.userId(), request.getTargetId());
    }

    @MessageMapping("/session/{sessionId}/webrtc/ice-candidate")
    public void handleIceCandidate(
            @DestinationVariable UUID sessionId,
            @Payload WebRtcIceRequest request,
            Principal principal) {
        StompPrincipal sp = (StompPrincipal) principal;
        broadcastService.sendToUser(request.getTargetId(), "webrtc_ice_candidate",
                Map.of("fromId", sp.userId(), "candidate", request.getCandidate()));
        log.debug("WebRTC ICE: session={} from={} to={}", sessionId, sp.userId(), request.getTargetId());
    }
}
