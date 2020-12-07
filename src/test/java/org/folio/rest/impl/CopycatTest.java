package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.CopyCatImports;
import org.folio.rest.jaxrs.model.CopyCatTargetCollection;
import org.folio.rest.jaxrs.model.CopyCatTargetProfile;
import org.folio.rest.jaxrs.resource.Copycat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class CopycatTest {
  static final String tenant = "testlib";

  Logger log = LogManager.getLogger();

  @BeforeEach
  void beforeClass(Vertx vertx, VertxTestContext context) {
    tenantInit(vertx, context).onComplete(context.succeeding(res -> {
      context.completeNow();
    }));
  }

  static Future<Void> tenantInit(Vertx vertx, VertxTestContext context) {
    TenantAPI tenantAPI = new TenantAPI();
    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    return Future.<Response>future(promise ->
        tenantAPI.postTenant(null, headers, promise, vertx.getOrCreateContext())).mapEmpty();
  }

  @Test
  void testEmptyProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);

    api.getCopycatTargetProfiles(0, 0, null, headers, context.succeeding(res -> {
      CopyCatTargetCollection col = (CopyCatTargetCollection) res.getEntity();
      assertThat(col.getTotalRecords()).isEqualTo(0);
      context.completeNow();
    }), vertx.getOrCreateContext());
  }

  @Test
  void testAddProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);

    CopyCatTargetProfile copyCatTargetProfile = new CopyCatTargetProfile()
        .withName("index data")
        .withUrl("z3950.indexdata.com/marc")
        .withExternalIdQueryMap("@attr 1=12 $identifier");
    Context vertxContext = vertx.getOrCreateContext();
    api.postCopycatTargetProfiles(copyCatTargetProfile, headers, context.succeeding(res1 -> {
      assertThat(res1.getStatus()).isEqualTo(201);
      CopyCatTargetProfile responseProfile = (CopyCatTargetProfile) res1.getEntity();
      String id = responseProfile.getId();
      api.getCopycatTargetProfiles(0, 0, null, headers, context.succeeding(res2 -> {
        assertThat(res2.getStatus()).isEqualTo(200);
        CopyCatTargetCollection col = (CopyCatTargetCollection) res2.getEntity();
        assertThat(col.getTotalRecords()).isEqualTo(1);
        api.getCopycatTargetProfilesById(id, headers, context.succeeding(res3 -> {
          assertThat(res3.getStatus()).isEqualTo(200);
          api.putCopycatTargetProfilesById(id, copyCatTargetProfile, headers, context.succeeding(res4 -> {
            assertThat(res4.getStatus()).isEqualTo(204);
            api.deleteCopycatTargetProfilesById(id, headers, context.succeeding(res5 -> {
              assertThat(res5.getStatus()).isEqualTo(204);
              context.completeNow();
            }), vertxContext);
          }), vertxContext);
        }), vertxContext);
      }), vertxContext);
    }), vertxContext);
  }

  @Test
  void testImportProfile(Vertx vertx, VertxTestContext context) {
    Copycat api = new CopycatAPI();

    Map<String, String> headers = new HashMap<>();
    headers.put("X-Okapi-Tenant", tenant);
    CopyCatImports copyCatImports = new CopyCatImports()
        .withTargetProfileId("target-profile-id")
        .withExternalIdentifier("external-id");
    api.postCopycatImports(copyCatImports, headers, context.succeeding(res -> {
      assertThat(res.getStatus()).isEqualTo(500);
      context.completeNow();
    }), vertx.getOrCreateContext());
  }
}
