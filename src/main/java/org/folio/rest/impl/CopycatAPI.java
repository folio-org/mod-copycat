package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CopyCatImports;
import org.folio.rest.jaxrs.model.CopyCatTargetCollection;
import org.folio.rest.jaxrs.model.CopyCatTargetProfile;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.resource.Copycat;
import org.folio.rest.persist.PgUtil;
import org.yaz4j.Connection;
import org.yaz4j.PrefixQuery;
import org.yaz4j.Query;
import org.yaz4j.ResultSet;
import org.yaz4j.exception.ZoomException;

import javax.ws.rs.core.Response;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CopycatAPI implements Copycat {

  private static final String PROFILE_TABLE = "targetprofile";
  private static Logger log = LogManager.getLogger();
  @Validate
  @Override
  public void postCopycatImports(CopyCatImports entity, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    Connection conn = new Connection("z3950.indexdata.com/marc", 0);
    Query query = new PrefixQuery("780306m19009999ohu");
    try {
      conn.connect();
      ResultSet search = conn.search(query);
      if (search.getHitCount() == 0) {
        List<Error> errors = new LinkedList<>();
        errors.add(new Error().withMessage("No record found"));
        asyncResultHandler.handle(Future.succeededFuture(
            PostCopycatImportsResponse.respond400WithApplicationJson(new Errors().withErrors(errors))));
        return;
      }
      asyncResultHandler.handle(Future.succeededFuture(PostCopycatImportsResponse.respond204()));
    } catch (ZoomException e) {
      e.printStackTrace();
      List<Error> errors = new LinkedList<>();
      log.warn("Error {}", e.getMessage());
      errors.add(new Error().withMessage(e.getMessage()));
      asyncResultHandler.handle(Future.succeededFuture(
          PostCopycatImportsResponse.respond400WithApplicationJson(new Errors().withErrors(errors))));
      return;
    } finally {
      conn.close();
    }
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
