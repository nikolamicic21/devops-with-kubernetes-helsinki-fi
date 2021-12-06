package io.nikolamicic21.dummysitecontroller;

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.openapi.*;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.Watch;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DummysiteControllerApplication {

    private static final ApiClient API_CLIENT;
    private static final CoreV1Api CORE_V1_API_CLIENT;

    static {
        ApiClient _apiClient = null;
        CoreV1Api _coreApiClient = null;
        try {
            _apiClient = ClientBuilder.standard().build();
            _coreApiClient = new CoreV1Api(_apiClient);
        } catch (IOException exception) {
            System.out.println(exception.getMessage());
        }

        API_CLIENT = _apiClient;
        CORE_V1_API_CLIENT = _coreApiClient;
    }


    public static void main(String[] args) throws ApiException, IOException {
        final var watchDummySitesCall = API_CLIENT.buildCall(
                "/apis/nikolamicic21.io/v1/dummysites",
                "GET",
                List.of(new Pair("watch", "true")),
                List.of(),
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                new String[]{"BearerToken"},
                null
        );

        try (final var watch = Watch
                .<Map<String, Object>>createWatch(
                        API_CLIENT,
                        watchDummySitesCall,
                        new TypeToken<Watch.Response<Map<String, Object>>>() {
                        }.getType()
                )
        ) {
            for (Watch.Response<Map<String, Object>> item : watch) {
                switch (item.type) {
                    case "ADDED":
                        handleAddEvent(item.object);
                        break;
                    case "DELETED":
                        handleDeleteEvent(item.object);
                        break;
                }
            }
        }
    }

    private static void handleAddEvent(Map<String, Object> data) {
        try {
            final var namespace = getNamespace(data);
            final var websiteUrl = getWebsiteUrl(data);

            final var httpClient = new OkHttpClient.Builder()
                    .connectTimeout(1, TimeUnit.SECONDS)
                    .readTimeout(2, TimeUnit.SECONDS)
                    .build();
            final var request = new Request.Builder()
                    .url(websiteUrl)
                    .build();
            final var response = httpClient
                    .newCall(request)
                    .execute();
            final var responseBody = response.body().string();

            System.out.println("Adding ConfigMap 'dummysite-configmap'");
            final var configMap = new V1ConfigMapBuilder()
                    .withNewMetadata()
                    .withName("dummysite-configmap")
                    .withNamespace(namespace)
                    .endMetadata()
                    .withData(Map.of("index.html", responseBody))
                    .build();
            CORE_V1_API_CLIENT.createNamespacedConfigMap(
                    namespace,
                    configMap,
                    null,
                    null,
                    null
            );
            System.out.println("ConfigMap 'dummysite-configmap' was added");

            System.out.println("Adding Pod 'dummysite-pod'");
            V1Pod pod = new V1PodBuilder()
                    .withNewMetadata()
                    .withName("dummysite-pod")
                    .withNamespace(namespace)
                    .endMetadata()
                    .withNewSpec()
                    .addNewContainer()
                    .withName("dummysite-nginx")
                    .withImage("nginx:1.19-alpine")
                    .withVolumeMounts(
                            new V1VolumeMountBuilder()
                                    .withName("dummysite-volume")
                                    .withMountPath("/usr/share/nginx/html")
                                    .withReadOnly(true)
                                    .build()
                    )
                    .endContainer()
                    .addNewVolume()
                    .withName("dummysite-volume")
                    .withNewConfigMap()
                    .withName("dummysite-configmap")
                    .endConfigMap()
                    .endVolume()
                    .endSpec()
                    .build();
            CORE_V1_API_CLIENT.createNamespacedPod(
                    namespace,
                    pod,
                    null,
                    null,
                    null
            );
            System.out.println("Pod 'dummysite-pod' was added");
        } catch (ApiException | IOException exception) {
            System.out.println(exception.getMessage());
        }
    }

    private static void handleDeleteEvent(Map<String, Object> data) {
        try {
            final var namespace = getNamespace(data);

            System.out.println("Deleting Pod 'dummysite-pod'");
            CORE_V1_API_CLIENT.deleteNamespacedPod(
                    "dummysite-pod",
                    namespace,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            System.out.println("Pod 'dummysite-pod' was deleted");
            System.out.println("Deleting ConfigMap 'dummysite-configmap'");
            CORE_V1_API_CLIENT.deleteNamespacedConfigMap(
                    "dummysite-configmap",
                    namespace,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
            System.out.println("ConfigMap 'dummysite-configmap' was deleted");
        } catch (ApiException exception) {
            System.out.println(exception.getMessage());
        }
    }

    private static String getNamespace(Map<String, Object> data) {
        String namespace = null;
        if (data != null && data.containsKey("metadata")) {
            final var metadata = (Map<String, Object>) data.get("metadata");
            if (metadata != null && metadata.containsKey("namespace")) {
                namespace = (String) metadata.get("namespace");
            }
        }

        if (namespace == null) {
            throw new RuntimeException();
        }

        return namespace;
    }

    private static String getWebsiteUrl(Map<String, Object> data) {
        String websiteUrl = null;
        if (data != null && data.containsKey("spec")) {
            final var spec = (Map<String, Object>) data.get("spec");
            if (spec != null && spec.containsKey("websiteUrl")) {
                websiteUrl = (String) spec.get("websiteUrl");
            }
        }

        if (websiteUrl == null) {
            throw new RuntimeException();
        }

        return websiteUrl;
    }

}
