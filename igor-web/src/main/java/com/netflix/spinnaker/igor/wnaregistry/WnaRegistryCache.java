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

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.config.WnaRegistryProperties;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Job;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class WnaRegistryCache {
  private static final String ID = "wnaregistry:builds:queue";

  private static final String POLL_STAMP = "lastPollCycleTimestamp";

  private static final Job POLL_CYCLE_JOB_KEY = new Job("poll", "poll", "poll");

  private final RedisClientDelegate redisClientDelegate;
  private final IgorConfigurationProperties igorConfigurationProperties;

  public void setLastPollCycleTimestamp(WnaRegistryProperties.Host host, long timestamp) {
    String key = makeKey(host, POLL_CYCLE_JOB_KEY);
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, POLL_STAMP, Long.toString(timestamp));
        });
  }

  public Long getLastPollCycleTimestamp(WnaRegistryProperties.Host host) {
    return redisClientDelegate.withCommandsClient(
        c -> {
          String ts = c.hget(makeKey(host, POLL_CYCLE_JOB_KEY), POLL_STAMP);
          return ts == null ? null : Long.parseLong(ts);
        });
  }

  public boolean getEventPosted(
      WnaRegistryProperties.Host host, Job job, Long cursor, Integer buildNumber) {
    String key = makeKey(host, job) + ":" + POLL_STAMP + ":" + cursor;
    return redisClientDelegate.withCommandsClient(
        c -> c.hget(key, Integer.toString(buildNumber)) != null);
  }

  public void setEventPosted(
      WnaRegistryProperties.Host host, Job job, Long cursor, Integer buildNumber) {
    String key = makeKey(host, job) + ":" + POLL_STAMP + ":" + cursor;
    redisClientDelegate.withCommandsClient(
        c -> {
          c.hset(key, Integer.toString(buildNumber), "POSTED");
        });
  }

  private String makeKey(WnaRegistryProperties.Host host, Job job) {
    return prefix()
        + ":"
        + host.getName()
        + ":"
        + job.getTeamName()
        + ":"
        + job.getPipelineName()
        + ":"
        + job.getName();
  }

  private String prefix() {
    return igorConfigurationProperties.getSpinnaker().getJedis().getPrefix() + ":" + ID;
  }
}
