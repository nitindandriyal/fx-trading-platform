package play.lab.config.service;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.theme.lumo.Lumo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Theme(variant = Lumo.DARK)
public class ConfigServiceAppLauncher implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServiceAppLauncher.class, args);
        Thread aeronThread = new Thread(AeronService.INSTANCE::loop);
        aeronThread.setName("Aeron-Service-Thread");
        aeronThread.start();
    }
}
