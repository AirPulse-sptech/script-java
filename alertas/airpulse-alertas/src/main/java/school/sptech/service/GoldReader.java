package school.sptech.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import school.sptech.dto.AlertaGoldDto;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

public class GoldReader {

    private final S3Client s3Client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GoldReader(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public List<AlertaGoldDto> ler(String bucket, String chave) throws IOException {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(chave)
                .build();

        try (InputStream conteudo = s3Client.getObject(request, ResponseTransformer.toInputStream())) {
            List<AlertaGoldDto> eventos = objectMapper.readValue(conteudo, new TypeReference<List<AlertaGoldDto>>() {});
            return eventos == null ? List.of() : eventos;
        }
    }
}
