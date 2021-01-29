package org.folio.copycat;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.CopyCatProfile;
import org.folio.rest.jaxrs.model.TargetOptions;
import org.yaz4j.Connection;
import org.yaz4j.PrefixQuery;
import org.yaz4j.Query;
import org.yaz4j.Record;
import org.yaz4j.ResultSet;
import org.yaz4j.exception.ZoomException;

public final class RecordRetriever {
  private static Logger log = LogManager.getLogger(RecordRetriever.class);

  private RecordRetriever() {
    throw new UnsupportedOperationException();
  }

  /**
   * Construct yaz4j Query based on external identifier and query mapping.
   * @param profile target profile
   * @param externalId Identifier to use within query
   * @return Query RPN Query pattern with $identifier being replaced.
   */
  static Query constructQuery(CopyCatProfile profile, String externalId)
      throws ZoomException {
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
  static Future<byte[]> getRecordAsJsonObject(CopyCatProfile profile, String externalId,
                                              String type) {
    if (profile.getUrl() == null) {
      return Future.failedFuture("url missing in target profile");
    }
    if (profile.getExternalIdQueryMap() == null) {
      return Future.failedFuture("externalIdQueryMap missing in target profile");
    }
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
    try {
      Query query = constructQuery(profile, externalId);
      conn.connect();
      log.info("Search {} {}", profile.getUrl(), externalId);
      ResultSet search = conn.search(query);
      Record record = search.getRecord(0);
      if (record == null) {
        return Future.failedFuture("No record found");
      }
      return Future.succeededFuture(record.get(type));
    } catch (ZoomException e) {
      return Future.failedFuture(e);
    } finally {
      conn.close();
    }
  }

  static Future<JsonObject> getRecordAsJsonObject(CopyCatProfile profile, String externalId) {
    // for YAZ, specifying marc8 here really means that it will use either UTF-8 or MARC-8
    // depending on the leader of the MARC record.
    return getRecordAsJsonObject(profile, externalId, "json;charset=marc8")
        .map(buf -> new JsonObject(new String(buf)));
  }

  /**
   * Retrieve record as JSON from target.
   * @param profile target profile
   * @param externalId external identifier (such as ISBN, OCLC number)
   * @param vertxContext Vert.x context
   * @return async result with record (failure if no record is found)
   */
  public static Future<JsonObject> getRecordAsJsonObject(CopyCatProfile profile,
                                                         String externalId, Context vertxContext) {
    // execute in separate thread, because getRecordAsJsonObject is a blocking function.
    return Future.future(promise0 -> vertxContext.owner().executeBlocking(promise1 ->
        getRecordAsJsonObject(profile, externalId)
            .onComplete(promise1), promise0));
  }

}
