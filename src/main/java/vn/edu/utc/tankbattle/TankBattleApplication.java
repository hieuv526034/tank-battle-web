package vn.edu.utc.tankbattle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Điểm vào duy nhất. Scheduling chạy vòng game và dọn các phòng mất kết nối. */
@SpringBootApplication
@EnableScheduling
public class TankBattleApplication {
    public static void main(String[] args) {
        SpringApplication.run(TankBattleApplication.class, args);
    }
}
