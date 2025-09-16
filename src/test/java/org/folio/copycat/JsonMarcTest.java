package org.folio.copycat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.testing.UtilityClassTester;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

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

    var exception = assertThrows(IllegalArgumentException.class,
        () ->  JsonMarc.embedPath(marc, "000__$", "id1"));
    assertThat(exception.getMessage()).isEqualTo("pattern must be exactly 7 characters (3+2+$+subfield)");

    exception = assertThrows(IllegalArgumentException.class,
        () ->  JsonMarc.embedPath(marc, "000___a", "id1"));
    assertThat(exception.getMessage()).isEqualTo("Missing $ in marcPath");
  }

  @Test
  void testEmbedPathNoFields() throws IOException {
    var exception = assertThrows(IllegalArgumentException.class,
        () ->  JsonMarc.embedPath(new JsonObject(), "12300$a", "id1"));
    assertThat(exception.getMessage()).isEqualTo("No fields in marc");
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

  @ParameterizedTest
  @MethodSource("embedPathTestProvider")
  void testEmbedPathParameterized(String marcPath, String value, int fieldIndex, String expectedJson) throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);
    JsonMarc.embedPath(marc, marcPath, value);
    JsonArray fields = marc.getJsonArray("fields");
    assertThat(fields.getJsonObject(fields.size() - fieldIndex).encode()).isEqualTo(expectedJson);
  }

  static Stream<Arguments> embedPathTestProvider() {
    return Stream.of(
      Arguments.of("70020$a", "1234", 2, "{\"700\":{\"ind1\":\"2\",\"ind2\":\"0\",\"subfields\":[{\"a\":\"1234\"}]}}"),
      Arguments.of("999_1$a", "1234", 1, "{\"999\":{\"ind1\":\" \",\"ind2\":\"1\",\"subfields\":[{\"a\":\"1234\"}]}}"),
      Arguments.of("70010$a", "1234", 3, "{\"700\":{\"subfields\":[{\"a\":\"1234\"},{\"q\":\"(Joseph Arthur)\"}],\"ind1\":\"1\",\"ind2\":\"0\"}}"),
      Arguments.of("70010$b", "1234", 3, "{\"700\":{\"subfields\":[{\"a\":\"Baird, J. Arthur\"},{\"q\":\"(Joseph Arthur)\"},{\"b\":\"1234\"}],\"ind1\":\"1\",\"ind2\":\"0\"}}")
    );
  }

  @Test
  void testEmbedPathNoSubfields() throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);
    JsonArray fields = marc.getJsonArray("fields");
    // remove subfields from the 700 entry in order to make it invalid
    fields.getJsonObject(fields.size() - 3).getJsonObject("700").remove("subfields");

    var exception = assertThrows(IllegalArgumentException.class,
        () ->  JsonMarc.embedPath(marc, "70010$a", "1234"));
    assertThat(exception.getMessage()).isEqualTo("No subfields in marc");
  }

  @Test
  void testEmbedPathControlfield() throws IOException {
    String file = new String(getClass().getClassLoader().getResourceAsStream("marc1.json").readAllBytes());
    JsonObject marc = new JsonObject(file);
    JsonMarc.embedPath(marc, "00510$a", "1234");
  }

}
