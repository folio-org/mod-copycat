package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
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
import org.folio.rest.jaxrs.model.CopyCatCollection;
import org.folio.rest.jaxrs.model.CopyCatImports;
import org.folio.rest.jaxrs.model.CopyCatProfile;
import org.folio.rest.jaxrs.model.Error;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Record;
import org.folio.rest.persist.PgUtil;
import org.folio.rest.persist.PostgresClient;
import org.marc4j.MarcJsonWriter;
import org.marc4j.MarcStreamReader;
import org.marc4j.converter.impl.AnselToUnicode;
import org.marc4j.marc.Leader;

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

  private static final String PROFILE_TABLE = "profile";
  private static final Logger log = LogManager.getLogger(CopycatImpl.class);

  static Future<JsonObject> getLocalRecord(Record record) {

    try {
      final String json = record.getJson();
      if (json != null) {
        log.info("local JSON record {}", json);
        return Future.succeededFuture(new JsonObject(json));
      }
      final String base64String = record.getMarc();
      if (base64String != null) {
        byte[] bytes = Base64.getDecoder().decode(base64String);
        ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
        MarcStreamReader reader = new MarcStreamReader(stream);
        if (!reader.hasNext()) {
          return Future.failedFuture("Incomplete/missing MARC record");
        }
        org.marc4j.marc.Record marcRecord = reader.next();
        char charCodingScheme = marcRecord.getLeader().getCharCodingScheme();
        if (charCodingScheme == ' ') {
          marcRecord.getLeader().setCharCodingScheme('a');
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MarcJsonWriter writer = new MarcJsonWriter(out, MarcJsonWriter.MARC_IN_JSON);
        if (charCodingScheme == ' ') {
          writer.setConverter(new AnselToUnicode());
        }
        writer.write(marcRecord);
        JsonObject json2 = new JsonObject(out.toString());
        log.info("converted MARC record {}", json2::encodePrettily);
        writer.close();
        return Future.succeededFuture(json2);
      }
      return Future.failedFuture("One of 'json' or 'marc' must be given in record");
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  @Validate
  @Override
  public void postCopycatImports(CopyCatImports entity, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {

    String profileId = entity.getProfileId();
    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    Future.<JsonObject>future(
        promise -> postgresClient.getById(PROFILE_TABLE, profileId, promise))
        .compose(res -> {
          if (res == null) {
            return Future.failedFuture("No such profileId " + profileId);
          }
          CopyCatProfile targetProfile = res.mapTo(CopyCatProfile.class);
          Record record = entity.getRecord();
          Future<JsonObject> fut = record != null
              ? getLocalRecord(record)
              :  RecordRetriever.getRecordAsJsonObject(targetProfile,
              entity.getExternalIdentifier(), vertxContext);
          return fut.compose(marc -> {
            String jobProfile;
            List<String> instances = new LinkedList<>();
            if (entity.getInternalIdentifier() != null) {
              jobProfile = targetProfile.getUpdateJobProfileId();
              String pattern = targetProfile.getInternalIdEmbedPath();
              if (pattern == null) {
                return Future.failedFuture("Missing internalIdEmbedPath in target profile");
              }
              instances.add(entity.getInternalIdentifier());
              log.info("Embedding identifier {} in MARC {}",
                  entity::getInternalIdentifier, () -> pattern);
              JsonMarc.embedPath(marc, pattern, entity.getInternalIdentifier());
            } else {
              jobProfile = targetProfile.getCreateJobProfileId();
            }
            log.info("Importing {}", marc::encodePrettily);
            RecordImporter importer = new RecordImporter(okapiHeaders, vertxContext);
            return importer.begin(jobProfile)
                .compose(x -> importer.post(marc))
                .compose(x -> importer.end(instances));
          });
        })
        .onSuccess(
            instances -> {
              if (!instances.isEmpty()) {
                log.info("Got instance identifiers: {}", String.join(", ", instances));
                entity.setInternalIdentifier(instances.get(0));
              }
              asyncResultHandler.handle(
                  Future.succeededFuture(
                      PostCopycatImportsResponse.respond200WithApplicationJson(entity)));
            })
        .onFailure(cause ->
            asyncResultHandler.handle(
                Future.succeededFuture(
                    PostCopycatImportsResponse.respond400WithApplicationJson(createErrors(cause))))
        );
  }

  @Validate
  @Override
  public void postCopycatProfiles(CopyCatProfile entity,
                                  Map<String, String> okapiHeaders,
                                  Handler<AsyncResult<Response>> asyncResultHandler,
                                  Context vertxContext) {
    PgUtil.post(PROFILE_TABLE, entity, okapiHeaders, vertxContext,
        PostCopycatProfilesResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void getCopycatProfiles(int offset, int limit, String query,
                                 Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler,
                                 Context vertxContext) {

    PgUtil.get(PROFILE_TABLE, CopyCatProfile.class, CopyCatCollection.class,
        query, offset, limit,  okapiHeaders, vertxContext, GetCopycatProfilesResponse.class,
        asyncResultHandler);
  }

  @Validate
  @Override
  public void getCopycatProfilesById(String id, Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {

    PgUtil.getById(PROFILE_TABLE, CopyCatProfile.class, id, okapiHeaders, vertxContext,
        GetCopycatProfilesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void putCopycatProfilesById(String id, CopyCatProfile entity,
                                     Map<String, String> okapiHeaders,
                                     Handler<AsyncResult<Response>> asyncResultHandler,
                                     Context vertxContext) {

    PgUtil.put(PROFILE_TABLE, entity, id, okapiHeaders, vertxContext,
        PutCopycatProfilesByIdResponse.class, asyncResultHandler);
  }

  @Validate
  @Override
  public void deleteCopycatProfilesById(String id, Map<String, String> okapiHeaders,
                                        Handler<AsyncResult<Response>> asyncResultHandler,
                                        Context vertxContext) {

    PgUtil.deleteById(PROFILE_TABLE, id, okapiHeaders, vertxContext,
        DeleteCopycatProfilesByIdResponse.class, asyncResultHandler);
  }
}
