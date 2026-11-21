package com.example.eventsbooking.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/** @param localDir directory of the local file storage (in Docker: a mounted volume) */
@ConfigurationProperties("app.storage")
public record StorageProperties(@DefaultValue("./data/uploads") Path localDir) {
}
