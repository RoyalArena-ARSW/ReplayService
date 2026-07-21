package edu.eci.arsw.RoyalArena.dto;

public record ReplaySummaryDTO(
        Long id,
        String matchId,
        Long playerAId, String playerAName,
        Long playerBId, String playerBName,
        String winnerTeam,
        int crownsA, int crownsB,
        double durationSeconds,
        long playedAtMs
) { }