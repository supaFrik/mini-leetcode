package jsd.toiyeujava.mini_leetcode.judge.strategy;

import jsd.toiyeujava.mini_leetcode.entity.ProblemType;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.submission.dto.SubmissionTaskMessage;

import java.nio.file.Path;

public interface JudgeStrategy {

    boolean supports(ProblemType problemType);

    void executeJudge(SubmissionTaskMessage message, Problem problem, Path workDir) throws Exception;
}
