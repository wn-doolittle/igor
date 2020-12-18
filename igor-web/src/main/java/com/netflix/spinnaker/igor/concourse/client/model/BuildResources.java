package com.netflix.spinnaker.igor.concourse.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;

@Data
@EqualsAndHashCode(of = "id")
public class BuildResources {

  private List<BuildResource> outputs;

  @Data
  public static class BuildResource {
    String name;
    Map<String, String> version;
  }
}
