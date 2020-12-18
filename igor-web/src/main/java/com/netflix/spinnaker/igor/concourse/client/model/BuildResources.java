package com.netflix.spinnaker.igor.concourse.client.model;

import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class BuildResources {

  private List<BuildResource> outputs;

  @Data
  public static class BuildResource {
    String name;
    Map<String, String> version;
  }
}
