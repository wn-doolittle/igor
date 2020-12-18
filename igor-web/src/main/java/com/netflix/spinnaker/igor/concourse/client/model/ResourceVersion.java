package com.netflix.spinnaker.igor.concourse.client.model;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Setter
@EqualsAndHashCode(of = "id")
public class ResourceVersion {
  @Getter private Long id;
  @Getter private Map<String, String> version;
  @Getter private boolean enabled;

  private List<Map<String, String>> metadata;

  public Map<String, String> getMetadata() {
    return metadata == null
        ? null
        : metadata.stream().collect(toMap(m -> m.get("name"), m -> m.get("value")));
  }
}
