package com.cognition.devinops.devin.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateSessionRequest(
        @JsonProperty("prompt") String prompt,
        @JsonProperty("playbook_id") String playbookId,
        @JsonProperty("knowledge_ids") List<String> knowledgeIds,
        @JsonProperty("max_acu_limit") Integer maxAcuLimit,
        @JsonProperty("structured_output_required") Boolean structuredOutputRequired,
        @JsonProperty("structured_output_schema") JsonNode structuredOutputSchema,
        @JsonProperty("tags") List<String> tags
) {

    public CreateSessionRequest withoutTags() {
        return new CreateSessionRequest(prompt, playbookId, knowledgeIds, maxAcuLimit,
                structuredOutputRequired, structuredOutputSchema, null);
    }
}
