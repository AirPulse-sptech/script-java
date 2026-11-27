package school.sptech.service;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Lista todos os objetos S3 sob um prefixo cujo nome termine com
 * {@code _gold_alertas.json}. Realiza paginação completa para garantir
 * que nenhum arquivo seja omitido.
 */
public class S3AlertLister {

    private final S3Client s3Client;

    public S3AlertLister(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Retorna as chaves de todos os arquivos Gold de alertas encontrados
     * no bucket/prefixo informado.
     *
     * @param bucket nome do bucket S3
     * @param prefix prefixo de busca (ex.: {@code 03-gold/})
     * @return lista de chaves S3 que terminam com {@code _gold_alertas.json}
     */
    public List<String> listar(String bucket, String prefix) {
        List<String> keys = new ArrayList<>();
        String continuationToken = null;

        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix);

            if (continuationToken != null) {
                reqBuilder.continuationToken(continuationToken);
            }

            ListObjectsV2Response response = s3Client.listObjectsV2(reqBuilder.build());

            for (S3Object obj : response.contents()) {
                if (obj.key().endsWith("_gold_alertas.json")) {
                    keys.add(obj.key());
                }
            }

            continuationToken = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (continuationToken != null);

        return keys;
    }
}
