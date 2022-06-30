package org.folio.copycat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JsonMarcTest {
  private static final Logger log = LogManager.getLogger(JsonMarcTest.class);

  @Test
  void constructor() {
    UtilityClassTester.assertUtilityClass(JsonMarc.class);
  }

  @Test
  void testEmbedPathBadPattern() throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);

    assertThrows(IllegalArgumentException.class,
        () ->  JsonMarc.embedPath(marc, "000__$", "id1"));
    assertThrows(IllegalArgumentException.class,
        () ->  JsonMarc.embedPath(marc, "000___a", "id1"));
  }

  @Test
  void testEmbedPathAtBeginning() throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);
    JsonMarc.embedPath(marc, "000ab$a", "id1");
    JsonArray fields = marc.getJsonArray("fields");
    assertThat(fields.getJsonObject(0).encode()).isEqualTo(
        "{\"000\":{\"ind1\":\"a\",\"ind2\":\"b\",\"subfields\":[{\"a\":\"id1\"}]}}");
    assertThat(fields.getJsonObject(1).fieldNames()).contains("001");
  }

  @Test
  void testEmbedPathAtMiddle() throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);
    JsonMarc.embedPath(marc, "650ab$a", "1234");
    JsonArray fields = marc.getJsonArray("fields");
    assertThat(fields.getJsonObject(fields.size() - 5).fieldNames()).contains("630");
    assertThat(fields.getJsonObject(fields.size() - 4).encode()).isEqualTo(
        "{\"650\":{\"ind1\":\"a\",\"ind2\":\"b\",\"subfields\":[{\"a\":\"1234\"}]}}");
    assertThat(fields.getJsonObject(fields.size() - 3).fieldNames()).contains("700");
  }

  @Test
  void testEmbedPathIndicatorMismatch() throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);

    JsonMarc.embedPath(marc, "70020$a", "1234");
    JsonArray fields = marc.getJsonArray("fields");
    log.info("fields {}", fields.encodePrettily());
    assertThat(fields.getJsonObject(fields.size() - 2).encode()).isEqualTo(
        "{\"700\":{\"ind1\":\"2\",\"ind2\":\"0\",\"subfields\":[{\"a\":\"1234\"}]}}");
  }

  @Test
  void testEmbedPathAtEnd() throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);
    JsonMarc.embedPath(marc, "999_1$a", "1234");
    JsonArray fields = marc.getJsonArray("fields");
    assertThat(fields.getJsonObject(fields.size() - 1).encode()).isEqualTo(
        "{\"999\":{\"ind1\":\" \",\"ind2\":\"1\",\"subfields\":[{\"a\":\"1234\"}]}}");
  }

  @Test
  void testEmbedPathModify() throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);
    JsonMarc.embedPath(marc, "70010$a", "1234");
    JsonArray fields = marc.getJsonArray("fields");
    assertThat(fields.getJsonObject(fields.size() - 3).encode()).isEqualTo(
        "{\"700\":{\"subfields\":[{\"a\":\"Baird, J. Arthur\"},{\"q\":\"(Joseph Arthur)\"},{\"a\":\"1234\"}],\"ind1\":\"1\",\"ind2\":\"0\"}}");
  }

}
