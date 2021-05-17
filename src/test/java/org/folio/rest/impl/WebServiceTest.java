package org.folio.rest.impl;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.RestVerticle;
import org.folio.rest.client.TenantClient;
import org.folio.rest.jaxrs.model.Parameter;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.utils.TenantInit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class WebServiceTest {
  private static final Logger log = LogManager.getLogger(CopycatImpl.class);

  private static final String OCLC_WORLDCAT_ID = "f26df83c-aa25-40b6-876e-96852c3d4fd4";
  private static final String TENANT = "tenant";
  private static final int mockPort = 9231;
  private static final int port = 9230;
  private static ImporterMock mock;
  private static WebClient webClient;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    DeploymentOptions options = new DeploymentOptions()
      .setConfig(
      new JsonObject()
        .put("http.port", port));

    webClient = WebClient.create(vertx);
    mock = new ImporterMock(vertx);
    vertx.deployVerticle(RestVerticle.class.getName(), options)
      .compose(a -> mock.start(mockPort))
      .onComplete(context.succeeding(x -> {
        log.debug("beforeAll completed");
        context.completeNow();
      }));
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext context) {
    webClient.close();
    vertx.close(context.succeeding(x -> {
      log.debug("afterAll completed");
      context.completeNow();
    }));
  }

  @Test
  void testReferenceDataLoaded(Vertx vertx, VertxTestContext context) {
    TenantClient tenantClient = new TenantClient("http://localhost:" + port, TENANT, null);
    TenantAttributes tenantAttributes = new TenantAttributes()
      .withModuleTo("mod-copycat-1.0.0")
      .withParameters(Collections.singletonList(new Parameter().withKey("loadReference").withValue("true")));

    TenantInit.exec(tenantClient, tenantAttributes, 60000)
      .compose(res -> webClient.get(port, "localhost", "/copycat/profiles")
        .putHeader(XOkapiHeaders.TENANT, TENANT)
        .send())
      .compose(res -> {
        assertThat(res.statusCode()).isEqualTo(200);
        JsonObject bodyResponse = res.bodyAsJsonObject();
        assertThat(bodyResponse.getInteger("totalRecords")).isEqualTo(2); // number of reference records
        return Future.succeededFuture();
      })

      .compose(res ->
        webClient.get(port, "localhost", "/copycat/profiles/" + OCLC_WORLDCAT_ID)
          .putHeader(XOkapiHeaders.TENANT, TENANT)
          .send())
      .compose(res -> {
        assertThat(res.statusCode()).isEqualTo(200);
        JsonObject oclcProfile = res.bodyAsJsonObject().put("authentication", "foo/bar");
        return webClient.put(port, "localhost", "/copycat/profiles/" + OCLC_WORLDCAT_ID)
          .putHeader(XOkapiHeaders.TENANT, TENANT)
          .putHeader("Content-Type", "application/json")
          .sendJson(oclcProfile);
      })
      .compose(res -> {
        assertThat(res.statusCode()).isEqualTo(204);
        tenantAttributes.setModuleFrom("mod-copycat-1.0.0");
        tenantAttributes.setModuleTo("mod-copycat-1.0.1");
        return TenantInit.exec(tenantClient, tenantAttributes, 60000);
      })

      .compose(res ->
        webClient.get(port, "localhost", "/copycat/profiles/" + OCLC_WORLDCAT_ID)
          .putHeader(XOkapiHeaders.TENANT, TENANT)
          .send())
      .compose(res -> {
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.bodyAsJsonObject().getString("authentication")).isEqualTo("foo/bar");
        return Future.succeededFuture();
      })
      .compose(res -> TenantInit.purge(tenantClient, 60000))
      .onComplete(context.succeeding(x -> context.completeNow())
      );
  }
}
