package org.eclipse.hawkbit.autoconfigure.artifact;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * S3-compatible artifact storage configuration.
 * Uses AWS S3 SDK v2 which defaults to virtual-hosted style (required by TOS).
 */
@Getter
@Setter
@ConfigurationProperties("hawkbit.artifact.s3")
public class S3ArtifactProperties {

    /** S3 endpoint URL (e.g. https://tos-s3-cn-shanghai.ivolces.com) */
    private String endpoint;

    /** Access key */
    private String accessKey;

    /** Secret key */
    private String secretKey;

    /** Bucket name */
    private String bucket = "zota-repo";

    /** Region (optional, auto-detected from endpoint for TOS) */
    private String region;
}
