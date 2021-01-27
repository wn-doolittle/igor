package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.wnaregistry.WnaRegistryCache;
import com.netflix.spinnaker.igor.wnaregistry.service.WnaRegistryService;
import com.netflix.spinnaker.igor.service.ArtifactDecorator;
import com.netflix.spinnaker.igor.service.BuildServices;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("wnaregistry.enabled")
@EnableConfigurationProperties(WnaRegistryProperties.class)
public class WnaRegistryConfig {
  @Bean
  public Map<String, WnaRegistryService> wnaRegistryMasters(
      BuildServices buildServices,
      WnaRegistryCache wnaRegistryCache,
      Optional<ArtifactDecorator> artifactDecorator,
      IgorConfigurationProperties igorConfigurationProperties,
      @Valid WnaRegistryProperties wnaRegistryProperties) {
    List<WnaRegistryProperties.Host> masters = wnaRegistryProperties.getMasters();
    if (masters == null) {
      return Collections.emptyMap();
    }

    Map<String, WnaRegistryService> wnaRegistryMasters =
        masters.stream()
            .map(m -> new WnaRegistryService(m, artifactDecorator))
            .collect(Collectors.toMap(WnaRegistryService::getMaster, Function.identity()));

    buildServices.addServices(wnaRegistryMasters);
    return wnaRegistryMasters;
  }
}
