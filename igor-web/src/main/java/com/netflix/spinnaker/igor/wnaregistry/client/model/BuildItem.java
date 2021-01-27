package com.netflix.spinnaker.igor.wnaregistry.client.model;

import java.math.BigDecimal;
import java.util.List;
import lombok.Data;

@Data
public class BuildItem implements Comparable<BuildItem> {

    private String job;
    private BigDecimal buildId;
    private Long buildTimestamp;
    private String url;

    private GitInfo gitInfo;
    private List<ArtifactInfo> artifacts;

    public Job getJobKey() {
        String[] jobParts = job.split("#");
        if (jobParts.length != 3) {
            throw new IllegalArgumentException("job must be in the format teamName#pipelineName#jobName");
        }

        Job jobKey = new Job();
        jobKey.setTeamName(jobParts[0]);
        jobKey.setPipelineName(jobParts[1]);
        jobKey.setName(jobParts[2]);

        return jobKey;
    }

    public int getNumber() {
        return buildId.intValue();
    }

    @Override
    public int compareTo(BuildItem o) {
        return o.buildId.compareTo(buildId);
    }

}
