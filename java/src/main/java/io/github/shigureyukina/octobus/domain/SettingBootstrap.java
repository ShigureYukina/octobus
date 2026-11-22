package io.github.shigureyukina.octobus.domain;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
class SettingBootstrap implements ApplicationRunner {

    private final SettingService settingService;

    SettingBootstrap(SettingService settingService) {
        this.settingService = settingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        settingService.initializeDefaults();
    }
}
