package org.folio.rest.impl;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test the Dockerfile, shaded jar, PostgreSQL and native yaz4j library during mvn verify.
 */
@Testcontainers
class CopyCatIT {
  private static final Network NETWORK = Network.newNetwork();

  @Container
  @SuppressWarnings("resource") // Testcontainers manages the container lifecycle.
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
      Objects.toString(System.getenv("TESTCONTAINERS_POSTGRES_IMAGE"), "postgres:16-alpine")))
      .withNetwork(NETWORK)
      .withNetworkAliases("postgres")
      .withUsername("username")
      .withPassword("password")
      .withDatabaseName("postgres");

  @Container
  @SuppressWarnings("resource") // Testcontainers manages the container lifecycle.
  private static final GenericContainer<?> MOD_COPYCAT = new GenericContainer<>(
      new ImageFromDockerfile().withFileFromPath(".", Path.of(".")))
      .dependsOn(POSTGRES)
      .withNetwork(NETWORK)
      .withNetworkAliases("mod-copycat")
      .withExposedPorts(8081)
      .withEnv("DB_HOST", "postgres")
      .withEnv("DB_PORT", "5432")
      .withEnv("DB_USERNAME", POSTGRES.getUsername())
      .withEnv("DB_PASSWORD", POSTGRES.getPassword())
      .withEnv("DB_DATABASE", POSTGRES.getDatabaseName())
      .withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(CopyCatIT.class)))
      // An HTTP wait also works with the shell-free runtime image.
      .waitingFor(Wait.forHttp("/admin/health").forStatusCode(200))
      .withStartupTimeout(Duration.ofMinutes(2));

  @AfterAll
  static void afterAll() {
    MOD_COPYCAT.stop();
    POSTGRES.stop();
    NETWORK.close();
  }

  private RequestSpecification request(String tenant) {
    var builder = new RequestSpecBuilder()
        .setBaseUri("http://" + MOD_COPYCAT.getHost())
        .setPort(MOD_COPYCAT.getMappedPort(8081))
        .setContentType(ContentType.JSON);
    if (tenant != null) {
      builder.addHeader("X-Okapi-Tenant", tenant)
          .addHeader("X-Okapi-Url", "http://mod-copycat:8081");
    }
    return given().spec(builder.build()).log().ifValidationFails();
  }

  @Test
  void health() {
    // Startup invokes Init, which creates and closes a native yaz4j Connection.
    request(null).get("/admin/health").then().log().ifValidationFails()
        .statusCode(200).body(is("\"OK\""));
  }

  private void postTenant(String tenant, JsonObject attributes) {
    String location = request(tenant).body(attributes.encode()).post("/_/tenant")
        .then().log().ifValidationFails().statusCode(201).extract().header("Location");
    request(tenant).get(location + "?wait=30000").then().log().ifValidationFails()
        .statusCode(200).body("complete", is(true)).body("error", nullValue());
  }

  private JsonObject profile(String tenant) {
    var profile = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("name", "Integration test")
        .put("createJobProfileId", UUID.randomUUID().toString())
        .put("updateJobProfileId", UUID.randomUUID().toString())
        .put("url", "127.0.0.1:1/marc")
        .put("externalIdQueryMap", "$identifier");
    request(tenant).body(profile.encode()).post("/copycat/profiles")
        .then().log().ifValidationFails().statusCode(201);
    return profile;
  }

  @Test
  void installAndUpgrade() {
    String tenant = "copycat_it";
    var attributes = new JsonObject().put("module_to", "mod-copycat-999999.0.0");
    postTenant(tenant, attributes);
    JsonObject profile = profile(tenant);
    String path = "/copycat/profiles/" + profile.getString("id");

    // Re-run migrations and verify that existing data survives.
    postTenant(tenant, attributes.put("module_from", "mod-copycat-0.0.0"));
    request(tenant).get(path).then().log().ifValidationFails().statusCode(200)
        .body("name", is("Integration test")).body("url", is("127.0.0.1:1/marc"));

    profile.put("name", "Updated profile");
    request(tenant).body(profile.encode()).put(path)
        .then().log().ifValidationFails().statusCode(204);
    request(tenant).get(path).then().log().ifValidationFails().statusCode(200)
        .body("name", is("Updated profile"));
    request(tenant).delete(path).then().log().ifValidationFails().statusCode(204);
    request(tenant).get(path).then().log().ifValidationFails().statusCode(404);
  }

  @Test
  void nativeConnectionFailure() {
    String tenant = "copycat_native_it";
    postTenant(tenant, new JsonObject().put("module_to", "mod-copycat-999999.0.0"));
    JsonObject profile = profile(tenant);
    // No service listens on port 1 inside this container. YAZ must report the
    // connection failure, without an external Z39.50 server or other FOLIO modules.
    var body = new JsonObject().put("profileId", profile.getString("id"))
        .put("externalIdentifier", "1234");
    request(tenant).body(body.encode()).post("/copycat/imports")
        .then().log().ifValidationFails().statusCode(400)
        .body("errors[0].message", is("Z39.50 error: Connection could not be made to 127.0.0.1:1/marc"));
  }
}
