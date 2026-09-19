package com.lyh.liuaiagent.auth.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {
    private Jwt jwt = new Jwt();
    private BootstrapAdmin bootstrapAdmin = new BootstrapAdmin();
    private Upload upload = new Upload();
    private Cos cos = new Cos();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private long expirationMs = 7_200_000L;
    }

    @Getter
    @Setter
    public static class BootstrapAdmin {
        private String username;
        private String password;
    }

    @Getter
    @Setter
    public static class Upload {
        private String dir;
        private long maxSizeBytes = 2_097_152L;
    }

    @Getter
    @Setter
    public static class Cos {
        private boolean enabled;
        private String secretId;
        private String secretKey;
        private String region;
        private String bucket;
        private String publicBaseUrl;

        public boolean isConfigured() {
            return enabled && hasText(secretId) && hasText(secretKey) && hasText(region) && hasText(bucket);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
