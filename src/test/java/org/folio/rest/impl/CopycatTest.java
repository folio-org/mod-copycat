package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.postgres.testing.PostgresTesterContainer;
import org.folio.rest.jaxrs.model.CopyCatCollection;
import org.folio.rest.jaxrs.model.CopyCatImports;
import org.folio.rest.jaxrs.model.CopyCatProfile;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Record;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.jaxrs.resource.Copycat;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
class CopycatTest {
  static final String tenant = "testlib";

  private static final String URL_INDEXDATA = "z3950.indexdata.com/marc";
  private static final String EXTERNAL_ID_INDEXDATA = "780306m19009999ohu";
  private static final int mockPort = 9231;
  private static ImporterMock mock;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    PostgresClient.setPostgresTester(new PostgresTesterContainer());
    mock = new ImporterMock(vertx);
    mock.start(mockPort)
        .compose(x -> tenantInit(vertx, context)).onComplete(context.succeeding(res -> context.completeNow()));
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext context) {
    mock.stop().onComplete(context.succeeding(res -> context.completeNow()));
  }

  static Future<Void> tenantInit(Vertx vertx, VertxTestContext context) {
    TenantAPI tenantAPI = new CopyCatInit();
    Map<String, String> headers = new CaseInsensitiveMap();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    Promise<Void> promise = Promise.promise();
    tenantAPI.postTenantSync(new TenantAttributes(), headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(204);
      promise.complete();
    })), vertx.getOrCreateContext());
    return promise.future();
  }

  @Test
  void testEmptyProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap();
    headers.put(XOkapiHeaders.TENANT, tenant);

    api.getCopycatProfiles(0, 0, null, headers, context.succeeding(res -> context.verify(() -> {
      CopyCatCollection col = (CopyCatCollection) res.getEntity();
      assertThat(col.getTotalRecords()).isZero();
      context.completeNow();
    })), vertx.getOrCreateContext());
  }

  @Test
  void testGetTableWithoutSchema(Vertx vertx, VertxTestContext context) {
    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    PostgresClient postgresClient = PgUtil.postgresClient(vertx.getOrCreateContext(), headers);
    postgresClient
      .execute("SELECT jsonb FROM profile")
      .onComplete(context.succeeding(res -> context.verify(() -> context.completeNow())));
  }

  @Test
  void testAddProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);

    CopyCatProfile copycatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("@attr 1=12 $identifier");
    Context vertxContext = vertx.getOrCreateContext();
    api.postCopycatProfiles(copycatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String id = responseProfile.getId();
      api.getCopycatProfiles(0, 0, null, headers, context.succeeding(res2 -> context.verify(() -> {
        assertThat(res2.getStatus()).isEqualTo(200);
        CopyCatCollection col = (CopyCatCollection) res2.getEntity();
        assertThat(col.getTotalRecords()).isEqualTo(1);
        api.getCopycatProfilesById(id, headers, context.succeeding(res3 -> context.verify(() -> {
          assertThat(res3.getStatus()).isEqualTo(200);
          api.putCopycatProfilesById(id, copycatProfile, headers, context.succeeding(res4 -> context.verify(() -> {
            assertThat(res4.getStatus()).isEqualTo(204);
            api.deleteCopycatProfilesById(id, headers, context.succeeding(res5 -> context.verify(() -> {
              assertThat(res5.getStatus()).isEqualTo(204);
              context.completeNow();
            })), vertxContext);
          })), vertxContext);
        })), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileNoProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    String profileId = UUID.randomUUID().toString();
    CopyCatImports copyCatImports = new CopyCatImports()
        .withProfileId(profileId)
        .withExternalIdentifier(EXTERNAL_ID_INDEXDATA);
    api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
      assertThat(res.getStatus()).isEqualTo(400);
      Errors errors = (Errors) res.getEntity();
      assertThat(errors.getErrors().get(0).getMessage()).contains("No such profileId " + profileId);
      context.completeNow();
    })), vertxContext);
  }

  @Test
  void testImportProfileOK(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(200);
        CopyCatImports importResposne = (CopyCatImports) res.getEntity();
        assertThat(importResposne.getInternalIdentifier()).isEqualTo(mock.getInstanceId());
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingTargetUrl(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withExternalIdQueryMap("$identifier");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA);
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().size()).isEqualTo(1);
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("url missing in target profile");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingExternalIdQueryMap(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA);
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA);
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().size()).isEqualTo(1);
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("externalIdQueryMap missing in target profile");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileBadOkapiUrl(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + (mockPort + 1)); // nothing here
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().get(0).getMessage()).contains("Connection refused");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingUserId(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("Missing " + XOkapiHeaders.USER_ID + " header");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingOkapiUrl(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("Missing " + XOkapiHeaders.URL + " header");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileWithInternalIdentifier(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());
    Context vertxContext = vertx.getOrCreateContext();

    // make mock return no source records
    JsonObject obj = new JsonObject().put("sourceRecords", new JsonArray());
    mock.setSourceStorageResponse(obj.encode());

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withInternalIdEmbedPath("999ff$i");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withInternalIdentifier("1234")
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(200);
        CopyCatImports importsResponse = (CopyCatImports) res.getEntity();
        assertThat(importsResponse.getInternalIdentifier()).isEqualTo("1234");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileWithoutInternalIdentifierPollFailure(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());
    Context vertxContext = vertx.getOrCreateContext();

    // make poll fail
    mock.setSourceRecordStorageStatus(404);

    CopyCatProfile copyCatProfile = new CopyCatProfile()
      .withName("index data")
      .withUrl(URL_INDEXDATA)
      .withExternalIdQueryMap("$identifier")
      .withInternalIdEmbedPath("999ff$i");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
        .withProfileId(targetProfileId)
        .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(200);
        CopyCatImports importsResponse = (CopyCatImports) res.getEntity();
        assertThat(importsResponse.getInternalIdentifier()).isNull(); // poll failed so no internalIdentifier
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
          context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingInternalIdEmbedPath(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withInternalIdentifier("1234")
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("Missing internalIdEmbedPath in target profile");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileZeroHits(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withExternalIdentifier("1234"); // gets 0 record(s)
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().size()).isEqualTo(1);
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("No record found");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileNonExistingTargetProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    Context vertxContext = vertx.getOrCreateContext();

    String profileId = "1234";
    CopyCatImports copyCatImports = new CopyCatImports()
        .withProfileId(profileId)
        .withExternalIdentifier("does not matter");
    api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
      assertThat(res.getStatus()).isEqualTo(400);
      Errors errors = (Errors) res.getEntity();
      assertThat(errors.getErrors().size()).isEqualTo(1);
      context.completeNow();
    })), vertxContext);
  }

  @Test
  void testImportProfileRecordJsonOK(Vertx vertx, VertxTestContext context) throws IOException {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());

    Record record = new Record().withJson(file);

    CopyCatProfile copyCatProfile = new CopyCatProfile().withName("local");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withRecord(record);
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(200);
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileRecordJsonBadContent(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    Record record = new Record().withAdditionalProperty("json", "x");

    CopyCatProfile copyCatTargetProfile = new CopyCatProfile().withName("local");
    api.postCopycatProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withRecord(record);
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().size()).isEqualTo(1);
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileRecordMarcOK(Vertx vertx, VertxTestContext context) throws IOException {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    byte[] bytes = getClass().getClassLoader().getResourceAsStream("marc1.marc").readAllBytes();
    String base64String = Base64.getEncoder().encodeToString(bytes);

    Record record = new Record().withMarc(base64String);

    CopyCatProfile copyCatProfile = new CopyCatProfile().withName("local");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withRecord(record);
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(200);
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileRecordMarcBase64Error(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    Record record = new Record().withMarc("ab");

    CopyCatProfile copyCatProfile = new CopyCatProfile().withName("local");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withRecord(record);
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().size()).isEqualTo(1);
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("Premature end of file encountered");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileRecordMarcEmpty(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    Record record = new Record().withMarc("");

    CopyCatProfile copyCatProfile = new CopyCatProfile().withName("local");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withRecord(record);
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().size()).isEqualTo(1);
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("Incomplete/missing MARC record");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileRecordBadType(Vertx vertx, VertxTestContext context) throws IOException {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new CaseInsensitiveMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject jsonRecord = new JsonObject(file);
    Record record = new Record().withAdditionalProperty("badType", jsonRecord);

    CopyCatProfile copyCatProfile = new CopyCatProfile().withName("local");
    api.postCopycatProfiles(copyCatProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatProfile responseProfile = (CopyCatProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withProfileId(targetProfileId)
          .withRecord(record);
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().size()).isEqualTo(1);
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("One of 'json' or 'marc' must be given in record");
        api.deleteCopycatProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testGetLocalMarc6(Vertx vertx, VertxTestContext context) throws IOException {
    // this is marc6.marc and marc6.json from YAZ, but with 010 put at the end because marc4j somehow
    // swaps it..!
    byte [] marc = getClass().getClassLoader().getResourceAsStream("marc6.marc").readAllBytes();
    String expectMarc = new String(getClass().getClassLoader().getResourceAsStream("marc6.json").readAllBytes());

    Record record = new Record().withMarc(Base64.getEncoder().encodeToString(marc));
    CopycatImpl.getLocalRecord(record).onComplete(context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.encodePrettily()).isEqualTo(new JsonObject(expectMarc).encodePrettily());
      context.completeNow();;
    })));
  }

  @Test
  void testGetLocalMarc7(Vertx vertx, VertxTestContext context) throws IOException {
    // this is marc7.marc and marc7.json from YAZ, but with 010 put at the end because marc4j somehow
    // swaps it..!
    byte [] marc = getClass().getClassLoader().getResourceAsStream("marc7.marc").readAllBytes();
    String expectMarc = new String(getClass().getClassLoader().getResourceAsStream("marc7.json").readAllBytes());

    Record record = new Record().withMarc(Base64.getEncoder().encodeToString(marc));
    CopycatImpl.getLocalRecord(record).onComplete(context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.encodePrettily()).isEqualTo(new JsonObject(expectMarc).encodePrettily());
      context.completeNow();;
    })));
  }

  @Test
  void testGetLocalMarc7utf8(Vertx vertx, VertxTestContext context) throws IOException {
    // this is marc7.xml.marc and marc7.json from YAZ, but with 010 put at the end because marc4j somehow
    // swaps it..!
    byte [] marc = getClass().getClassLoader().getResourceAsStream("marc7utf8.marc").readAllBytes();
    String expectMarc = new String(getClass().getClassLoader().getResourceAsStream("marc7utf8.json").readAllBytes());

    Record record = new Record().withMarc(Base64.getEncoder().encodeToString(marc));
    CopycatImpl.getLocalRecord(record).onComplete(context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.encodePrettily()).isEqualTo(new JsonObject(expectMarc).encodePrettily());
      context.completeNow();;
    })));
  }


}
