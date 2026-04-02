package com.ominouschestlock.paper;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

final class YamlLockRepository implements LockRepository {
    private final File dataFolder;
    private final File dataFile;
    private final int maxPins;
    private final int maxDepths;
    private final Logger logger;
    private final YamlStorageCodec.LocationMetadataResolver locationMetadataResolver;

    YamlLockRepository(File dataFolder, File dataFile, int maxPins, int maxDepths, Logger logger,
                       YamlStorageCodec.LocationMetadataResolver locationMetadataResolver) {
        this.dataFolder = dataFolder;
        this.dataFile = dataFile;
        this.maxPins = maxPins;
        this.maxDepths = maxDepths;
        this.logger = logger;
        this.locationMetadataResolver = locationMetadataResolver;
    }

    @Override
    public String backendName() {
        return "yaml";
    }

    @Override
    public void initialize() throws LockRepositoryException {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            throw new LockRepositoryException("Could not create data folder: " + dataFolder.getAbsolutePath());
        }
    }

    @Override
    public Map<String, LockInfo> loadAll() {
        return YamlStorageCodec.load(dataFile, maxPins, maxDepths);
    }

    @Override
    public void saveAll(Map<String, LockInfo> locks) throws LockRepositoryException {
        try {
            YamlStorageCodec.save(dataFile, new LinkedHashMap<>(locks), locationMetadataResolver);
        } catch (IOException exception) {
            throw new LockRepositoryException("Could not save data.yml", exception);
        }
    }

    @Override
    public void upsertAll(Map<String, LockInfo> locks) throws LockRepositoryException {
        Map<String, LockInfo> merged = loadAll();
        merged.putAll(locks);
        saveAll(merged);
    }

    @Override
    public void close() {
        if (logger != null) {
            logger.fine("YAML storage closed.");
        }
    }
}
