package org.folio.copycat;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.impl.ImporterMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class RecordImporterTest {
  private static final Logger log = LogManager.getLogger(JsonMarcTest.class);
  private static ImporterMock mock;
  private static final int port = 9231; // where mock is running
  private static JsonObject marc1;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) throws IOException {
    String file = new String(RecordImporterTest.class.getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    marc1 = new JsonObject(file);
    mock = new ImporterMock(vertx);
    mock.start(port).onComplete(context.succeeding(res -> context.completeNow()));
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext context) {
    log.debug("Vertx {}", vertx);
    mock.stop().onComplete(context.succeeding(res -> context.completeNow()));
  }

  @Test
  void testOK(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    Future<List<String>> future = importer.begin("1234", "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.succeeding(x -> {
      assertThat(x).containsExactly(mock.getInstanceId());
      assertThat(mock.getLastJobProfileJobId()).isEqualTo("1234");
      context.completeNow();
    }));
  }

  @Test
  void testBadUserId(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, "1234");

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.begin(null, "import").onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Invalid UUID string: 1234");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlBegin(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.begin(null, "import").onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      assertThat(cause.getMessage()).contains("/change-manager/jobExecutions");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlPutProfile(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.putJobProfile("id", "import").onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      assertThat(cause.getMessage()).contains("/change-manager/jobExecutions/");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlPost(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.post(new JsonObject()).onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      assertThat(cause.getMessage()).contains("/change-manager/jobExecutions/");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlEnd(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.end().onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      assertThat(cause.getMessage()).contains("/change-manager/jobExecutions/");
      context.completeNow();
    })));
  }

  @Test
  void testBadUrlGetSourceStorage(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + (port + 1)); // nothing running
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    importer.getSourceRecords1().onComplete(context.failing(cause -> context.verify(() -> {
      assertThat(cause.getMessage()).contains("Connection refused");
      assertThat(cause.getMessage()).contains("/source-storage/source-records");
      context.completeNow();
    })));
  }

  @Test
  void testBadStatusCreate(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID,  UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    mock.setCreateStatus(204);
    importer.begin(null, "import").onComplete(context.failing(cause -> context.verify(() -> {
      mock.setCreateStatus(201);
      assertThat(cause.getMessage()).contains("returned 204");
      context.completeNow();
    })));
  }

  @Test
  void testBadStatusImport(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    mock.setImportStatus(201);
    Future<Void> future = importer.begin(null, "import")
        .compose(x -> importer.post(marc1));
    future.onComplete(context.failing(cause -> context.verify(() -> {
      mock.setImportStatus(204);
      assertThat(cause.getMessage()).contains("returned 201");
      context.completeNow();
    })));
  }

  @Test
  void testBadStatusPutProfile(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    mock.setPutProfileStatus(201);
    Future<Void> future = importer.begin(null, "import")
        .compose(x -> importer.post(marc1));
    future.onComplete(context.failing(cause -> context.verify(() -> {
      mock.setPutProfileStatus(200);
      assertThat(cause.getMessage()).contains("returned 201");
      context.completeNow();
    })));
  }

  @Test
  void testImporterWithMissingInstanceId(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());
    // rapid retry
    importer.setStoragePollWait(1);

    JsonObject obj = new JsonObject()
        .put("sourceRecords",
            new JsonArray().add(
                new JsonObject().put("externalIdsHolder",
                    new JsonObject().put("other", "val"))
            )
        );
    mock.setSourceStorageResponse(obj.encode());

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.succeeding(x -> {
      assertThat(x).containsExactly(mock.getInstanceId());
      assertThat(mock.getLastJobProfileJobId()).isEqualTo(jobProfileId);
      context.completeNow();
    }));
  }

  @Test
  void testImporterWithEmptySourceRecords(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());
    // rapid retry
    importer.setStoragePollWait(1);

    JsonObject obj = new JsonObject()
        .put("sourceRecords",
            new JsonArray()
        );
    mock.setSourceStorageResponse(obj.encode());

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.succeeding(x -> {
      assertThat(x).isEmpty();
      assertThat(mock.getLastJobProfileJobId()).isEqualTo(jobProfileId);
      context.completeNow();
    }));
  }

  @Test
  void testImporterWithTwoSourceRecords(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    JsonObject obj = new JsonObject()
        .put("sourceRecords",
            new JsonArray().add(
                new JsonObject().put("externalIdsHolder",
                    new JsonObject().put("instanceId", "id1")
                ))
            .add(
                new JsonObject().put("externalIdsHolder",
                    new JsonObject().put("instanceId", "id2")
                ))
            );
    mock.setSourceStorageResponse(obj.encode());

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.succeeding(x -> {
      assertThat(x).containsExactly("id1", "id2");
      assertThat(mock.getLastJobProfileJobId()).isEqualTo(jobProfileId);
      context.completeNow();
    }));
  }

  @Test
  void testImporterWithIterations(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    // rapid retry and the mock returns after 3 iterations (rather than immediately)
    importer.setStoragePollWait(1);
    mock.setIterations(3);

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.succeeding(x -> {
      assertThat(x).containsExactly(mock.getInstanceId());
      assertThat(mock.getLastJobProfileJobId()).isEqualTo(jobProfileId);
      context.completeNow();
    }));
  }

  @Test
  void testImporterRetryLimit(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());
    // rapid retry and try only 2 times before giving up
    importer.setStoragePollWait(1);
    importer.setStoragePollIterations(2);
    mock.setIterations(3);

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.failing(cause -> {
      assertThat(cause.getMessage()).contains("Did not get any instances after 2 retries");
      context.completeNow();
    }));
  }

  @Test
  void testImporterBadStatusSourceStorage(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    mock.setSourceRecordStorageStatus(400);

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.failing(cause -> {
      assertThat(cause.getMessage()).contains("returned 400 (expected 200)");
      context.completeNow();
    }));
  }

  @Test
  void testImporterSourceRecordStorageBadResponse(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    mock.setSourceStorageResponse("{");

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.failing(cause -> {
      assertThat(cause.getMessage()).contains("Failed to decode");
      context.completeNow();
    }));
  }

  @Test
  void testImporterSourceRecordStorageMissingSourceRecordsProperty(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    mock.setSourceStorageResponse("{}");

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.failing(cause -> {
      assertThat(cause.getMessage()).isEqualTo("Missing \"sourceRecords\" in response");
      context.completeNow();
    }));
  }

  @Test
  void testImporterSourceRecordStorageBadObjectInList(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new HashMap<>();

    headers.put(XOkapiHeaders.URL, "http://localhost:" + port);
    headers.put(XOkapiHeaders.TENANT, "testlib");
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    RecordImporter importer = new RecordImporter(headers, vertx.getOrCreateContext());

    JsonObject obj = new JsonObject()
        .put("sourceRecords",
            new JsonArray().add(1) // Integer in array and not JsonObject
        );
    mock.setSourceStorageResponse(obj.encode());

    String jobProfileId = UUID.randomUUID().toString();
    Future<List<String>> future = importer.begin(jobProfileId, "import")
        .compose(x -> importer.post(marc1))
        .compose(x -> importer.end());
    future.onComplete(context.failing(cause -> {
      assertThat(cause.getMessage()).contains("class java.lang.Integer cannot be cast to class io.vertx.core.json.JsonObject");
      context.completeNow();
    }));
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

    mock.setWaitMs(1000);
    importer.begin(null, "import").onComplete(context.failing(cause -> context.verify(() -> {
      mock.setWaitMs(1);
      assertThat(cause.getMessage()).contains("Connection was closed");
      context.completeNow();
    })));
  }

}
