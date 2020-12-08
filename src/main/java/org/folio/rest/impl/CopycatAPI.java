package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import javax.ws.rs.core.Response;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import org.folio.rest.persist.PostgresClient;
import org.yaz4j.Connection;
import org.yaz4j.PrefixQuery;
import org.yaz4j.Query;
import org.yaz4j.Record;
import org.yaz4j.ResultSet;
import org.yaz4j.exception.ZoomException;

public class CopycatAPI implements Copycat {

  static Errors createErrors(String message) {
    List<Error> errors = new LinkedList<>();
    errors.add(new Error().withMessage(message));
    return new Errors().withErrors(errors);
  }

  static Errors createErrors(Throwable throwable) {
    log.warn("Error {}", throwable.getMessage(), throwable);
    return createErrors(throwable.getMessage());
  }

  private static final String PROFILE_TABLE = "targetprofile";
  private static Logger log = LogManager.getLogger(CopycatAPI.class);

  /**
   * Construct yaz4j Query based on external identifier and query mappping.
   * @param profile
   * @param externalId
   * @return Query
   */
  static Query constructQuery(CopyCatTargetProfile profile, String externalId) {
    // assuming the externalId does not have whitespace or include {}"\\ characters
    String pqf = profile.getExternalIdQueryMap().replace("$identifier", externalId);
    return new PrefixQuery(pqf);
  }

  /**
   * Search for identifier and return record.
   * @param profile Target Profile
   * @param externalId
   * @param timeout timeout in seconds before giving up
   * @return record content
   */
  static Future<byte[]> getMARC(CopyCatTargetProfile profile, String externalId, int timeout) {
    Connection conn = new Connection(profile.getUrl(), 0);
    conn.option("timeout", Integer.toString(timeout));
    Query query = constructQuery(profile, externalId);
    try {
      conn.connect();
      ResultSet search = conn.search(query);
      if (search.getHitCount() == 0) {
        return Future.failedFuture("No record found");
      }
      Record record = search.getRecord(0);
      return Future.succeededFuture(record.get("render"));
    } catch (ZoomException e) {
      return Future.failedFuture(e);
    } finally {
      conn.close();
    }
  }

  static Future<byte[]> getMARC(CopyCatTargetProfile profile, String externalId, Context vertxContext) {
    return Future.future(promise0 -> vertxContext.owner().<byte[]>executeBlocking(promise1 ->
            getMARC(profile, externalId, 15).onComplete(record -> promise1.handle(record))
        , result -> promise0.handle(result)));
  }

  @Validate
  @Override
  public void postCopycatImports(CopyCatImports entity, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String targetProfileId = entity.getTargetProfileId();
    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    Future.<JsonObject>future(promise -> postgresClient.getById(PROFILE_TABLE, targetProfileId, promise))
        .compose(res -> {
          final CopyCatTargetProfile targetProfile = res.mapTo(CopyCatTargetProfile.class);
          return getMARC(targetProfile, entity.getExternalIdentifier(), vertxContext);
        })
        .onSuccess(record ->
            asyncResultHandler.handle(Future.succeededFuture(PostCopycatImportsResponse.respond204()))
        )
        .onFailure(cause -> {
          log.error("{}", cause.getMessage(), cause);
          asyncResultHandler.handle(Future.succeededFuture(PostCopycatImportsResponse.respond400WithApplicationJson(createErrors(cause)))
          );
        });
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
