package org.eclipse.hawkbit.artifact.fs;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.hawkbit.artifact.AbstractArtifactStorage;
import org.eclipse.hawkbit.artifact.ArtifactStorage;
import org.eclipse.hawkbit.artifact.exception.ArtifactBinaryNotFoundException;
import org.eclipse.hawkbit.artifact.exception.ArtifactStoreException;
import org.eclipse.hawkbit.artifact.model.ArtifactHashes;
import org.eclipse.hawkbit.autoconfigure.artifact.S3ArtifactProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * S3-compatible {@link ArtifactStorage} implementation using AWS S3 SDK v2.
 * Extends {@link AbstractArtifactStorage} for hash computation and dedup logic.
 *
 * <p>Supports virtual-hosted style URLs by default (required by Volcano Engine TOS).
 * Activate by setting {@code hawkbit.artifact.s3.endpoint} in application properties.
 * When not configured, falls back to {@link FileArtifactStorage}.
 */
@Slf4j
public class S3ArtifactStorage extends AbstractArtifactStorage {

    private final S3ArtifactProperties props;
    private final S3Client client;

    public S3ArtifactStorage(S3ArtifactProperties props) {
        this.props = props;
        var builder = S3Client.builder()
            .endpointOverride(URI.create(props.getEndpoint()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
            .forcePathStyle(false) // virtual-hosted style required by TOS
            .overrideConfiguration(c -> c
                .apiCallTimeout(Duration.ofSeconds(30))
                .apiCallAttemptTimeout(Duration.ofSeconds(10)));

        if (props.getRegion() != null && !props.getRegion().isEmpty()) {
            builder.region(Region.of(props.getRegion()));
        } else {
            builder.region(Region.US_EAST_1); // dummy region for non-AWS endpoints
        }
        this.client = builder.build();
        log.info("[S3] Initialized: endpoint={}, bucket={}, region={}",
            props.getEndpoint(), props.getBucket(), props.getRegion());
    }

    @Override
    protected void store(String tenant, ArtifactHashes hashes, String contentType, File tempFile) throws IOException {
        String key = objectKey(tenant, hashes.sha1());
        try {
            client.putObject(PutObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .contentType(contentType != null ? contentType : "application/octet-stream")
                .build(),
                RequestBody.fromFile(tempFile));
            log.debug("[S3] Stored artifact: {} ({} bytes)", key, tempFile.length());
        } catch (Exception e) {
            throw new ArtifactStoreException("Failed to store artifact to S3: " + key, e);
        }
    }

    @Override
    public InputStream getBySha1(String tenant, String sha1Hash) {
        String key = objectKey(tenant, sha1Hash);
        try {
            return client.getObject(GetObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .build());
        } catch (NoSuchKeyException e) {
            throw new ArtifactBinaryNotFoundException("Artifact not found: " + key);
        } catch (Exception e) {
            throw new ArtifactStoreException("Failed to read artifact from S3: " + key, e);
        }
    }

    @Override
    public boolean existsBySha1(String tenant, String sha1Hash) {
        String key = objectKey(tenant, sha1Hash);
        try {
            client.headObject(HeadObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .build());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void deleteBySha1(String tenant, String sha1Hash) {
        String key = objectKey(tenant, sha1Hash);
        try {
            client.deleteObject(b -> b.bucket(props.getBucket()).key(key));
            log.debug("[S3] Deleted artifact: {}", key);
        } catch (Exception e) {
            log.warn("[S3] Failed to delete artifact {}: {}", key, e.getMessage());
        }
    }

    @Override
    public void deleteByTenant(String tenant) {
        String prefix = sanitizeTenant(tenant) + "/";
        try {
            ListObjectsV2Response listResp = client.listObjectsV2(ListObjectsV2Request.builder()
                .bucket(props.getBucket())
                .prefix(prefix)
                .build());

            var ids = listResp.contents().stream()
                .map(S3Object::key)
                .map(k -> ObjectIdentifier.builder().key(k).build())
                .collect(Collectors.toList());

            if (!ids.isEmpty()) {
                client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(props.getBucket())
                    .delete(Delete.builder().objects(ids).build())
                    .build());
            }
            log.info("[S3] Deleted {} artifacts for tenant: {}", ids.size(), tenant);
        } catch (Exception e) {
            throw new ArtifactStoreException("Failed to delete tenant artifacts from S3: " + tenant, e);
        }
    }

    private static String objectKey(String tenant, String sha1Hash) {
        return sanitizeTenant(tenant) + "/" + sha1Hash;
    }
}
