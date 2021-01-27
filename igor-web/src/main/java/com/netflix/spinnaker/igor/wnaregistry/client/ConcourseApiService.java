package com.netflix.spinnaker.igor.wnaregistry.client;

import com.netflix.spinnaker.igor.wnaregistry.client.model.Job;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Pipeline;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Resource;
import com.netflix.spinnaker.igor.wnaregistry.client.model.Team;
import java.util.List;
import retrofit.http.GET;
import retrofit.http.Path;

public interface ConcourseApiService {
  @GET("/api/v1/teams")
  List<Team> teams();

  @GET("/api/v1/pipelines")
  List<Pipeline> pipelines();

  @GET("/api/v1/jobs")
  List<Job> jobs();

  @GET("/api/v1/teams/{team}/pipelines/{pipeline}/resources")
  List<Resource> resources(@Path("team") String team, @Path("pipeline") String pipeline);
}
