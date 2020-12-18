package com.netflix.spinnaker.igor.concourse.client.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Setter
@EqualsAndHashCode(of = "id")
public class ResourceVersion {
  @Getter private Long id;
  @Getter private Map<String, String> version;
  @Getter private boolean enabled;

  @Nullable private List<Map<String, String>> metadata;

  @Nullable
  public Map<String, String> getMetadata() {
    return metadata == null
        ? null
        : metadata.stream().collect(Collectors.toMap(m -> m.get("name"), m -> m.get("value")));
  }
}
