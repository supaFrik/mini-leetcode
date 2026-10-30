package jsd.toiyeujava.mini_leetcode.judge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "jsd.toiyeujava.mini_leetcode.judge",
        "jsd.toiyeujava.mini_leetcode.submission",
        "jsd.toiyeujava.mini_leetcode.storage",
        "jsd.toiyeujava.mini_leetcode.config"
})
@EntityScan("jsd.toiyeujava.mini_leetcode")
@EnableJpaRepositories("jsd.toiyeujava.mini_leetcode")
@EnableScheduling
public class MiniLeetcodeJudgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniLeetcodeJudgeApplication.class, args);
    }
}
