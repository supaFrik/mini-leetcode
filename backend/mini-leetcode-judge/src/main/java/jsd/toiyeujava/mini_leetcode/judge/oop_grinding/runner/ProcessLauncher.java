package jsd.toiyeujava.mini_leetcode.judge.oop_grinding.runner;

import java.io.IOException;

@FunctionalInterface
public interface ProcessLauncher {
    Process launch(ProcessBuilder processBuilder) throws IOException;
}
