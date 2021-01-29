package org.folio.copycat;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.rest.jaxrs.model.CopyCatProfile;
import org.folio.rest.jaxrs.model.TargetOptions;
import org.folio.rest.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.yaz4j.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class RecordRetrieverTest {
  private static final String URL_INDEXDATA = "z3950.indexdata.com/marc";
  private static final String URL_BAD_TARGET = "z3950.indexdata.com:211/marc";
  private static final String URL_WORLDCAT = "zcat.oclc.org/OLUCWorldCat";
  private static final String EXTERNAL_ID_WORLDCAT = "1188724030";
  private static final String EXTERNAL_ID_INDEXDATA = "780306m19009999ohu";

  @Test
  void constructor() {
    UtilityClassTester.assertUtilityClass(RecordRetriever.class);
  }

  @Test
  void getJsonMarcOK(Vertx vertx, VertxTestContext context) {
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_INDEXDATA, "json")
        .onComplete(context.succeeding(res -> context.verify(() -> {
          JsonObject marc = new JsonObject(new String(res));
          assertThat(marc.getJsonArray("fields").getJsonObject(3)
              .getString("008")).startsWith(EXTERNAL_ID_INDEXDATA);

          context.completeNow();
        })));
  }

  @Test
  void getLineMarcOK(Vertx vertx, VertxTestContext context) {
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_INDEXDATA, "render")
        .onComplete(context.succeeding(res -> context.verify(() -> {
          String line = new String(res);
          assertThat(line).contains("008 " + EXTERNAL_ID_INDEXDATA);
          context.completeNow();
        })));
  }

  @Test
  void getSutrsOK(Vertx vertx, VertxTestContext context) {
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("preferredRecordSyntax", "sutrs")
            .withAdditionalProperty("timeout", 10));

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_INDEXDATA, "render")
        .onComplete(context.succeeding(res -> context.verify(() -> {
          String sutrs = new String(res);
          assertThat(sutrs).contains("008: " + EXTERNAL_ID_INDEXDATA);
          context.completeNow();
        })));
  }

  @Test
  void getBadOption(Vertx vertx, VertxTestContext context) {
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("structure", Boolean.TRUE));

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_INDEXDATA, "render")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage()).isEqualTo("Illegal options type for key structure: class java.lang.Boolean");
          context.completeNow();
        })));
  }

  @Test
  void getMarcBadTarget(Vertx vertx, VertxTestContext context) {
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_BAD_TARGET)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("timeout", 1)); // low timeout so we it's not taking too long

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_INDEXDATA, "json")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage())
              .isEqualTo("Server " + URL_BAD_TARGET + " timed out handling our request");
          context.completeNow();
        })));
  }

  @Test
  void getMarcBadCredentials(Vertx vertx, VertxTestContext context) {
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("OLUCWorldCat")
        .withAuthentication("foo bar")
        .withUrl(URL_WORLDCAT)
        .withExternalIdQueryMap("@attr 1=1211 $identifier");

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_WORLDCAT, "json")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage())
              .isEqualTo("Server " + URL_WORLDCAT + " rejected our init request");
          context.completeNow();
        })));
  }

  static void testAuth(String auth, String user, String group, String password) {
    Connection conn = new Connection("localhost", 210);

    RecordRetriever.setAuthOptions(conn, auth);
    assertThat(conn.option("user")).isEqualTo(user);
    assertThat(conn.option("group")).isEqualTo(group);
    assertThat(conn.option("password")).isEqualTo(password);
    conn.close();
  }

  @Test
  void testSetAuthOptions() {
    testAuth(null, null, null, null);
    testAuth(" ", "", null, null);
    testAuth(" a ", "a", null, null);
    testAuth("a/b", "a/b", null, null);
    testAuth(" a  b ", "a", null, "b");
    testAuth(" a  b c ", "a", "b", "c");
  }

}
