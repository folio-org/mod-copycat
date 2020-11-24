package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CopyCatImports;
import org.folio.rest.jaxrs.model.CopyCatTargetCollection;
import org.folio.rest.jaxrs.model.CopyCatTargetProfile;
import org.folio.rest.jaxrs.resource.Copycat;
import org.folio.rest.persist.PgUtil;
import javax.ws.rs.core.Response;
import java.util.Map;

public class CopycatAPI implements Copycat {

  private static final String PROFILE_TABLE = "targetprofile";

  @Validate
  @Override
  public void postCopycatImports(CopyCatImports entity, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    asyncResultHandler.handle(Future.succeededFuture(
        GetCopycatTargetProfilesByIdResponse.respond500WithTextPlain("Not implemented")));
  }

  @Validate
  @Override
  public void postCopycatTargetProfiles(CopyCatTargetProfile entity, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.post(PROFILE_TABLE, entity, okapiHeaders, vertxContext,
        PostCopycatTargetProfilesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getCopycatTargetProfiles(int offset, int limit, String query, Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.get(PROFILE_TABLE, CopyCatTargetProfile.class, CopyCatTargetCollection.class, query, offset, limit,
        okapiHeaders, vertxContext, GetCopycatTargetProfilesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getCopycatTargetProfilesById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.getById(PROFILE_TABLE, CopyCatTargetProfile.class, id, okapiHeaders, vertxContext,
        GetCopycatTargetProfilesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putCopycatTargetProfilesById(String id, CopyCatTargetProfile entity, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.put(PROFILE_TABLE, entity, id, okapiHeaders, vertxContext,
        PutCopycatTargetProfilesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteCopycatTargetProfilesById(String id, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PgUtil.deleteById(PROFILE_TABLE, id, okapiHeaders, vertxContext,
        DeleteCopycatTargetProfilesByIdResponse.class, asyncResultHandler);
  }
}
