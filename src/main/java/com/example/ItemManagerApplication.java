package com.example.itemmanager;

import com.example.itemmanager.ui.SwingClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ItemManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ItemManagerApplication.class, args);
    }

    /**
     * Launch Swing client conditionally.
     * To launch desktop UI when running locally, pass system property -Ddesktop=true
     * or environment variable DESKTOP=true.
     */
    @Bean
    public ApplicationRunner runner() {
        return new ApplicationRunner() {
            @Override
            public void run(ApplicationArguments args) throws Exception {
                String prop = System.getProperty("desktop");
                String env = System.getenv("DESKTOP");
                boolean launchDesktop = "true".equalsIgnoreCase(prop) || "true".equalsIgnoreCase(env);
                if (launchDesktop) {
                    // Launch Swing on a separate thread to avoid blocking Spring Boot
                    new Thread(() -> {
                        try {
                            SwingClient client = new SwingClient();
                            client.show();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }, "Swing-Client-Thread").start();
                }
            }
        };
    }
}
