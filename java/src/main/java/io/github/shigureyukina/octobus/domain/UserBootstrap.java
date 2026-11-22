package io.github.shigureyukina.octobus.domain;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class UserBootstrap implements ApplicationRunner {

    private final UserService userService;

    UserBootstrap(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        userService.initializeDefaultUser();
    }
}
