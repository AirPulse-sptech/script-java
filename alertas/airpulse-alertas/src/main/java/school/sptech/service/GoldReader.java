package school.sptech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import school.sptech.dto.AlertaDto;

public class GoldReader {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoldReader(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public List<AlertaDto> ler(String bucket, String chave) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(chave)
                .build();

        try (InputStream conteudo = s3Client.getObject(request, ResponseTransformer.toInputStream())) {
            return objectMapper.readValue(conteudo, new TypeReference<List<AlertaDto>>() {});
        }
    }
}