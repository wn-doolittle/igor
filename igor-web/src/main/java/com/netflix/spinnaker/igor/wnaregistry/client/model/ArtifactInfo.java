package com.netflix.spinnaker.igor.wnaregistry.client.model;

import lombok.Data;

@Data
public class ArtifactInfo {

  private String type;
  private String url;
}
