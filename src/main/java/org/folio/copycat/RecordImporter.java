package org.folio.copycat;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;

/**
 * Responsible for importing records. Uses mod-record-source-manager for importing.
 *
 * <p><ul>
 * <li><a href="https://github.com/folio-org/mod-source-record-manager">mod-record-source-manager</a></li>
 * <li><a href="https://github.com/folio-org/mod-source-record-manager/blob/master/descriptors/ModuleDescriptor-template.json">Descriptor</a></li>
 * <li><a href="https://github.com/folio-org/mod-source-record-manager/blob/master/ramls/change-manager.raml">RAML</a></li>
 * <li><a href="https://github.com/folio-org/data-import-raml-storage/blob/master/schemas/dto/">schemas</a></li>
 * <li><a href="https://github.com/folio-org/mod-source-record-manager/blob/master/README.md#data-import-workflow">workflow</a></li>
 * </ul>
 */
public class RecordImporter {

  static final String DEFAULT_JOB_PROFILE_ID =  "c8f98545-898c-4f48-a494-3ab6736a3243";
  private static final int WEBCLIENT_CONNECT_TIMEOUT = 10;
  private static final int WEBCLIENT_IDLE_TIMEOUT = 20;
  private static final int SOURCE_STORAGE_POLL_WAIT = 300;
  private static final int SOURCE_STORAGE_POLL_ITERATIONS = 10;

  private static final Logger log = LogManager.getLogger(RecordImporter.class);
  private final WebClient client;
  private final Map<String, String> okapiHeaders;
  private final String okapiUrl;
  private final String userId;
  private String jobId;
  private final Vertx vertx;
  private int storagePollWait;
  private int storagePollIterations;

  /**
   * Constructor for importing (can NOT be shared between users/tenants).
   * @param okapiHeaders Okapi headers
   * @param context Vert.x. context
   * @param options Options for WebClient used for logic
   */
  public RecordImporter(Map<String, String> okapiHeaders, Context context,
                        WebClientOptions options) {

    if (options == null) {
      options = new WebClientOptions();
      options.setConnectTimeout(WEBCLIENT_CONNECT_TIMEOUT);
      options.setIdleTimeout(WEBCLIENT_IDLE_TIMEOUT);
    }
    vertx = context.owner();
    client = WebClient.create(vertx, options);
    this.okapiUrl = okapiHeaders.get(XOkapiHeaders.URL);
    if (this.okapiUrl == null) {
      throw new IllegalArgumentException("Missing " + XOkapiHeaders.URL + " header");
    }
    this.userId = okapiHeaders.get(XOkapiHeaders.USER_ID);
    if (this.userId == null) {
      throw new IllegalArgumentException("Missing " + XOkapiHeaders.USER_ID + " header");
    }
    this.okapiHeaders = okapiHeaders;
    storagePollWait = SOURCE_STORAGE_POLL_WAIT;
    storagePollIterations = SOURCE_STORAGE_POLL_ITERATIONS;
  }

  /**
   * Constructor for importing (can NOT be shared between users/tenants).
   * @param okapiHeaders essential headers for importing
   * @param context Vert.x context
   */

  public RecordImporter(Map<String, String> okapiHeaders, Context context) {
    this(okapiHeaders, context, null);
  }

  void setStoragePollWait(int ms) {
    storagePollWait = ms;
  }

  void setStoragePollIterations(int cnt) {
    storagePollIterations = cnt;
  }

  Future<String> createJob(String jobProfileId) {
    String abs = okapiUrl + "/change-manager/jobExecutions";
    HttpRequest<Buffer> request = client.postAbs(abs);
    request.headers().addAll(okapiHeaders);
    request.putHeader("Accept", "*/*");
    request.putHeader("Content-Type", "application/json");
    JsonObject jobProfileInfo = new JsonObject();
    if (jobProfileId == null) {
      jobProfileId = DEFAULT_JOB_PROFILE_ID;
    }
    jobProfileInfo.put("id", jobProfileId);
    jobProfileInfo.put("name", "Default job profile");
    jobProfileInfo.put("dataType", "MARC");

    JsonObject initJob = new JsonObject();
    initJob.put("userId", userId);
    initJob.put("sourceType", "ONLINE");
    initJob.put("jobProfileInfo", jobProfileInfo);
    log.info("createJob with {}", initJob.encode());
    return request.sendJsonObject(initJob).compose(result -> {
      if (result.statusCode() != 201) {
        log.error("{} returned {}", abs, result.statusCode());
        return Future.failedFuture(abs + " returned " + result.statusCode()
            + " (expected 201):" + result.bodyAsString());
      }
      return Future.succeededFuture(result.bodyAsJsonObject().getString("parentJobExecutionId"));
    });
  }

  /**
   * begin importing for current user/tenant.
   * @param jobProfileId SRS job profile Id; null for default profile.
   * @return async result.
   */
  public Future<Void> begin(String jobProfileId) {
    return createJob(jobProfileId).compose(id -> {
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

    return request.sendJsonObject(jobProfile).compose(result -> {
      if (result.statusCode() != 200) {
        log.error("{} returned {}", abs, result.statusCode());
        return Future.failedFuture(abs  + " returned " + result.statusCode()
            + " (expected 200):" + result.bodyAsString());
      }
      return Future.succeededFuture();
    });
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
    return request.sendJsonObject(rawRecordsDto).compose(result -> {
      if (result.statusCode() != 204) {
        log.error("{} returned {}", abs, result.statusCode());
        return Future.failedFuture(abs  + " returned " + result.statusCode()
            + " (expected 204):" + result.bodyAsString());
      }
      return Future.succeededFuture();
    });
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

  Future<List<String>> getSourceRecords1() {
    String abs = okapiUrl + "/source-storage/source-records?snapshotId=" + jobId;
    HttpRequest<Buffer> request = client.getAbs(abs);
    request.headers().addAll(okapiHeaders);
    request.putHeader("Accept", "*/*");
    request.putHeader("Content-Type", "application/json");
    return request.send().compose(result -> {
      if (result.statusCode() != 200) {
        log.error("{} returned {}", abs, result.statusCode());
        return Future.failedFuture(abs  + " returned " + result.statusCode()
            + " (expected 200):" + result.bodyAsString());
      }
      log.info("Got 200 OK {} {}", abs, result.bodyAsString());
      try {
        JsonObject obj = result.bodyAsJsonObject();
        JsonArray sourceRecords = obj.getJsonArray("sourceRecords");
        if (sourceRecords == null) {
          return Future.failedFuture("Missing \"sourceRecords\" in response");
        }
        List<String> instances = new LinkedList<>();
        for (int i = 0; i < sourceRecords.size(); i++) {
          JsonObject sourceRecord = sourceRecords.getJsonObject(i);
          JsonObject externalIdsHolder = sourceRecord.getJsonObject("externalIdsHolder");
          if (externalIdsHolder == null) {
            return Future.succeededFuture(null);
          }
          String instanceId = externalIdsHolder.getString("instanceId");
          if (instanceId == null) {
            return Future.succeededFuture(null);
          }
          instances.add(instanceId);
        }
        if (instances.isEmpty()) {
          return Future.succeededFuture(null);
        }
        return Future.succeededFuture(instances);
      } catch (Exception e) {
        log.error(e.getMessage(), e);
        return Future.failedFuture(e);
      }
    });
  }

  Future<List<String>> getSourceRecords(int it) {
    log.info("get source records, iteration {}", it);
    return getSourceRecords1().compose(res -> {
      if (res !=  null) {
        return Future.succeededFuture(res);
      }
      // didn't get the instance identifiers
      if (it >= storagePollIterations) {
        return Future.failedFuture("Did not get any instances after "
            + storagePollIterations + " retries");
      }
      Promise<List<String>> promise = Promise.promise();
      vertx.setTimer(storagePollWait, x -> getSourceRecords(it + 1)
          .onComplete(promise));
      return promise.future();
    });
  }

  /**
   * end importing.
   *
   * @return async result.
   */
  public Future<List<String>> end() {
    return post(null, true)
        .compose(x -> getSourceRecords(1))
        .onComplete(x -> client.close());
  }
}
