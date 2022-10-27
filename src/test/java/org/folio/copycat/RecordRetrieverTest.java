package org.folio.copycat;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.folio.okapi.testing.UtilityClassTester;
import org.folio.rest.jaxrs.model.CopyCatProfile;
import org.folio.rest.jaxrs.model.TargetOptions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.yaz4j.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class RecordRetrieverTest {
  private static final String HOST_INDEXDATA = "z3950.indexdata.com";
  private static final String URL_INDEXDATA = "z3950.indexdata.com/marc";
  private static final String URL_BAD_TARGET = "z3950.indexdata.com:211/marc";
  private static final String URL_WORLDCAT = "zcat.oclc.org/OLUCWorldCat";
  private static final String EXTERNAL_ID_WORLDCAT = "1188724030";
  private static final String EXTERNAL_ID_INDEXDATA = "780306m19009999ohu";

  private static boolean zServerAvailable = false;
  @Test
  void constructor() {
    UtilityClassTester.assertUtilityClass(RecordRetriever.class);
  }


  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext context) {
    Future<Void> f = vertx.createNetClient()
        .connect(210, HOST_INDEXDATA)
        .compose(x -> {
          zServerAvailable = true;
          return x.close();
        }, e -> Future.succeededFuture());
    f.onComplete(context.succeedingThenComplete());
  }

  @Test
  void getJsonMarcOK(Vertx vertx, VertxTestContext context) {
    Assumptions.assumeTrue(zServerAvailable);
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("preferredRecordSyntax", "usmarc"));

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_INDEXDATA)
        .onComplete(context.succeeding(marc -> context.verify(() -> {
          assertThat(marc.getJsonArray("fields").getJsonObject(3)
              .getString("008")).startsWith(EXTERNAL_ID_INDEXDATA);
          context.completeNow();
        })));
  }

  @Test
  void getJsonMarcOKMarcEncoding(Vertx vertx, VertxTestContext context) {
    Assumptions.assumeTrue(zServerAvailable);
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("preferredRecordSyntax", "usmarc")
            .withAdditionalProperty(RecordRetriever.MARCENCODING_PROPERTY, "iso-8859-1"));

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_INDEXDATA)
        .onComplete(context.succeeding(marc -> context.verify(() -> {
          assertThat(marc.getJsonArray("fields").getJsonObject(3)
              .getString("008")).startsWith(EXTERNAL_ID_INDEXDATA);
          context.completeNow();
        })));
  }

  @Test
  void getJsonMarcMarcEncodingNumeric(Vertx vertx, VertxTestContext context) {
    Assumptions.assumeTrue(zServerAvailable);
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty(RecordRetriever.MARCENCODING_PROPERTY, 865));

    RecordRetriever.getRecordAsJsonObject(copyCatProfile, EXTERNAL_ID_INDEXDATA)
        .onComplete(context.succeeding(marc -> context.verify(() -> {
          assertThat(marc.getJsonArray("fields").getJsonObject(3)
              .getString("008")).startsWith(EXTERNAL_ID_INDEXDATA);
          context.completeNow();
        })));
  }

  @Test
  void getLineMarcOK(Vertx vertx, VertxTestContext context) {
    Assumptions.assumeTrue(zServerAvailable);
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier");

    RecordRetriever.getRecordAsBytes(copyCatProfile, EXTERNAL_ID_INDEXDATA, "render")
        .onComplete(context.succeeding(res -> context.verify(() -> {
          String line = new String(res);
          assertThat(line).contains("008 " + EXTERNAL_ID_INDEXDATA);
          context.completeNow();
        })));
  }

  @Test
  void getSutrsOK(Vertx vertx, VertxTestContext context) {
    Assumptions.assumeTrue(zServerAvailable);
    CopyCatProfile copyCatProfile = new CopyCatProfile()
        .withName("index data")
        .withUrl(URL_INDEXDATA)
        .withExternalIdQueryMap("$identifier")
        .withTargetOptions(new TargetOptions()
            .withAdditionalProperty("preferredRecordSyntax", "sutrs")
            .withAdditionalProperty("timeout", 10));

    RecordRetriever.getRecordAsBytes(copyCatProfile, EXTERNAL_ID_INDEXDATA, "render")
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

    RecordRetriever.getRecordAsBytes(copyCatProfile, EXTERNAL_ID_INDEXDATA, "render")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage()).isEqualTo("Illegal options type for key structure: class java.lang.Boolean");
          context.completeNow();
        })));
  }

  @Test
  void getMarcBadUseAttribute(Vertx vertx, VertxTestContext context) {
    Assumptions.assumeTrue(zServerAvailable);
    CopyCatProfile copyCatProfile = new CopyCatProfile()
      .withName("index data")
      .withUrl(URL_INDEXDATA)
      .withExternalIdQueryMap("@attr 1=1211 $identifier");

    RecordRetriever.getRecordAsBytes(copyCatProfile, EXTERNAL_ID_INDEXDATA, "json")
      .onComplete(context.failing(cause -> context.verify(() -> {
        assertThat(cause.getMessage())
          .isEqualTo(
            "Z39.50 error: server z3950.indexdata.com/marc returned diagnostic:"
              + " Bib1Exception: Error Code = 114 (UnsupportedUseAttribute)."
              + " Perhaps the copycat profile is incorrectly configured for this server");
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

    RecordRetriever.getRecordAsBytes(copyCatProfile, EXTERNAL_ID_INDEXDATA, "json")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage())
              .isEqualTo("Z39.50 error: Server " + URL_BAD_TARGET + " timed out handling our request");
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

    RecordRetriever.getRecordAsBytes(copyCatProfile, EXTERNAL_ID_WORLDCAT, "json")
        .onComplete(context.failing(cause -> context.verify(() -> {
          assertThat(cause.getMessage())
              .isEqualTo("Z39.50 error: server " + URL_WORLDCAT + " rejected init."
                + " This may be due to missing or incorrect authentication for the copycat profile"
              );
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
