package jsd.toiyeujava.mini_leetcode.ranking.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jsd.toiyeujava.mini_leetcode.ranking.service.RankingService;
import jsd.toiyeujava.mini_leetcode.ranking.service.RankingService.RankingRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ranking")
@Tag(name = "Ranking", description = "Site-wide leaderboard")
public class RankingController {

    private final RankingService rankingService;

    public RankingController(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @GetMapping
    @Operation(summary = "Top students by distinct accepted problems; ties go to fewer submissions")
    public List<RankingRow> ranking() {
        return rankingService.top();
    }
}
