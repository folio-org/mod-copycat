package org.folio.rest.impl;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ImporterMock {
  private final Vertx vertx;
  private HttpServer server;
  private static final Logger log = LogManager.getLogger(CopycatImpl.class);
  private int createStatus = 201;
  private int importStatus = 204;
  private int sourceStorageRecordStorageStatus = 200;
  private int putProfileStatus = 200;
  private final String instanceId = "1234";
  private String sourceRecordStorageResponse;
  private int waitMs = 1;
  private int iteration;
  private String lastJobProfileId;

  Set<String> jobs = new TreeSet<>();

  public ImporterMock(Vertx vertx) {
    this.vertx = vertx;
  }

  public void setIterations(int iterations) {
    this.iteration = iterations;
  }

  public String getInstanceId() {
    return instanceId;
  }

  public void setSourceRecordStorageStatus(int code) {
    sourceStorageRecordStorageStatus = code;
  }

  public void setSourceStorageResponse(String response) {
    this.sourceRecordStorageResponse = response;
  }

  public void setCreateStatus(int code) {
    createStatus = code;
  }

  public void setImportStatus(int code) {
    importStatus = code;
  }

  public void setWaitMs(int ms) {
    waitMs = ms;
  }

  public void setPutProfileStatus(int code) {
    putProfileStatus = code;
  }

  public String getLastJobProfileJobId() {
    return lastJobProfileId;
  }

  public void createJob(RoutingContext ctx) {
    try {
      JsonObject requestBody = ctx.getBodyAsJson();
      String userId = requestBody.getString("userId");
      UUID uuid = UUID.fromString(userId);

      JsonObject responseBody = new JsonObject();
      String id = UUID.randomUUID().toString();

      responseBody.put("parentJobExecutionId", id);
      JsonArray jobExecutions = new JsonArray();
      jobExecutions.add(new JsonObject()
          .put("id", id)
          .put("jobProfileInfo", requestBody.getJsonObject("jobProfileInfo"))
          .put("userId", uuid.toString()));

      responseBody.put("jobExecutions", jobExecutions);
      jobs.add(id);
      vertx.setTimer(waitMs, res -> {
        ctx.response().putHeader("Content-Type", "application/json");
        ctx.response().setStatusCode(createStatus);
        ctx.response().end(responseBody.encode());
      });
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      ctx.response().setStatusCode(500);
      ctx.response().end(e.getMessage());
      return;
    }
  }

  public void putOrDeleteProfile(RoutingContext ctx) {
    JsonObject requestBody;
    try {
      String path = ctx.request().path();
      String [] comp = path.split("/");
      String id = comp[3];
      if (!jobs.contains(id)) {
        ctx.response().setStatusCode(404);
        ctx.response().end("Job not found " + id);
        return;
      }
      if (HttpMethod.DELETE.equals(ctx.request().method())) {
        jobs.remove(id);
        ctx.response().setStatusCode(204);
        ctx.response().end();
        return;
      }
      requestBody = ctx.getBodyAsJson();
      if (!requestBody.containsKey("id")) {
        ctx.response().setStatusCode(400);
        ctx.response().end("Missing id");
        return;
      }
      lastJobProfileId = requestBody.getString("id");
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      ctx.response().setStatusCode(500);
      ctx.response().end(e.getMessage());
      return;
    }
    ctx.response().setStatusCode(putProfileStatus);
    ctx.response().putHeader("Application", "application/json");
    ctx.response().end(requestBody.encode());
  }

  public void importJob(RoutingContext ctx) {
    try {
      String path = ctx.request().path();
      String [] comp = path.split("/");
      String id = comp[3];
      if (!jobs.contains(id)) {
        ctx.response().setStatusCode(404);
        ctx.response().end("Job not found " + id);
        return;
      }
      JsonObject requestBody = ctx.getBodyAsJson();
      if (!requestBody.containsKey("initialRecords")) {
        ctx.response().setStatusCode(400);
        ctx.response().end("Missing initialRecords");
        return;
      }
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      ctx.response().setStatusCode(500);
      ctx.response().end(e.getMessage());
      return;
    }
    ctx.response().setStatusCode(importStatus);
    ctx.response().end();
  }

  public void sourceStorage(RoutingContext ctx) {
    String id = ctx.request().getParam("snapshotId");
    if (!jobs.contains(id)) {
      ctx.response().setStatusCode(404);
      ctx.response().end("Job not found " + id);
      return;
    }
    ctx.response().setStatusCode(sourceStorageRecordStorageStatus);
    if (sourceStorageRecordStorageStatus != 200) {
      ctx.end("Error " + sourceStorageRecordStorageStatus);
      sourceStorageRecordStorageStatus = 200;
      return;
    }
    ctx.response().putHeader("Application", "application/json");
    if (sourceRecordStorageResponse != null) {
      ctx.response().end(sourceRecordStorageResponse);
      sourceRecordStorageResponse = null;
      return;
    }
    JsonObject sourceRecord = new JsonObject()
        .put("recordType", "MARC")
        .put("additionalInfo",
            new JsonObject().put("suppressDiscovery", false));
    if (iteration == 0) {
      sourceRecord.put("externalIdsHolder",
          new JsonObject().put("instanceId", instanceId));
    } else {
      --iteration;
    }

    JsonObject response = new JsonObject()
        .put("sourceRecords",
            new JsonArray()
                .add(sourceRecord));
    ctx.response().end(response.encodePrettily());
  }

  public Future<Void> start(int port) {
    Router router = Router.router(vertx);
    router.postWithRegex("/change-manager/jobExecutions.*").handler(BodyHandler.create());
    router.post("/change-manager/jobExecutions").handler(this::createJob);
    router.postWithRegex("/change-manager/jobExecutions/.*").handler(this::importJob);

    router.putWithRegex("/change-manager/jobExecutions/.*").handler(BodyHandler.create());
    router.putWithRegex("/change-manager/jobExecutions/.*").handler(this::putOrDeleteProfile);
    router.deleteWithRegex("/change-manager/jobExecutions/.*").handler(this::putOrDeleteProfile);

    router.getWithRegex("/source-storage/source-records.*").handler(BodyHandler.create());
    router.getWithRegex("/source-storage/source-records.*").handler(this::sourceStorage);

    return vertx.createHttpServer()
        .requestHandler(router)
        .listen(port)
        .onSuccess(x -> server = x).mapEmpty();
  }

  public Future<Void> stop() {
    if (server == null) {
      return Future.succeededFuture();
    }
    return server.close();
  }
}
