package edu.eci.arsw.RoyalArena.dto;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Paquete de replay recibido de Game Engine por RabbitMQ. El servicio no
 * necesita interpretar el estado del tablero: solo archivarlo y devolverlo. Por
 * eso 'state' es un JsonNode genérico en vez de tipar todo el MatchSnapshotDTO
 * (que obligaría a copiar todos los DTOs del snapshot aquí).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReplayPacket(
        String matchId,
        Long playerAId,
        String playerAName,
        Long playerBId,
        String playerBName,
        String winnerTeam,
        int crownsA,
        int crownsB,
        double durationSeconds,
        long playedAtMs,
        List<ReplayCardPlayed> cardsPlayed,
        List<ReplaySnapshot> snapshots
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReplayCardPlayed(
            int tick, Long playerId, Long cardId, double x, double y
    ) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReplaySnapshot(
            int tick,
            JsonNode state   // el snapshot completo, sin tipar campo por campo
    ) { }
}