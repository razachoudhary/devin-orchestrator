package com.cognition.devinops.config;

import java.time.Duration;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private static ClientHttpRequestFactory requestFactory() {
        return ClientHttpRequestFactories.get(SimpleClientHttpRequestFactory.class,
                ClientHttpRequestFactorySettings.DEFAULTS
                        .withConnectTimeout(Duration.ofSeconds(5))
                        .withReadTimeout(Duration.ofSeconds(30)));
    }

    @Bean
    public RestClient devinRestClient(DevinProperties devinProperties) {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .baseUrl(devinProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + devinProperties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                .build();
    }

    @Bean
    public RestClient gitHubRestClient(GitHubProperties gitHubProperties) {
        return RestClient.builder()
                .requestFactory(requestFactory())
                .baseUrl(gitHubProperties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + gitHubProperties.token())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .build();
    }
}
