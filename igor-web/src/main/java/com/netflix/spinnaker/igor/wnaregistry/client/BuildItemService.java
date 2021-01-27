package com.netflix.spinnaker.igor.wnaregistry.client;

import com.netflix.spinnaker.igor.wnaregistry.client.model.BuildItem;
import java.util.List;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

public interface BuildItemService {
  @GET("/api/v1/builds")
  List<BuildItem> builds(@Query("since") Long since);

  @GET("/api/v1/teams/{team}/pipelines/{pipeline}/jobs/{job}/builds")
  List<BuildItem> builds(
      @Path("team") String team, @Path("pipeline") String pipeline, @Path("job") String job);

  @GET("/api/v1/teams/{team}/pipelines/{pipeline}/jobs/{job}/builds/{build}")
  BuildItem build(
      @Path("team") String team,
      @Path("pipeline") String pipeline,
      @Path("job") String job,
      @Path("build") String build);
}
