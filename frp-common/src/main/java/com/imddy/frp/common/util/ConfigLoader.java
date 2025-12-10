package com.imddy.frp.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
public class ConfigLoader {
    private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

    public static <T> T loadConfig(String configPath, Class<T> clazz) throws IOException {
        File configFile = new File(configPath);
        if (configFile.exists()) {
            log.info("Loading config from file: {}", configPath);
            return YAML_MAPPER.readValue(configFile, clazz);
        }

        // 尝试从classpath加载
        InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream(configPath);
        if (is != null) {
            log.info("Loading config from classpath: {}", configPath);
            return YAML_MAPPER.readValue(is, clazz);
        }

        throw new IOException("Config file not found: " + configPath);
    }
}
