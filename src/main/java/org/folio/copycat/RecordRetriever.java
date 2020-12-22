package org.folio.copycat;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.CopyCatTargetProfile;
import org.folio.rest.jaxrs.model.TargetOptions;
import org.yaz4j.Connection;
import org.yaz4j.PrefixQuery;
import org.yaz4j.Query;
import org.yaz4j.Record;
import org.yaz4j.ResultSet;
import org.yaz4j.exception.ZoomException;

public class RecordRetriever {
  private static Logger log = LogManager.getLogger(RecordRetriever.class);

  /**
   * Construct yaz4j Query based on external identifier and query mapping.
   * @param profile target profile
   * @param externalId Identifier to use within query
   * @return Query RPN Query pattern with $identifier being replaced.
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
   * Search and retrieve record.
   * @param profile Target Profile
   * @param externalId record identifier such as ISBN number, OCLC number.
   * @param type render type ("json", "xml", "raw") . See
   *             <a href="https://software.indexdata.com/yaz/doc/zoom.records.html">ZOOM_record_get</a>
   * @return record content
   */
  static Future<byte[]> getRecordAsJsonObject(CopyCatTargetProfile profile, String externalId,
                                              String type) {
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
      log.info("Search {} {}", profile.getUrl(), query);
      ResultSet search = conn.search(query);
      if (search.getHitCount() == 0) {
        return Future.failedFuture("No record found");
      }
      Record record = search.getRecord(0);
      conn.close();
      return Future.succeededFuture(record.get(type));
    } catch (ZoomException e) {
      conn.close();
      return Future.failedFuture(e);
    }
  }

  static Future<JsonObject> getRecordAsJsonObject(CopyCatTargetProfile profile, String externalId) {
    return getRecordAsJsonObject(profile, externalId, "json")
        .map(buf -> new JsonObject(new String(buf)));
  }

  /**
   * Retrieve record as JSON from target.
   * @param profile target profile
   * @param externalId external identifier (such as ISBN, OCLC number)
   * @param vertxContext Vert.x context
   * @return async result with record (failure if no record is found)
   */
  public static Future<JsonObject> getRecordAsJsonObject(CopyCatTargetProfile profile,
                                                         String externalId, Context vertxContext) {
    // execute in separate thread, because getRecordAsJsonObject is a blocking function.
    return Future.future(promise0 -> vertxContext.owner().<JsonObject>executeBlocking(promise1 ->
        getRecordAsJsonObject(profile, externalId)
            .onComplete(record -> promise1.handle(record)), result -> promise0.handle(result)));
  }

}
