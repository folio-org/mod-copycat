package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
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
import org.folio.rest.jaxrs.model.TargetOptions;
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
    log.warn(throwable.getMessage(), throwable);
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

  static void setAuthOptions(Connection conn, String auth) {
    if (auth != null) {
      String[] s = auth.trim().split("\\s+");
      conn.option("user", s[0]);
      if (s.length == 2) {
        conn.option("password", s[1]);
      } else if (s.length == 3) {
        conn.option("group", s[1]);
        conn.option("password", s[2]);
      }
    }
  }
  /**
   * Search for identifier and return record.
   * @param profile Target Profile
   * @param externalId
   * @param type render type
   * @return record content
   */
  static Future<byte[]> getMARC(CopyCatTargetProfile profile, String externalId, String type) {
    Connection conn = new Connection(profile.getUrl(), 0);
    conn.option("timeout", "15");
    conn.option("preferredRecordSyntax", "usmarc");
    setAuthOptions(conn, profile.getAuthentication());

    TargetOptions targetOptions = profile.getTargetOptions();
    if (targetOptions != null) {
      for (Map.Entry<String, Object> entry : targetOptions.getAdditionalProperties().entrySet()) {
        if (entry.getValue() instanceof String) {
          conn.option(entry.getKey(), (String) entry.getValue());
        } else if (entry.getValue() instanceof Integer) {
          conn.option(entry.getKey(), Integer.toString((Integer) entry.getValue()));
        } else {
          return Future.failedFuture("Illegal options type for key " + entry.getKey()
              + ": " + entry.getValue().getClass());
        }
      }
    }
    Query query = constructQuery(profile, externalId);
    try {
      conn.connect();
      ResultSet search = conn.search(query);
      if (search.getHitCount() == 0) {
        return Future.failedFuture("No record found");
      }
      Record record = search.getRecord(0);
      return Future.succeededFuture(record.get(type));
    } catch (ZoomException e) {
      return Future.failedFuture(e);
    } finally {
      conn.close();
    }
  }

  private static Future<byte[]> getMARC(CopyCatTargetProfile profile, String externalId, Context vertxContext) {
    return Future.future(promise0 -> vertxContext.owner().<byte[]>executeBlocking(promise1 ->
            getMARC(profile, externalId, "json").onComplete(record -> promise1.handle(record))
        , result -> promise0.handle(result)));
  }

  static void embedPath(JsonObject marc, String marcPath, String value) {
    JsonArray ar = marc.getJsonArray("fields");

    final String tagPattern = marcPath.substring(0, 3);
    String indicatorPattern = marcPath.substring(3, 5);
    indicatorPattern = indicatorPattern.replace('_', ' ');

    int di = marcPath.indexOf('$');
    if (di == -1) {
      throw new IllegalArgumentException("Missing $ in marcPath");
    }
    final String subFieldPattern = marcPath.substring(di + 1);
    for (int i = 0; i < ar.size(); i++) {
      JsonObject entry = ar.getJsonObject(i);
      JsonObject jsonField1 = entry.getJsonObject(tagPattern);
      if (jsonField1 != null) {
        boolean found = true;
        for (int j = 0; j < indicatorPattern.length(); j++) {
          if (!indicatorPattern.substring(j, j + 1).equals(jsonField1.getString("ind" + Integer.toString(j + 1)))) {
            found = false;
          }
        }
        if (found) {
          JsonArray subAr = jsonField1.getJsonArray("subfields");
          subAr.add(new JsonObject().put(subFieldPattern, value));
          return;
        }
      }
    }
    JsonObject jsonField = new JsonObject();
    for (int j = 0; j < indicatorPattern.length(); j++) {
      jsonField.put("ind" + Integer.toString(j + 1), indicatorPattern.substring(j, j + 1));
    }
    JsonArray subAr = new JsonArray();
    jsonField.put("subfields", subAr);
    subAr.add(new JsonObject().put(subFieldPattern, value));
    ar.add(new JsonObject().put(tagPattern, jsonField));
  }

  @Validate
  @Override
  public void postCopycatImports(CopyCatImports entity, Map<String, String> okapiHeaders,
                                 Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String targetProfileId = entity.getTargetProfileId();
    PostgresClient postgresClient = PgUtil.postgresClient(vertxContext, okapiHeaders);
    Future.<JsonObject>future(promise -> postgresClient.getById(PROFILE_TABLE, targetProfileId, promise))
        .compose(res -> {
          CopyCatTargetProfile targetProfile = res.mapTo(CopyCatTargetProfile.class);
          return getMARC(targetProfile, entity.getExternalIdentifier(), vertxContext);
        })
        .onSuccess(record ->
            asyncResultHandler.handle(Future.succeededFuture(PostCopycatImportsResponse.respond204()))
        )
        .onFailure(cause ->
            asyncResultHandler.handle(Future.succeededFuture(PostCopycatImportsResponse.respond400WithApplicationJson(createErrors(cause))))
        );
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
