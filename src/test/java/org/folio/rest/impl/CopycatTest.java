package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.folio.rest.jaxrs.model.CopyCatImports;
import org.folio.rest.jaxrs.model.CopyCatTargetCollection;
import org.folio.rest.jaxrs.model.CopyCatTargetProfile;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.TargetOptions;
import org.folio.rest.jaxrs.resource.Copycat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.yaz4j.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class CopycatTest {
  static final String tenant = "testlib";

  private static final String URL_INDEXDATA = "z3950.indexdata.com/marc";
  private static final String URL_BAD_TARGET = "z3950.indexdata.com:211/marc";
  private static final String URL_WORLDCAT = "zcat.oclc.org/OLUCWorldCat";
  private static final String EXTERNAL_ID_WORLDCAT = "0679429220";
  private static final String EXTERNAL_ID_INDEXDATA = "780306m19009999ohu";
  private static final int mockPort = 9231;
  private static ImporterMock mock;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    mock = new ImporterMock(vertx);
    mock.start(mockPort).onComplete(context.succeeding(res -> context.completeNow()));
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext context) {
    mock.stop().onComplete(context.succeeding(res -> context.completeNow()));
  }

  @BeforeEach
  void beforeEach(Vertx vertx, VertxTestContext context) {
    tenantInit(vertx, context).onComplete(context.succeeding(res -> context.completeNow()));
  }

  static Future<Void> tenantInit(Vertx vertx, VertxTestContext context) {
    TenantAPI tenantAPI = new TenantAPI();
    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    headers.put("X-Okapi-Url", "http://localhost:" + mockPort);
    return Future.<Response>future(promise ->
        tenantAPI.postTenant(null, headers, promise, vertx.getOrCreateContext())).mapEmpty();
  }

  @Test
  void testEmptyProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);

    api.getCopycatTargetProfiles(0, 0, null, headers, context.succeeding(res -> context.verify(() -> {
      CopyCatTargetCollection col = (CopyCatTargetCollection) res.getEntity();
      assertThat(col.getTotalRecords()).isEqualTo(0);
      context.completeNow();
    })), vertx.getOrCreateContext());
  }

  @Test
  void testAddProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);

    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("@attr 1=12 $identifier");
    Context vertxContext = vertx.getOrCreateContext();
    api.postCopycatTargetProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatTargetProfile responseProfile = (CopyCatTargetProfile) res1.getEntity();
      String id = responseProfile.getId();
      api.getCopycatTargetProfiles(0, 0, null, headers, context.succeeding(res2 -> context.verify(() -> {
        assertThat(res2.getStatus()).isEqualTo(200);
        CopyCatTargetCollection col = (CopyCatTargetCollection) res2.getEntity();
        assertThat(col.getTotalRecords()).isEqualTo(1);
        api.getCopycatTargetProfilesById(id, headers, context.succeeding(res3 -> context.verify(() -> {
          assertThat(res3.getStatus()).isEqualTo(200);
          api.putCopycatTargetProfilesById(id, copyCatTargetProfile, headers, context.succeeding(res4 -> context.verify(() -> {
            assertThat(res4.getStatus()).isEqualTo(204);
            api.deleteCopycatTargetProfilesById(id, headers, context.succeeding(res5 -> context.verify(() -> {
              assertThat(res5.getStatus()).isEqualTo(204);
              context.completeNow();
            })), vertxContext);
          })), vertxContext);
        })), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileOK(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    headers.put("X-Okapi-Url", "http://localhost:" + mockPort);
    headers.put("X-Okapi-User-Id", UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatTargetProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatTargetProfile responseProfile = (CopyCatTargetProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withTargetProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(204);
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingUserId(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    headers.put("X-Okapi-Url", "http://localhost:" + mockPort);
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatTargetProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatTargetProfile responseProfile = (CopyCatTargetProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withTargetProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().get(0).getMessage()).contains("returned 500");
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingOkapiUrl(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatTargetProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatTargetProfile responseProfile = (CopyCatTargetProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withTargetProfileId(targetProfileId)
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().get(0).getMessage()).contains("Invalid url");
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }


  @Test
  void testImportProfileWithInternalIdentifier(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    headers.put("X-Okapi-Url", "http://localhost:" + mockPort);
    headers.put("X-Okapi-User-Id", UUID.randomUUID().toString());
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withInternalIdEmbedPath("999ff$i");
    api.postCopycatTargetProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatTargetProfile responseProfile = (CopyCatTargetProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withTargetProfileId(targetProfileId)
          .withInternalIdentifier("1234")
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(204);
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingInternalIdEmbedPath(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    headers.put("X-Okapi-Url", "http://localhost:" + mockPort);
    headers.put("X-Okapi-User-Id", UUID.randomUUID().toString());
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatTargetProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatTargetProfile responseProfile = (CopyCatTargetProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withTargetProfileId(targetProfileId)
          .withInternalIdentifier("1234")
          .withExternalIdentifier(EXTERNAL_ID_INDEXDATA); // gets 1 record
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("Missing internalIdEmbedPath in target profile");
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileZeroHits(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    headers.put("X-Okapi-Url", "http://localhost:" + mockPort);
    headers.put("X-Okapi-User-Id", UUID.randomUUID().toString());
    Context vertxContext = vertx.getOrCreateContext();

    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");
    api.postCopycatTargetProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatTargetProfile responseProfile = (CopyCatTargetProfile) res1.getEntity();
      String targetProfileId = responseProfile.getId();
      CopyCatImports copyCatImports = new CopyCatImports()
          .withTargetProfileId(targetProfileId)
          .withExternalIdentifier("1234"); // gets 0 record(s)
      api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
        assertThat(res.getStatus()).isEqualTo(400);
        Errors errors = (Errors) res.getEntity();
        assertThat(errors.getErrors().size()).isEqualTo(1);
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("No record found");
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void getJsonMarcOK(Vertx vertx, VertxTestContext context) {
    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");

    CopycatAPI.getMARC(copyCatTargetProfile, EXTERNAL_ID_INDEXDATA, "json")
        .onComplete(context.succeeding(res -> context.verify(() -> {
          JsonObject marc = new JsonObject(new String(res));
          assertThat(marc.getJsonArray("fields").getJsonObject(3)
              .getString("008")).startsWith(EXTERNAL_ID_INDEXDATA);

          context.completeNow();
        })));
  }

  @Test
  void getLineMarcOK(Vertx vertx, VertxTestContext context) {
    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");

    CopycatAPI.getMARC(copyCatTargetProfile, EXTERNAL_ID_INDEXDATA, "render")
        .onComplete(context.succeeding(res -> context.verify(() -> {
          String line = new String(res);
          assertThat(line).contains("008 " + EXTERNAL_ID_INDEXDATA);
          context.completeNow();
        })));
  }

  @Test
  void getSutrsOK(Vertx vertx, VertxTestContext context) {
    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("preferredRecordSyntax", "sutrs")
            .withAdditionalProperty("timeout", 10));

    CopycatAPI.getMARC(copyCatTargetProfile, EXTERNAL_ID_INDEXDATA, "render")
        .onComplete(context.succeeding(res -> context.verify(() -> {
          String sutrs = new String(res);
          assertThat(sutrs).contains("008: " + EXTERNAL_ID_INDEXDATA);
          context.completeNow();
        })));
  }

  @Test
  void getBadTargetOption(Vertx vertx, VertxTestContext context) {
    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("structure", Boolean.TRUE));

    CopycatAPI.getMARC(copyCatTargetProfile, EXTERNAL_ID_INDEXDATA, "render")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage()).isEqualTo("Illegal options type for key structure: class java.lang.Boolean");
          context.completeNow();
        })));
  }

  @Test
  void getMarcBadTarget(Vertx vertx, VertxTestContext context) {
    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl(URL_BAD_TARGET)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("timeout", 1)); // low timeout so we it's not taking too long

    CopycatAPI.getMARC(copyCatTargetProfile, EXTERNAL_ID_INDEXDATA, "json")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage())
              .isEqualTo("Server " + URL_BAD_TARGET + ":0 timed out handling our request");
          context.completeNow();
        })));
  }

  @Test
  void getMarcBadCredentials(Vertx vertx, VertxTestContext context) {
    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("OLUCWorldCat")
        .withAuthentication("foo bar")
        .withUrl(URL_WORLDCAT)
        .withExternalIdQueryMap("@attr 1=7 $identifier");

    CopycatAPI.getMARC(copyCatTargetProfile, EXTERNAL_ID_WORLDCAT, "json")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage())
              .isEqualTo("Server " + URL_WORLDCAT + ":0 rejected our init request");
          context.completeNow();
        })));
  }

  static void testAuth(String auth, String user, String group, String password) {
    Connection conn = new Connection("localhost", 210);

    CopycatAPI.setAuthOptions(conn, auth);
    assertThat(conn.option("user")).isEqualTo(user);
    assertThat(conn.option("group")).isEqualTo(group);
    assertThat(conn.option("password")).isEqualTo(password);
    conn.close();
  }

  @Test
  void testSetAuthOptions() {
    testAuth(null, null, null, null);
    testAuth(" ", "", null, null);
    testAuth(" a ", "a", null, null);
    testAuth("a/b", "a/b", null, null);
    testAuth(" a  b ", "a", null, "b");
    testAuth(" a  b c ", "a", "b", "c");
  }

  @Test
  void testImportProfileNonExistingTargetProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    Context vertxContext = vertx.getOrCreateContext();

    String targetProfileId = "1234";
    CopyCatImports copyCatImports = new CopyCatImports()
        .withTargetProfileId(targetProfileId)
        .withExternalIdentifier("does not matter");
    api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
      assertThat(res.getStatus()).isEqualTo(400);
      Errors errors = (Errors) res.getEntity();
      assertThat(errors.getErrors().size()).isEqualTo(1);
      context.completeNow();
    })), vertxContext);
  }

}
