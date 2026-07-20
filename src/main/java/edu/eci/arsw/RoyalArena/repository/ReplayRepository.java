package edu.eci.arsw.RoyalArena.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import edu.eci.arsw.RoyalArena.model.Replay;

public interface ReplayRepository extends JpaRepository<Replay, Long> {

    /**
     * Replays donde el usuario participó (como A o como B), más recientes
     * primero. Es la consulta de "mis replays".
     */
    @Query("SELECT r FROM Replay r WHERE r.playerAId = :userId OR r.playerBId = :userId "
            + "ORDER BY r.playedAtMs DESC")
    List<Replay> findByParticipant(@Param("userId") Long userId);

    /** Cuántas replays tiene un usuario (para la retención de máx 10). */
    @Query("SELECT COUNT(r) FROM Replay r WHERE r.playerAId = :userId OR r.playerBId = :userId")
    long countByParticipant(@Param("userId") Long userId);

    @Query("SELECT COUNT(r) > 0 FROM Replay r WHERE r.matchId = :matchId")
    boolean findByMatchIdExists(@Param("matchId") String matchId);
}