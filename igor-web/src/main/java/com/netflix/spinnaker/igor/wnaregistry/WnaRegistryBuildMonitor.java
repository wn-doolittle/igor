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

package com.netflix.spinnaker.igor.wnaregistry;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.build.model.GenericBuild;
import com.netflix.spinnaker.igor.build.model.GenericProject;
import com.netflix.spinnaker.igor.config.WnaRegistryProperties;
import com.netflix.spinnaker.igor.history.EchoService;
import com.netflix.spinnaker.igor.history.model.GenericBuildContent;
import com.netflix.spinnaker.igor.history.model.GenericBuildEvent;
import com.netflix.spinnaker.igor.polling.CommonPollingMonitor;
import com.netflix.spinnaker.igor.polling.DeltaItem;
import com.netflix.spinnaker.igor.polling.LockService;
import com.netflix.spinnaker.igor.polling.PollContext;
import com.netflix.spinnaker.igor.polling.PollingDelta;
import com.netflix.spinnaker.igor.service.BuildServices;
import com.netflix.spinnaker.igor.wnaregistry.client.model.BuildItem;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Job;
import com.netflix.spinnaker.igor.wnaregistry.service.WnaRegistryService;
import com.netflix.spinnaker.kork.discovery.DiscoveryStatusListener;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty("wnaregistry.enabled")
@Slf4j
public class WnaRegistryBuildMonitor
    extends CommonPollingMonitor<
        WnaRegistryBuildMonitor.JobDelta, WnaRegistryBuildMonitor.JobPollingDelta> {
  private final BuildServices buildServices;
  private final WnaRegistryCache cache;
  private final WnaRegistryProperties wnaRegistryProperties;
  private final Optional<EchoService> echoService;

  public WnaRegistryBuildMonitor(
      IgorConfigurationProperties properties,
      Registry registry,
      DynamicConfigService dynamicConfigService,
      DiscoveryStatusListener discoveryStatusListener,
      Optional<LockService> lockService,
      Optional<EchoService> echoService,
      BuildServices buildServices,
      WnaRegistryCache cache,
      WnaRegistryProperties wnaRegistryProperties,
      TaskScheduler scheduler) {
    super(
        properties,
        registry,
        dynamicConfigService,
        discoveryStatusListener,
        lockService,
        scheduler);
    this.buildServices = buildServices;
    this.cache = cache;
    this.wnaRegistryProperties = wnaRegistryProperties;
    this.echoService = echoService;
  }

  @Override
  protected JobPollingDelta generateDelta(PollContext ctx) {
    WnaRegistryProperties.Host host =
        wnaRegistryProperties.getMasters().stream()
            .filter(h -> h.getName().equals(ctx.partitionName))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Unable to find wnaRegistry host with name '" + ctx.partitionName + "'"));

    WnaRegistryService wnaRegistryService = getService(host);

    final Long lastPollTs = cache.getLastPollCycleTimestamp(host);

    List<BuildItem> builds = wnaRegistryService.getBuilds(lastPollTs);

    long lastBuildStamp = builds.iterator().next().getBuildTimestamp();

    if (lastPollTs == null && !igorProperties.getSpinnaker().getBuild().isHandleFirstBuilds()) {
      cache.setLastPollCycleTimestamp(host, lastBuildStamp);
      return new JobPollingDelta(host.getName(), Collections.emptyList());
    }

    if (!igorProperties.getSpinnaker().getBuild().isProcessBuildsOlderThanLookBackWindow()) {
      builds = onlyInLookBackWindow(builds);
    }

    ListMultimap<Job, BuildItem> buildsByJob = Multimaps.index(builds, BuildItem::getJobKey);

    List<JobDelta> jobDeltas =
        buildsByJob.asMap().entrySet().stream()
            .map(
                jobBuilds -> {
                  Date upperBound = new Date(lastBuildStamp);
                  long cursor = lastPollTs == null ? lastBuildStamp : lastPollTs;
                  Date lowerBound = new Date(cursor);

                  return new JobDelta(
                      host,
                      jobBuilds.getKey(),
                      cursor,
                      lowerBound,
                      upperBound,
                      jobBuilds.getValue().stream()
                          .filter(
                              b ->
                                  !cache.getEventPosted(host, b.getJobKey(), cursor, b.getNumber()))
                          .map(wnaRegistryService::getGenericBuild)
                          .collect(Collectors.toList()));
                })
            .collect(Collectors.toList());

    return new JobPollingDelta(host.getName(), jobDeltas);
  }

  private WnaRegistryService getService(WnaRegistryProperties.Host host) {
    return (WnaRegistryService) buildServices.getService("concourse-" + host.getName());
  }

  private List<BuildItem> onlyInLookBackWindow(List<BuildItem> builds) {
    long lookbackDate =
        System.currentTimeMillis()
            - (getPollInterval()
                + (igorProperties.getSpinnaker().getBuild().getLookBackWindowMins() * 60) * 1000);
    return builds.stream()
        .filter(b -> b.getBuildTimestamp() > lookbackDate)
        .collect(Collectors.toList());
  }

  @Override
  protected void commitDelta(JobPollingDelta delta, boolean sendEvents) {
    for (JobDelta jobDelta : delta.items) {
      for (GenericBuild build : jobDelta.getBuilds()) {
        boolean eventPosted =
            cache.getEventPosted(
                jobDelta.getHost(), jobDelta.getJob(), jobDelta.getCursor(), build.getNumber());
        if (!eventPosted && sendEvents) {
          sendEventForBuild(jobDelta.getHost(), jobDelta.getJob(), build);
        }
        log.info(
            "({}) caching build {} for : {}",
            jobDelta.getHost().getName(),
            build.getNumber(),
            build.getFullDisplayName());
        cache.setEventPosted(
            jobDelta.getHost(), jobDelta.getJob(), jobDelta.getCursor(), build.getNumber());
      }
      cache.setLastPollCycleTimestamp(jobDelta.getHost(), jobDelta.getCursor());
    }
  }

  private void sendEventForBuild(WnaRegistryProperties.Host host, Job job, GenericBuild build) {
    if (echoService.isPresent()) {
      log.info(
          "({}) pushing build {} for : {}",
          host.getName(),
          build.getNumber(),
          build.getFullDisplayName());

      GenericProject project =
          new GenericProject(
              job.getTeamName() + "/" + job.getPipelineName() + "/" + job.getName(), build);

      GenericBuildContent content = new GenericBuildContent();
      content.setProject(project);
      content.setMaster("concourse-" + host.getName());
      content.setType("concourse");

      GenericBuildEvent event = new GenericBuildEvent();
      event.setContent(content);

      AuthenticatedRequest.allowAnonymous(() -> echoService.get().postEvent(event));
    } else {
      log.warn("Cannot send build event notification: Echo is not configured");
      log.info("({}) unable to push event for :" + build.getFullDisplayName());
      registry.counter(missedNotificationId.withTag("monitor", getName())).increment();
    }
  }

  @Override
  public void poll(boolean sendEvents) {
    for (WnaRegistryProperties.Host host : wnaRegistryProperties.getMasters()) {
      pollSingle(new PollContext(host.getName(), !sendEvents));
    }
  }

  @Override
  public String getName() {
    return "wnaRegistryBuildMonitor";
  }

  @RequiredArgsConstructor
  @Getter
  static class JobDelta implements DeltaItem {
    private final WnaRegistryProperties.Host host;
    private final Job job;
    private final Long cursor;
    private final Date lowerBound;
    private final Date upperBound;
    private final List<GenericBuild> builds;
  }

  @RequiredArgsConstructor
  @Getter
  static class JobPollingDelta implements PollingDelta<JobDelta> {
    private final String name;
    private final List<JobDelta> items;
  }
}
