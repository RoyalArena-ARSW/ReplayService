package edu.eci.arsw.RoyalArena.service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.eci.arsw.RoyalArena.dto.ReplayPacket;
import edu.eci.arsw.RoyalArena.model.Replay;
import edu.eci.arsw.RoyalArena.repository.ReplayRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Archiva las replays y mantiene la política de retención: máximo N por usuario.
 */
@Slf4j
@Service
public class ReplayService {

    private final ReplayRepository repository;
    private final ObjectMapper objectMapper;
    private final int maxPerUser;

    public ReplayService(ReplayRepository repository,
                         ObjectMapper objectMapper,
                         @Value("${royalarena.replay.max-per-user}") int maxPerUser) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.maxPerUser = maxPerUser;
    }

    /**
     * Guarda una replay recibida de Game Engine y aplica retención a AMBOS
     * jugadores. Idempotente: si ya existe una replay con ese matchId, la ignora
     * (RabbitMQ puede reentregar un mensaje).
     */
    @Transactional
    public void saveReplay(ReplayPacket packet) {
        if (repository.findByMatchIdExists(packet.matchId())) {
            log.info("Replay for match {} already exists, skipping", packet.matchId());
            return;
        }

        String replayJson;
        try {
            // Empaquetar cardsPlayed + snapshots en un solo JSON para la columna TEXT
            replayJson = objectMapper.writeValueAsString(
                    new ReplayData(packet.cardsPlayed(), packet.snapshots()));
        } catch (JsonProcessingException e) {
            log.error("Could not serialize replay data for match {}: {}",
                    packet.matchId(), e.getMessage());
            return;
        }

        Replay replay = Replay.builder()
                .matchId(packet.matchId())
                .playerAId(packet.playerAId())
                .playerAName(packet.playerAName())
                .playerBId(packet.playerBId())
                .playerBName(packet.playerBName())
                .winnerTeam(packet.winnerTeam())
                .crownsA(packet.crownsA())
                .crownsB(packet.crownsB())
                .durationSeconds(packet.durationSeconds())
                .playedAtMs(packet.playedAtMs())
                .replayData(replayJson)
                .createdAt(Instant.now())
                .build();

        repository.save(replay);
        log.info("Saved replay for match {} ({} vs {})",
                packet.matchId(), packet.playerAId(), packet.playerBId());

        // Retención: cada jugador conserva solo sus N más recientes
        enforceRetention(packet.playerAId());
        enforceRetention(packet.playerBId());
    }

    /**
     * Si el usuario supera el máximo, borra sus replays más viejas hasta dejarlo
     * en el límite. Como una replay puede involucrar a dos usuarios, solo se
     * borra del disco si NINGUNO de los dos la necesita para su límite.
     *
     * Simplificación: borramos la replay más vieja del usuario que se pasó. Si el
     * otro jugador aún la quería, se pierde igual — para un proyecto es aceptable.
     * (La alternativa "conservar si algún dueño la necesita" requiere contar por
     * ambos lados y complica sin aportar mucho.)
     */
    private void enforceRetention(Long userId) {
        List<Replay> replays = repository.findByParticipant(userId); // más recientes primero
        if (replays.size() <= maxPerUser) {
            return;
        }
        List<Replay> toDelete = replays.subList(maxPerUser, replays.size());
        repository.deleteAll(toDelete);
        log.info("Retention: deleted {} old replays for user {}", toDelete.size(), userId);
    }

    public List<Replay> getReplaysOf(Long userId) {
        return repository.findByParticipant(userId);
    }

    public Replay getReplayById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Replay no encontrada: " + id));
    }

    /** Deserializa los datos pesados para devolverlos al reproducir. */
    public ReplayData parseReplayData(Replay replay) {
        try {
            return objectMapper.readValue(replay.getReplayData(), ReplayData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Replay data corrupta para " + replay.getId(), e);
        }
    }

    /** Contenedor de los datos pesados, tal como se guardan en la columna TEXT. */
    public record ReplayData(
            List<ReplayPacket.ReplayCardPlayed> cardsPlayed,
            List<ReplayPacket.ReplaySnapshot> snapshots
    ) { }
}