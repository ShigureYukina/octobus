package io.github.shigureyukina.octobus;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class OctobusApplication {

    public static void main(String[] args) {
        SpringApplication.run(OctobusApplication.class, args);
    }
}
