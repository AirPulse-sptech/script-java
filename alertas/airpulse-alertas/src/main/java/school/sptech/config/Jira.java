package school.sptech.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import school.sptech.dto.IssueFieldsDto;
import school.sptech.dto.IssueRequestDto;
import school.sptech.dto.IssueTypeDto;
import school.sptech.dto.ProjectDto;

public class Jira {

    private final String baseUrl;
    private final String authHeader;
    private final String projectKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public Jira(String baseUrl, String email, String apiToken) {
        this(baseUrl, email, apiToken, null);
    }

    public Jira(String baseUrl, String email, String apiToken, String projectKey) {
        this.baseUrl = normalizarBaseUrl(baseUrl);
        this.projectKey = projectKey;

        String auth = email + ":" + apiToken;
        this.authHeader = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        this.objectMapper = new ObjectMapper();
    }

    public String createIssue(String projectKey, String summary, String issueType)
            throws IOException, InterruptedException {
        return createIssue(projectKey, summary, issueType, null);
    }

    public String createIssue(String projectKey, String summary, String issueType, List<String> labels)
            throws IOException, InterruptedException {
        ProjectDto project = new ProjectDto(projectKey);
        IssueTypeDto type = new IssueTypeDto(issueType);
        IssueFieldsDto fields = (labels == null || labels.isEmpty())
                ? new IssueFieldsDto(project, summary, type)
                : new IssueFieldsDto(project, summary, type, labels);
        IssueRequestDto issueRequest = new IssueRequestDto(fields);

        String json = objectMapper.writeValueAsString(issueRequest);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/rest/api/3/issue"))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status >= 200 && status < 300) {
            return response.body();
        }

        if (status == 429) {
            throw new JiraRateLimitException("Jira rate limit (429) ao criar issue");
        }

        throw new RuntimeException("Jira request failed: HTTP " + status + " - " + response.body());
    }

    public boolean issueExistePorLabel(String label) throws IOException, InterruptedException {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalStateException("projectKey não configurado na instância Jira.");
        }
        return issueExistePorLabel(projectKey, label);
    }

    public boolean issueExistePorLabel(String targetProjectKey, String label)
            throws IOException, InterruptedException {
        String jql = String.format("project = \"%s\" AND labels = \"%s\"",
                escaparJql(targetProjectKey), escaparJql(label));

        Map<String, Object> body = Map.of(
                "jql", jql,
                "fields", List.of("labels"),
                "maxResults", 1
        );

        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/rest/api/3/search/jql"))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();

        if (status == 429) {
            throw new JiraRateLimitException("Jira rate limit (429) ao consultar label " + label);
        }

        if (status < 200 || status >= 300) {
            throw new RuntimeException("Jira search/jql failed: HTTP " + status + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode issues = root.get("issues");
        return issues != null && issues.isArray() && !issues.isEmpty();
    }

    public String extrairIssueKey(String createResponseBody) {
        try {
            JsonNode root = objectMapper.readTree(createResponseBody);
            JsonNode key = root.get("key");
            return key != null ? key.asText() : createResponseBody;
        } catch (Exception e) {
            return createResponseBody;
        }
    }

    private static String normalizarBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static String escaparJql(String valor) {
        return valor == null ? "" : valor.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class JiraRateLimitException extends RuntimeException {
        public JiraRateLimitException(String message) {
            super(message);
        }
    }
}
