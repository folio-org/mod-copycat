package org.folio.copycat;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.impl.ImporterMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class RecordImporterTest {
  private static Logger log = LogManager.getLogger(JsonMarcTest.class);
  private static ImporterMock mock;
  private static int port = 9231; // where mock is running

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    mock = new ImporterMock(vertx);
    mock.start(port).onComplete(context.succeeding(res -> context.completeNow()));
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext context) {
    log.debug("Vertx {}", vertx);
    mock.stop().onComplete(context.succeeding(res -> context.completeNow()));
  }

  @Test
  void testOK(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);

    Future<Void> future = importer.begin()
        .compose(x -> importer.post(marc))
        .compose(x -> importer.end());
    future.onComplete(context.succeeding(x -> context.completeNow()));
  }

  @Test
  void testBadUserId(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();
    int port = 9231; // where mock is running

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, "1234");

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.begin().onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Invalid UUID string: 1234");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlBegin(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();
    int port = 9231; // where mock is running

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.begin().onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlPutProfile(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();
    int port = 9231; // where mock is running

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.putJobProfile().onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlPost(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();
    int port = 9231; // where mock is running

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.post(new JsonObject()).onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlEnd(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();
    int port = 9231; // where mock is running

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.end().onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      context.completeNow();
    })));
  }

  @Test
  void testBadStatusCreate(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();
    int port = 9231; // where mock is running

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    mock.setCreateStatus(204);
    importer.begin().onComplete(context.failing(cause -> context.verify(() -> {
      mock.setCreateStatus(201);
      assertThat(cause.getMessage()).contains("returned 204");
      context.completeNow();
    })));
  }

  @Test
  void testBadStatusImport(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);

    mock.setImportStatus(201);
    Future<Void> future = importer.begin()
        .compose(x -> importer.post(marc));
    future.onComplete(context.failing(cause -> context.verify(() -> {
      mock.setImportStatus(204);
      assertThat(cause.getMessage()).contains("returned 201");
      context.completeNow();
    })));
  }

  @Test
  void testBadStatusPutProfile(Vertx vertx, VertxTestContext context) throws IOException {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);

    mock.setPutProfileStatus(201);
    Future<Void> future = importer.begin()
        .compose(x -> importer.post(marc));
    future.onComplete(context.failing(cause -> context.verify(() -> {
      mock.setPutProfileStatus(200);
      assertThat(cause.getMessage()).contains("returned 201");
      context.completeNow();
    })));
  }

  @Test
  void testImporterTimeout(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    WebClientOptions options = new WebClientOptions();
    options.setIdleTimeout(1);
    options.setIdleTimeoutUnit(TimeUnit.MILLISECONDS);
    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext(), options);

    mock.setWaitMs(10);
    importer.begin().onComplete(context.failing(cause -> context.verify(() -> {
      mock.setWaitMs(1);
      assertThat(cause.getMessage()).contains("Connection was closed");
      context.completeNow();
    })));
  }

}
