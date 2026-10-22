package com.nexora.swing;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class NexoraSwingLauncher {
    private final NexoraSwingConsole console; private final Environment env;
    public NexoraSwingLauncher(NexoraSwingConsole console, Environment env){this.console=console;this.env=env;}
    @EventListener(ApplicationReadyEvent.class)
    public void maybeLaunch(){if(env.getProperty("nexora.swing.enabled",Boolean.class,false)) console.launch();}
}
