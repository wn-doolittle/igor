/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.wnaregistry.service;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.reducing;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.igor.build.model.GenericArtifact;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericGitRevision;
import com.netflix.spinnaker.igor.build.model.JobConfiguration;
import com.netflix.spinnaker.igor.build.model.Result;
import com.netflix.spinnaker.igor.wnaregistry.client.WnaRegistryClient;
import com.netflix.spinnaker.igor.wnaregistry.client.model.ArtifactInfo;
import com.netflix.spinnaker.igor.wnaregistry.client.model.BuildItem;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Job;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Pipeline;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Resource;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Team;
import com.netflix.spinnaker.igor.config.WnaRegistryProperties;
import com.netflix.spinnaker.igor.model.BuildServiceProvider;
import com.netflix.spinnaker.igor.service.ArtifactDecorator;
import com.netflix.spinnaker.igor.service.BuildOperations;
import com.netflix.spinnaker.igor.service.BuildProperties;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

@Slf4j
public class WnaRegistryService implements BuildOperations, BuildProperties {
  private final WnaRegistryProperties.Host host;
  private final WnaRegistryClient client;
  private final Optional<ArtifactDecorator> artifactDecorator;
  private final Permissions permissions;

  @Nullable private final Pattern resourceFilter;

  public WnaRegistryService(
      WnaRegistryProperties.Host host, Optional<ArtifactDecorator> artifactDecorator) {
    this(
        new WnaRegistryClient(host.getUrl(), host.getUsername(), host.getPassword()),
        host,
        artifactDecorator);
  }

  protected WnaRegistryService(
      WnaRegistryClient client,
      WnaRegistryProperties.Host host,
      Optional<ArtifactDecorator> artifactDecorator) {
    this.host = host;
    this.client = client;
    this.resourceFilter =
        host.getResourceFilterRegex() == null
            ? null
            : Pattern.compile(host.getResourceFilterRegex());
    this.artifactDecorator = artifactDecorator;
    this.permissions = host.getPermissions().build();
  }

  public String getMaster() {
    return "concourse-" + host.getName()
  }

  @Override
  public String getName() {
    return getMaster();
  }

  @Override
  public BuildServiceProvider getBuildServiceProvider() {
    return BuildServiceProvider.CONCOURSE;
  }

  @Override
  public Permissions getPermissions() {
    return permissions;
  }

  public Collection<Team> teams() {
    return client.getConcourseApiService().teams().stream()
        .filter(team -> host.getTeams() == null || host.getTeams().contains(team.getName()))
        .collect(toList());
  }

  public Collection<Pipeline> pipelines() {
    return client.getConcourseApiService().pipelines().stream()
        .filter(
            pipeline -> host.getTeams() == null || host.getTeams().contains(pipeline.getTeamName()))
        .collect(toList());
  }

  public Collection<Job> getJobs() {
    return client.getConcourseApiService().jobs().stream()
        .filter(job -> host.getTeams() == null || host.getTeams().contains(job.getTeamName()))
        .collect(toList());
  }

  @Override
  public List<GenericGitRevision> getGenericGitRevisions(String jobPath, GenericBuild build) {
    return build == null ? emptyList() : build.getGenericGitRevisions();
  }

  @Override
  public Map<String, ?> getBuildProperties(String jobPath, GenericBuild build, String fileName) {
    return build == null ? emptyMap() : build.getProperties();
  }

  @Nullable
  @Override
  public GenericBuild getGenericBuild(String jobPath, int buildNumber) {
    return getBuild(jobPath, buildNumber)
        .map(build -> getGenericBuild(build))
        .orElse(null);
  }

  public GenericBuild getGenericBuild(BuildItem b) {
    Job job = b.getJobKey();

    GenericBuild build = new GenericBuild();
    build.setId(b.getBuildId().toString());
    build.setBuilding(false);
    build.setNumber(b.getNumber());
    build.setResult(Result.SUCCESS);
    build.setName(job.getName());
    build.setFullDisplayName(job.getTeamName() + "/" + job.getPipelineName() + "/" + job.getName());
    build.setUrl(
        host.getUrl()
            + "/teams/"
            + job.getTeamName()
            + "/pipelines/"
            + job.getPipelineName()
            + "/jobs/"
            + job.getName()
            + "/builds/"
            + b.getBuildId());
    build.setTimestamp(Long.toString(b.getBuildTimestamp()));

    if(b.getGitInfo() != null) {
        build.setGenericGitRevisions(
            Collections.singletonList(
                GenericGitRevision.builder()
                    .committer(b.getGitInfo().getCommitter())
                    .branch(b.getGitInfo().getBranch())
                    .name(b.getGitInfo().getBranch())
                    .message(b.getGitInfo().getMessage())
                    .sha1(b.getGitInfo().getSha1())
                    .timestamp(b.getGitInfo().getCommitterDate() == null ? null : 
                        Instant.ofEpochMilli(b.getGitInfo().getCommitterDate()))
                    .build()));
    }

//    if (!mergedMetadataByResourceName.isEmpty()) {
//      build.setProperties(mergedMetadataByResourceName);
//    }

    if(b.getArtifacts() != null) {
        parseAndDecorateArtifacts(build, b.getArtifacts());
    }

    return build;
  }

  private void parseAndDecorateArtifacts(GenericBuild build, Collection<ArtifactInfo> artifacts) {
    build.setArtifacts(getArtifactsFromResources(artifacts));
    artifactDecorator.ifPresent(decorator -> decorator.decorate(build));
  }

  private List<GenericArtifact> getArtifactsFromResources(Collection<ArtifactInfo> artifacts) {
    return artifacts.stream()
        .map(ArtifactInfo::getUrl)
        .filter(Objects::nonNull)
        .map(WnaRegistryService::translateS3HttpUrl)
        .map(url -> new GenericArtifact(url, url, url))
        .collect(Collectors.toList());
  }

  private static String translateS3HttpUrl(String url) {
    if (url.startsWith("https://s3-")) {
      url = "s3://" + url.substring(url.indexOf('/', 8) + 1);
    }
    return url;
  }

  @Override
  public int triggerBuildWithParameters(String job, Map<String, String> queryParameters) {
    throw new UnsupportedOperationException("Triggering concourse builds not supported");
  }

  @Override
  public List<GenericBuild> getBuilds(String jobPath) {
    Job job = toJob(jobPath);

    if (host.getTeams() != null && !host.getTeams().contains(job.getTeamName())) {
      return emptyList();
    }

    return client
        .getBuildItemService()
        .builds(
            job.getTeamName(),
            job.getPipelineName(),
            job.getName())
//            host.getBuildLookbackLimit(),
        .stream()
        .sorted()
        .collect(
            Collectors.toMap(
                (b) -> b.getNumber(), Function.identity(), (b1, b2) -> b1, LinkedHashMap::new))
        .values()
        .stream()
        .map(build -> getGenericBuild(build))
        .collect(Collectors.toList());
  }

  @Override
  public JobConfiguration getJobConfig(String jobName) {
    throw new UnsupportedOperationException("getJobConfig is not yet implemented for WnaRegistry");
  }

  public List<BuildItem> getBuilds(@Nullable Long since) {
    return client
        .getBuildItemService()
        .builds(since)
        .stream()
        .sorted()
        .collect(
            Collectors.toMap(
                (b) -> b.getNumber(), Function.identity(), (b1, b2) -> b1, LinkedHashMap::new))
        .values()
        .stream()
        .collect(Collectors.toList());
  }

  public Optional<BuildItem> getBuild(String jobPath, int buildNumber) {
    Job job = toJob(jobPath);

    if (host.getTeams() != null && !host.getTeams().contains(job.getTeamName())) {
      return Optional.empty();
    }

    return Optional.ofNullable(client.getBuildItemService()
        .build(
            job.getTeamName(),
            job.getPipelineName(),
            job.getName(),
            Integer.toString(buildNumber)));
  }

  public List<String> getResourceNames(String team, String pipeline) {
    return client.getConcourseApiService().resources(team, pipeline).stream()
        .map(Resource::getName)
        .collect(toList());
  }

  private Job toJob(String jobPath) {
    String[] jobParts = jobPath.split("/");
    if (jobParts.length != 3) {
      throw new IllegalArgumentException("job must be in the format teamName/pipelineName/jobName");
    }

    Job job = new Job();
    job.setTeamName(jobParts[0]);
    job.setPipelineName(jobParts[1]);
    job.setName(jobParts[2]);

    return job;
  }

}
