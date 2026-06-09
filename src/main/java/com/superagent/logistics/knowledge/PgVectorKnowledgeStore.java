package com.superagent.logistics.knowledge;

import com.superagent.logistics.config.PgVectorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class PgVectorKnowledgeStore {

    private static final Logger log = LoggerFactory.getLogger(PgVectorKnowledgeStore.class);

    private final PgVectorProperties properties;
    private final TextEmbeddingService embeddingService;
    private volatile boolean ready;

    public PgVectorKnowledgeStore(PgVectorProperties properties, TextEmbeddingService embeddingService) {
        this.properties = properties;
        this.embeddingService = embeddingService;
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    public boolean isReady() {
        return properties.isEnabled() && ready;
    }

    public String table() {
        return tableName();
    }

    public int countChunks() {
        if (!isReady()) {
            return 0;
        }
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + tableName())) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException ex) {
            ready = false;
            log.warn("PGVector count failed: {}", ex.getMessage());
            return 0;
        }
    }

    public void syncChunks(List<KnowledgeChunk> chunks) {
        syncChunks("T001", chunks);
    }

    public void syncChunks(String tenantId, List<KnowledgeChunk> chunks) {
        if (!properties.isEnabled()) {
            ready = false;
            return;
        }
        try (Connection connection = openConnection()) {
            if (properties.isInitializeSchema()) {
                initializeSchema(connection);
            }
            replaceTenantChunks(connection, tenantId, chunks);
            ready = true;
            log.info("PGVector knowledge store synced: tenantId={}, chunks={}, table={}, embeddingProvider={}",
                    tenantId, chunks.size(), tableName(), embeddingService.providerName());
        } catch (RuntimeException | SQLException ex) {
            ready = false;
            String message = "PGVector knowledge store is not ready: " + ex.getMessage();
            if (properties.isFailFast()) {
                throw new IllegalStateException(message, ex);
            }
            log.warn("{}; keyword fallback remains available", message);
        }
    }

    public List<KnowledgeSearchResult> search(String tenantId, String query, int topK) {
        if (!isReady()) {
            return List.of();
        }
        float[] embedding = requireDimension(embeddingService.embedQuery(query));
        String vector = toVectorLiteral(embedding);
        String sql = """
                SELECT doc_id, chunk_id, title_path, content, metadata, acl_roles,
                       1 - (embedding <=> ?::vector) AS score
                FROM %s
                WHERE tenant_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """.formatted(tableName());
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, vector);
            statement.setString(2, tenantId);
            statement.setString(3, vector);
            statement.setInt(4, Math.max(topK * 8, topK));
            try (ResultSet rs = statement.executeQuery()) {
                List<KnowledgeSearchResult> results = new ArrayList<>();
                while (rs.next()) {
                    KnowledgeChunk chunk = new KnowledgeChunk(
                            rs.getString("doc_id"),
                            rs.getString("chunk_id"),
                            rs.getString("title_path"),
                            rs.getString("content"),
                            rs.getString("metadata"),
                            rs.getString("acl_roles")
                    );
                    results.add(new KnowledgeSearchResult(chunk, rs.getDouble("score")));
                }
                return results;
            }
        } catch (RuntimeException | SQLException ex) {
            ready = false;
            log.warn("PGVector search failed; keyword fallback remains available: {}", ex.getMessage());
            return List.of();
        }
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(properties.getJdbcUrl(), properties.getUsername(), properties.getPassword());
    }

    private void initializeSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION IF NOT EXISTS vector");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS %s (
                        id BIGSERIAL PRIMARY KEY,
                        tenant_id VARCHAR(64) NOT NULL,
                        doc_id VARCHAR(128) NOT NULL,
                        chunk_id VARCHAR(128) NOT NULL,
                        title_path VARCHAR(512) NOT NULL,
                        content TEXT NOT NULL,
                        metadata TEXT,
                        acl_roles VARCHAR(512),
                        embedding vector(%d) NOT NULL,
                        created_at TIMESTAMP NOT NULL,
                        updated_at TIMESTAMP NOT NULL,
                        UNIQUE (tenant_id, chunk_id)
                    )
                    """.formatted(tableName(), properties.getDimension()));
        }
    }

    private void replaceTenantChunks(Connection connection, String tenantId, List<KnowledgeChunk> chunks) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM " + tableName() + " WHERE tenant_id = ?")) {
            delete.setString(1, tenantId);
            delete.executeUpdate();
        }
        String sql = """
                INSERT INTO %s
                (tenant_id, doc_id, chunk_id, title_path, content, metadata, acl_roles, embedding, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::vector, ?, ?)
                """.formatted(tableName());
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.from(Instant.now());
            for (KnowledgeChunk chunk : chunks) {
                statement.setString(1, tenantId);
                statement.setString(2, chunk.docId());
                statement.setString(3, chunk.chunkId());
                statement.setString(4, chunk.title());
                statement.setString(5, chunk.content());
                statement.setString(6, chunk.metadata());
                statement.setString(7, chunk.aclRoles());
                float[] embedding = requireDimension(embeddingService.embedDocument(chunk.title() + "\n" + chunk.content()));
                statement.setString(8, toVectorLiteral(embedding));
                statement.setTimestamp(9, now);
                statement.setTimestamp(10, now);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        return builder.append(']').toString();
    }

    private float[] requireDimension(float[] vector) {
        if (vector.length != properties.getDimension()) {
            throw new IllegalStateException("Embedding dimension mismatch: model returned " + vector.length
                    + " but agent.vector-store.dimension is " + properties.getDimension());
        }
        return vector;
    }

    private String tableName() {
        String tableName = properties.getTableName();
        if (tableName == null || !tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalStateException("Invalid PGVector table name: " + tableName);
        }
        return tableName;
    }
}
