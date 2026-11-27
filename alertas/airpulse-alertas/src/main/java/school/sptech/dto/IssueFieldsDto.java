package school.sptech.dto;

import java.util.List;

public class IssueFieldsDto {

    private ProjectDto project;
    private String summary;
    private IssueTypeDto issuetype;
    private List<String> labels;

    public IssueFieldsDto() {
    }

    public IssueFieldsDto(ProjectDto project, String summary, IssueTypeDto issuetype) {
        this.project = project;
        this.summary = summary;
        this.issuetype = issuetype;
    }

    public IssueFieldsDto(ProjectDto project, String summary, IssueTypeDto issuetype, List<String> labels) {
        this.project = project;
        this.summary = summary;
        this.issuetype = issuetype;
        this.labels = labels;
    }

    public ProjectDto getProject() {
        return project;
    }

    public void setProject(ProjectDto project) {
        this.project = project;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public IssueTypeDto getIssuetype() {
        return issuetype;
    }

    public void setIssuetype(IssueTypeDto issuetype) {
        this.issuetype = issuetype;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }
}
