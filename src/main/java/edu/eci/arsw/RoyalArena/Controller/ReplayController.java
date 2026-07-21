package edu.eci.arsw.RoyalArena.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.eci.arsw.RoyalArena.dto.ReplayDetailDTO;
import edu.eci.arsw.RoyalArena.dto.ReplaySummaryDTO;
import edu.eci.arsw.RoyalArena.model.Replay;
import edu.eci.arsw.RoyalArena.service.ReplayService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/replays")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;

    /**
     * Mis replays (las últimas 10). El userId llega en el header que inyecta el
     * Gateway tras validar el token.
     */
    @GetMapping("/my")
    public ResponseEntity<List<ReplaySummaryDTO>> myReplays(
            @RequestHeader("X-User-Id") Long userId) {
        log.info("GET /api/replays/my - user {}", userId);
        List<ReplaySummaryDTO> summaries = replayService.getReplaysOf(userId).stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(summaries);
    }

    /**
     * Los datos completos de una replay para reproducirla.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ReplayDetailDTO> getReplay(@PathVariable Long id) {
        Replay replay = replayService.getReplayById(id);
        return ResponseEntity.ok(new ReplayDetailDTO(
                toSummary(replay),
                replayService.parseReplayData(replay)));
    }

    private ReplaySummaryDTO toSummary(Replay r) {
        return new ReplaySummaryDTO(
                r.getId(), r.getMatchId(),
                r.getPlayerAId(), r.getPlayerAName(),
                r.getPlayerBId(), r.getPlayerBName(),
                r.getWinnerTeam(), r.getCrownsA(), r.getCrownsB(),
                r.getDurationSeconds(), r.getPlayedAtMs());
    }
}