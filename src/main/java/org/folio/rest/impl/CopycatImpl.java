package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.copycat.JsonMarc;
import org.folio.copycat.RecordImporter;
import org.folio.copycat.RecordRetriever;
import org.folio.rest.annotations.Validate;
import org.folio.rest.jaxrs.model.CopyCatImports;
import org.folio.rest.jaxrs.model.CopyCatTargetCollection;
import org.folio.rest.jaxrs.model.CopyCatTargetProfile;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Record;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;

public class CopycatImpl implements org.folio.rest.jaxrs.resource.Copycat {

  static Errors createErrors(String message) {
    List<Error> errors = new LinkedList<>();
    errors.add(new Error().withMessage(message));
    return new Errors().withErrors(errors);
  }

  static Errors createErrors(Throwable throwable) {
    log.warn(throwable.getMessage(), throwable);
    return createErrors(throwable.getMessage());
  }

  private static final String PROFILE_TABLE = "targetprofile";
  private static Logger log = LogManager.getLogger(CopycatImpl.class);

  Future<JsonObject> getLocalRecord(Record record) {
    JsonObject jsonObject = new JsonObject(record.getAdditionalProperties());
    JsonObject json = jsonObject.getJsonObject("json");
    if (json != null) {
      log.info("local JSON record {}", () -> json.encodePrettily());
      return Future.succeededFuture(json);
    }
    return Future.failedFuture("No known record types in payload, got " +
        String.join(", ", jsonObject.fieldNames()));
  }

  @Validate
  @Override
  public void postCopycatImports(CopyCatImports entity, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {

    String targetProfileId = entity.getTargetProfileId();
    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    Future.<JsonObject>future(
        promise -> postgresClient.getById(PROFILE_TABLE, targetProfileId, promise))
        .compose(res -> {
          if (res == null) {
            return Future.failedFuture("No such targetProfileId " + targetProfileId);
          }
          CopyCatTargetProfile targetProfile = res.mapTo(CopyCatTargetProfile.class);
          Record record = entity.getRecord();
          Future<JsonObject> fut = record != null ? getLocalRecord(record)
              :  RecordRetriever.getRecordAsJsonObject(targetProfile,
              entity.getExternalIdentifier(), vertxContext);
          return fut.compose(marc -> {
            if (entity.getInternalIdentifier() != null) {
              String pattern = targetProfile.getInternalIdEmbedPath();
              if (pattern == null) {
                return Future.failedFuture("Missing internalIdEmbedPath in target profile");
              }
              JsonMarc.embedPath(marc, pattern, entity.getInternalIdentifier());
            }
            RecordImporter importer = new RecordImporter(okapiHeaders, vertxContext);
            return importer.begin()
                .compose(x -> importer.post(marc))
                .compose(x -> importer.end());
          });
        })
        .onSuccess(record ->
            asyncResultHandler.handle(
                Future.succeededFuture(PostCopycatImportsResponse.respond204()))
        )
        .onFailure(cause ->
            asyncResultHandler.handle(
                Future.succeededFuture(
                    PostCopycatImportsResponse.respond400WithApplicationJson(createErrors(cause))))
        );
  }

  @Validate
  @Override
  public void postCopycatTargetProfiles(CopyCatTargetProfile entity,
                                        Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {
    PgUtil.post(PROFILE_TABLE, entity, okapiHeaders, vertxContext,
        PostCopycatTargetProfilesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getCopycatTargetProfiles(int offset, int limit, String query,
                                       Map<String, String> okapiHeaders,
                                       Handler<AsyncResult<Response>> asyncResultHandler,
                                       Context vertxContext) {

    PgUtil.get(PROFILE_TABLE, CopyCatTargetProfile.class, CopyCatTargetCollection.class,
        query, offset, limit,  okapiHeaders, vertxContext, GetCopycatTargetProfilesResponse.class,
        asyncResultHandler);
  }

  @Validate
  @Override
  public void getCopycatTargetProfilesById(String id, Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {

    PgUtil.getById(PROFILE_TABLE, CopyCatTargetProfile.class, id, okapiHeaders, vertxContext,
        GetCopycatTargetProfilesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putCopycatTargetProfilesById(String id, CopyCatTargetProfile entity,
                                           Map<String, String> okapiHeaders,
                                           Handler<AsyncResult<Response>> asyncResultHandler,
                                           Context vertxContext) {

    PgUtil.put(PROFILE_TABLE, entity, id, okapiHeaders, vertxContext,
        PutCopycatTargetProfilesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteCopycatTargetProfilesById(String id, Map<String, String> okapiHeaders,
                                              Handler<AsyncResult<Response>> asyncResultHandler,
                                              Context vertxContext) {

    PgUtil.deleteById(PROFILE_TABLE, id, okapiHeaders, vertxContext,
        DeleteCopycatTargetProfilesByIdResponse.class, asyncResultHandler);
  }
}
