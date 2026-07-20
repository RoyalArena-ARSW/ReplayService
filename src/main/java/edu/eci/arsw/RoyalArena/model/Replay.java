package edu.eci.arsw.RoyalArena.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una partida archivada. Guarda los metadatos como columnas (para listar y
 * filtrar rápido) y los datos pesados de reproducción como JSON en una columna
 * de texto (no hay que consultarlos, solo devolverlos completos al reproducir).
 *
 * Índice por userId: la consulta "mis replays" y la retención (contar cuántas
 * tiene un usuario) lo usan constantemente.
 */
@Entity
@Table(name = "replays", indexes = {
        @Index(name = "idx_replay_player_a", columnList = "playerAId"),
        @Index(name = "idx_replay_player_b", columnList = "playerBId"),
        @Index(name = "idx_replay_played_at", columnList = "playedAtMs")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Replay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String matchId;

    private Long playerAId;
    private String playerAName;
    private Long playerBId;
    private String playerBName;

    private String winnerTeam;
    private int crownsA;
    private int crownsB;
    private double durationSeconds;

    /** Momento en que se jugó (epoch ms). Para ordenar y para retención. */
    private long playedAtMs;

    /**
     * Los datos pesados de reproducción (cardsPlayed + snapshots) serializados
     * como JSON. columnDefinition TEXT porque puede ser grande (180 snapshots).
     */
    @Column(columnDefinition = "TEXT")
    private String replayData;

    private Instant createdAt;
}