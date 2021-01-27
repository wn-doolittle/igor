package com.netflix.spinnaker.igor.wnaregistry.client.model;

import lombok.Data;

@Data
public class GitInfo {

  private String sha1;
  private String branch;
  private String message;

  private String committer;
  private Long committerDate;
}
