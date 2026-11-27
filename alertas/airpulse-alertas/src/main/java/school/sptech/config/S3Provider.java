package school.sptech.config;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

public class S3Provider {

    private final AwsCredentialsProvider credentials;

    public S3Provider() {
        // Lê AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY e AWS_SESSION_TOKEN das variáveis de ambiente
        this.credentials = DefaultCredentialsProvider.create();
    }

    public S3Client getS3Client() {
        Region region = resolveRegion(System.getenv("aws_region"));

        return S3Client.builder()
                .region(region)
                .credentialsProvider(credentials)
                .build();
    }

    public static Region resolveRegion(String regionEnv) {
        return (regionEnv != null && !regionEnv.isBlank())
                ? Region.of(regionEnv.trim())
                : Region.US_EAST_1;
    }
}
