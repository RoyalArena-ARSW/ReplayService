package edu.eci.arsw.RoyalArena.dto;

import edu.eci.arsw.RoyalArena.service.ReplayService.ReplayData;

public record ReplayDetailDTO(
        ReplaySummaryDTO summary,
        ReplayData data
) { }