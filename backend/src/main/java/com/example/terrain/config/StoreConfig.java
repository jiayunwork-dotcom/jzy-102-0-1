package com.example.terrain.config;

import com.example.terrain.snapshot.SnapshotStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Process-wide in-memory snapshot store. */
@Configuration
public class StoreConfig {
    @Bean
    public SnapshotStore snapshotStore() {
        return new SnapshotStore();
    }
}
