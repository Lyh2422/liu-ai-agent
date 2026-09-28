package com.lyh.liuaiagent.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "love-app.rag.pgvector")
public class LoveAppPgVectorProperties {
    private boolean enabled;
    private String url = "jdbc:postgresql://localhost:5432/liu_ai_vectors";
    private String username = "liu_ai_agent";
    private String password = "liu_ai_agent_vector_dev";
    private String schema = "public";
    private String table = "love_knowledge_vectors_qwen37_flash";
    private int dimensions;
    private int maximumPoolSize = 5;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }
    public String getTable() { return table; }
    public void setTable(String table) { this.table = table; }
    public int getDimensions() { return dimensions; }
    public void setDimensions(int dimensions) { this.dimensions = dimensions; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
}
