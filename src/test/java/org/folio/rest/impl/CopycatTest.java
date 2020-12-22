package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.rest.jaxrs.model.CopyCatImports;
import org.folio.rest.jaxrs.model.CopyCatTargetCollection;
import org.folio.rest.jaxrs.model.CopyCatTargetProfile;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.TenantJob;
import org.folio.rest.jaxrs.resource.Copycat;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class CopycatTest {
  static final String tenant = "testlib";

  private static final String URL_INDEXDATA = "z3950.indexdata.com/marc";
  private static final String EXTERNAL_ID_INDEXDATA = "780306m19009999ohu";
  private static final int mockPort = 9231;
  private static ImporterMock mock;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    mock = new ImporterMock(vertx);
    mock.start(mockPort)
        .compose(x -> tenantInit(vertx, context)).onComplete(context.succeeding(res -> context.completeNow()));
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext context) {
    mock.stop().onComplete(context.succeeding(res -> context.completeNow()));
  }

  static Future<Void> tenantInit(Vertx vertx, VertxTestContext context) {
    TenantAPI tenantAPI = new TenantAPI();
    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    Promise<Void> promise = Promise.promise();
    tenantAPI.postTenant(null, headers, context.succeeding(res1 -> context.verify(() -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      TenantJob job1 = (TenantJob) res1.getEntity();
      tenantAPI.getTenantByOperationId(job1.getId(), 10000, headers, context.succeeding(res2 -> context.verify(() -> {
        TenantJob job2 = (TenantJob) res2.getEntity();
        assertThat(job2.getComplete()).isTrue();
        promise.complete();
      })), vertx.getOrCreateContext());
    })), vertx.getOrCreateContext());
    return promise.future();
  }

  @Test
  void testEmptyProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);

    api.getCopycatTargetProfiles(0, 0, null, headers, context.succeeding(res -> context.verify(() -> {
      CopyCatTargetCollection col = (CopyCatTargetCollection) res.getEntity();
      assertThat(col.getTotalRecords()).isEqualTo(0);
      context.completeNow();
    })), vertx.getOrCreateContext());
  }

  @Test
  void testAddProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);

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
  void testImportProfileNoTargetProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

    Context vertxContext = vertx.getOrCreateContext();

    String targetProfileId = UUID.randomUUID().toString();
    CopyCatImports copyCatImports = new CopyCatImports()
        .withTargetProfileId(targetProfileId)
        .withExternalIdentifier(EXTERNAL_ID_INDEXDATA);
    api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> context.verify(() -> {
      assertThat(res.getStatus()).isEqualTo(400);
      Errors errors = (Errors) res.getEntity();
      assertThat(errors.getErrors().get(0).getMessage()).contains("No such targetProfileId " + targetProfileId);
      context.completeNow();
    })), vertxContext);
  }

  @Test
  void testImportProfileOK(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

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
  void testImportProfileBadOkapiUrl(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + (mockPort + 1)); // nothing here
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());

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
        assertThat(errors.getErrors().get(0).getMessage()).contains("Connection refused");
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 -> context.verify(() ->
            context.completeNow()
        )), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingUserId(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
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
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("Missing " + XOkapiHeaders.USER_ID + " header");
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileMissingOkapiUrl(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
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
        assertThat(errors.getErrors().get(0).getMessage()).isEqualTo("Missing " + XOkapiHeaders.URL + " header");
        api.deleteCopycatTargetProfilesById(targetProfileId, headers, context.succeeding(res3 ->
            context.completeNow()
        ), vertxContext);
      })), vertxContext);
    })), vertxContext);
  }

  @Test
  void testImportProfileWithInternalIdentifier(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());
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
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());
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
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
    headers.put(XOkapiHeaders.URL, "http://localhost:" + mockPort);
    headers.put(XOkapiHeaders.USER_ID, UUID.randomUUID().toString());
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
  void testImportProfileNonExistingTargetProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatImpl();

    Map<String, String> headers = new HashMap<>();
    headers.put(XOkapiHeaders.TENANT, tenant);
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
