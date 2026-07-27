package org.eclipse.hawkbit.autoconfigure.artifact;

import org.eclipse.hawkbit.artifact.ArtifactStorage;
import org.eclipse.hawkbit.artifact.fs.S3ArtifactStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Provides {@link S3ArtifactStorage} as the primary {@link ArtifactStorage} bean
 * when S3 object storage is configured (via AWS S3 SDK v2, virtual-hosted style).
 *
 * <p>Activate with:
 * <pre>
 * hawkbit.artifact.s3.endpoint=https://tos-s3-cn-shanghai.ivolces.com
 * hawkbit.artifact.s3.access-key=your-access-key
 * hawkbit.artifact.s3.secret-key=your-secret-key
 * hawkbit.artifact.s3.bucket=zota
 * </pre>
 *
 * <p>When S3 is not configured, the default {@code FileArtifactStorage} is used.
 */
@AutoConfiguration
@EnableConfigurationProperties(S3ArtifactProperties.class)
@ConditionalOnProperty(prefix = "hawkbit.artifact.s3", name = "endpoint")
public class S3ArtifactStorageConfiguration {

    @Bean
    @Primary
    public ArtifactStorage artifactStorage(S3ArtifactProperties properties) {
        return new S3ArtifactStorage(properties);
    }
}
