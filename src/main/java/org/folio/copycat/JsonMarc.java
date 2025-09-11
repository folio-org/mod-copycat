package org.folio.copycat;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class JsonMarc {
  private JsonMarc() {
    throw new UnsupportedOperationException();
  }

  /**
   * Embed value (typically identifier) in a MARC field.
   *
   * @param marc JSON MARC
   * @param marcPath Where to embed.
   * @param value string to embed.
   * @throws IllegalArgumentException for bad syntax of marcPath
   */
  public static void embedPath(JsonObject marc, String marcPath, String value) {
    if (marcPath.length() != 7) {
      throw new IllegalArgumentException("pattern must be exactly 7 characters (3+2+$+subfield");
    }
    if (marcPath.charAt(5) != '$') {
      throw new IllegalArgumentException("Missing $ in marcPath");
    }
    final String tagPattern = marcPath.substring(0, 3);
    String indicatorPattern = marcPath.substring(3, 5);
    indicatorPattern = indicatorPattern.replace('_', ' ');
    final String subFieldPattern = marcPath.substring(6);
    JsonArray ar = marc.getJsonArray("fields");
    int i;
    for (i = 0; i < ar.size(); i++) {
      JsonObject entry = ar.getJsonObject(i);
      // see if we have reached a tag after where we're going to insert..
      int cmp = 0;
      for (String tag : entry.fieldNames()) {
        cmp = tag.compareTo(tagPattern);
      }
      if (cmp > 0) {
        break;
      }
      JsonObject jsonField1 = entry.getJsonObject(tagPattern);
      if (jsonField1 != null) {
        boolean found = true;
        for (int j = 0; j < indicatorPattern.length(); j++) {
          if (!indicatorPattern.substring(j, j + 1).equals(jsonField1.getString("ind" + (j + 1)))) {
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
    // inserting at position i
    ar.add(new JsonObject()); // expand by one dummy
    for (int k = ar.size(); --k != i; ) {
      ar.set(k, ar.getJsonObject(k - 1));
    }
    JsonObject jsonField = new JsonObject();
    for (int j = 0; j < indicatorPattern.length(); j++) {
      jsonField.put("ind" + (j + 1), indicatorPattern.substring(j, j + 1));
    }
    JsonArray subAr = new JsonArray();
    jsonField.put("subfields", subAr);
    subAr.add(new JsonObject().put(subFieldPattern, value));
    ar.set(i, new JsonObject().put(tagPattern, jsonField));
  }
}
