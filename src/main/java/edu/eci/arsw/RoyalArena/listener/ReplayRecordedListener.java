package edu.eci.arsw.RoyalArena.listener;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import edu.eci.arsw.RoyalArena.dto.ReplayPacket;
import edu.eci.arsw.RoyalArena.service.ReplayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recibe los paquetes de replay de Game Engine y los archiva.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayRecordedListener {

    private final ReplayService replayService;

    @RabbitListener(queues = "${royalarena.events.queue.replay-recorded}")
    public void onReplayRecorded(ReplayPacket packet) {
        log.info("Received ReplayPacket for match {} ({} snapshots)",
                packet.matchId(), packet.snapshots() != null ? packet.snapshots().size() : 0);
        replayService.saveReplay(packet);
    }
}