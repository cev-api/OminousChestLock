package com.ominouschestlock.paper;

import java.util.Map;

interface LockRepository extends AutoCloseable {
    String backendName();

    void initialize() throws LockRepositoryException;

    Map<String, LockInfo> loadAll() throws LockRepositoryException;

    void saveAll(Map<String, LockInfo> locks) throws LockRepositoryException;

    void upsertAll(Map<String, LockInfo> locks) throws LockRepositoryException;

    @Override
    void close() throws LockRepositoryException;
}
