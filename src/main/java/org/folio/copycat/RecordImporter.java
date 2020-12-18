package org.folio.copycat;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

/**
 * Responsible for importing records.
 *
 * Uses mod-record-source-manager for importing.
 *
 * https://github.com/folio-org/mod-source-record-manager
 * https://github.com/folio-org/mod-source-record-manager/blob/master/descriptors/ModuleDescriptor-template.json
 * https://github.com/folio-org/mod-source-record-manager/blob/master/ramls/change-manager.raml
 * https://github.com/folio-org/data-import-raml-storage/blob/master/schemas/dto/initJobExecutionsRqDto.json
 * https://github.com/folio-org/mod-source-record-manager/blob/master/README.md#data-import-workflow
 */
public class RecordImporter {

  private static Logger log = LogManager.getLogger(RecordImporter.class);
  private final WebClient client;
  private final Map<String, String> okapiHeaders;
  private final String okapiUrl;
  private final String userId;
  private String jobId;

  /**
   * Constructor for importing (can NOT be shared between users/tenants).
   *
   * @param okapiHeaders essential headers for importing
   * @param context Vert.x context
   */
  public RecordImporter(Map<String, String> okapiHeaders, Context context) {
    client = WebClient.create(context.owner());
    this.okapiUrl = okapiHeaders.get("X-Okapi-Url");
    this.userId = okapiHeaders.get("X-Okapi-User-Id");
    this.okapiHeaders = okapiHeaders;
  }

  Future<String> createJob() {
    String abs = okapiUrl + "/change-manager/jobExecutions";
    HttpRequest<Buffer> request = client.postAbs(abs);
    request.headers().addAll(okapiHeaders);
    request.putHeader("Accept", "*/*");
    request.putHeader("Content-Type", "application/json");
    JsonObject jobProfileInfo = new JsonObject();
    jobProfileInfo.put("id", "c8f98545-898c-4f48-a494-3ab6736a3243");
    jobProfileInfo.put("name", "Default job profile");
    jobProfileInfo.put("dataType", "MARC");

    JsonObject initJob = new JsonObject();
    initJob.put("userId", userId);
    initJob.put("sourceType", "ONLINE");
    initJob.put("jobProfileInfo", jobProfileInfo);
    log.info("createJob with {}", initJob.encode());
    // With Vert.x 4 this may be simpler
    Promise<String> promise = Promise.promise();
    request.sendJsonObject(initJob, res -> {
      if (res.failed()) {
        log.error(res.cause().getMessage(), res.cause());
        promise.fail(res.cause());
        return;
      }
      HttpResponse<Buffer> result = res.result();
      if (result.statusCode() != 201) {
        log.error("{} returned {}", abs, result.statusCode());
        promise.fail(abs + " returned " + result.statusCode() + " (expected 201):" + result.bodyAsString());
        return;
      }
      promise.complete(result.bodyAsJsonObject().getString("parentJobExecutionId"));
    });
    return promise.future();
  }

  /**
   * begin importing for current user/tenant.
   *
   * @return async result.
   */
  public Future<Void> begin() {
    return createJob().compose(id -> {
      jobId = id;
      return putJobProfile();
    });
  }

  Future<Void> putJobProfile() {
    String abs = okapiUrl + "/change-manager/jobExecutions/" + jobId + "/jobProfile";
    HttpRequest<Buffer> request = client.putAbs(abs);
    request.headers().addAll(okapiHeaders);
    request.putHeader("Accept", "*/*");
    request.putHeader("Content-Type", "application/json");

    JsonObject jobProfile = new JsonObject();
    jobProfile.put("id", "d0ebb7b0-2f0f-11eb-adc1-0242ac120002");
    jobProfile.put("name", "CLI Create MARC Bibs and Instances");
    jobProfile.put("dataType", "MARC");

    // With Vert.x 4 this may be simpler
    Promise<Void> promise = Promise.promise();
    request.sendJsonObject(jobProfile, res -> {
      if (res.failed()) {
        log.error(res.cause().getMessage(), res.cause());
        promise.fail(res.cause());
        return;
      }
      HttpResponse<Buffer> result = res.result();
      if (result.statusCode() != 200) {
        log.error("{} returned {}", abs, result.statusCode());
        promise.fail(abs  + " returned " + result.statusCode() + " (expected 200):" + result.bodyAsString());
        return;
      }
      promise.complete();
    });
    return promise.future();
  }

  Future<Void> post(JsonObject record, boolean last) {
    String abs = okapiUrl + "/change-manager/jobExecutions/" + jobId + "/records";
    HttpRequest<Buffer> request = client.postAbs(abs);
    request.headers().addAll(okapiHeaders);
    request.putHeader("Accept", "*/*");
    request.putHeader("Content-Type", "application/json");

    JsonObject recordsMetadata = new JsonObject();
    recordsMetadata.put("last", last);
    recordsMetadata.put("contentType", "MARC_JSON");
    recordsMetadata.put("counter", 1);
    recordsMetadata.put("total", 1);

    JsonArray initialRecords = new JsonArray();
    if (record != null) {
      initialRecords.add(new JsonObject().put("record", record.encodePrettily()));
    }
    JsonObject rawRecordsDto = new JsonObject();
    rawRecordsDto.put("recordsMetadata", recordsMetadata);
    rawRecordsDto.put("initialRecords", initialRecords);
    // With Vert.x 4 this may be simpler
    Promise<Void> promise = Promise.promise();
    request.sendJsonObject(rawRecordsDto, res -> {
      if (res.failed()) {
        log.error(res.cause().getMessage(), res.cause());
        promise.fail(res.cause());
        return;
      }
      HttpResponse<Buffer> result = res.result();
      if (result.statusCode() != 204) {
        log.error("{} returned {}", abs, result.statusCode());
        promise.fail(abs  + " returned " + result.statusCode() + " (expected 204):" + result.bodyAsString());
        return;
      }
      promise.complete();
    });
    return promise.future();
  }


  /**
   * post record for importing.
   *
   * @param record record to be imported
   * @return async result.
   */
  public Future<Void> post(JsonObject record) {
    return post(record, false);
  }

  /**
   * end importing.
   *
   * @return async result.
   */
  public Future<Void> end() {
    return post(null, true).onComplete(x -> client.close());
  }
}
