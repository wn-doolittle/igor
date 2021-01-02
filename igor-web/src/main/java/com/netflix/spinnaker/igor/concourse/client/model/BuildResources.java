package com.netflix.spinnaker.igor.concourse.client.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class BuildResources {

  private List<BuildResource> inputs;
  private List<BuildResource> outputs;

  public List<BuildResource> getAll() {
    List<BuildResource> all = new ArrayList<>(inputs);
    all.addAll(outputs);
    return all;
  }

  @Data
  public static class BuildResource {
    String name;
    Map<String, String> version;
  }
}
