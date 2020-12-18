package org.folio.rest.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class ImporterMock {
  private final Vertx vertx;
  private HttpServer server;
  private static final Logger log = LogManager.getLogger(CopycatAPI.class);
  private int createStatus = 201;
  private int importStatus = 204;
  private int putProfileStatus = 200;

  Set<String> jobs = new TreeSet<>();

  public ImporterMock(Vertx vertx) {
    this.vertx = vertx;
  }

  public void setCreateStatus(int code) {
    createStatus = code;
  }

  public void setImportStatus(int code) {
    importStatus = code;
  }

  public void setPutProfileStatus(int code) {
    putProfileStatus = code;
  }

  public void createJob(RoutingContext ctx) {
    try {
      JsonObject requestBody = ctx.getBodyAsJson();
      String userId = requestBody.getString("userId");
      UUID uuid = UUID.fromString(userId);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      ctx.response().setStatusCode(500);
      ctx.response().end(e.getMessage());
      return;
    }
    JsonObject responseBody = new JsonObject();
    String id = UUID.randomUUID().toString();

    jobs.add(id);
    responseBody.put("parentJobExecutionId", id);

    ctx.response().putHeader("Content-Type", "application/json");
    ctx.response().setStatusCode(createStatus);
    ctx.response().end(responseBody.encode());
  }

  public void putProfile(RoutingContext ctx) {
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
      requestBody = ctx.getBodyAsJson();
      if (!requestBody.containsKey("id")) {
        ctx.response().setStatusCode(400);
        ctx.response().end("Missing id");
        return;
      }
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
      JsonObject recordsMetadata = requestBody.getJsonObject("recordsMetadata");
      if (Boolean.TRUE.equals(recordsMetadata.getBoolean("last"))) {
        jobs.remove(id);
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

  public Future<Void> start(int port) {
    Router router = Router.router(vertx);
    router.postWithRegex("/change-manager/jobExecutions.*").handler(BodyHandler.create());
    router.post("/change-manager/jobExecutions").handler(this::createJob);
    router.postWithRegex("/change-manager/jobExecutions/.*").handler(this::importJob);

    router.putWithRegex("/change-manager/jobExecutions/.*").handler(BodyHandler.create());
    router.putWithRegex("/change-manager/jobExecutions/.*").handler(this::putProfile);

    Promise<Void> promise = Promise.promise();
    vertx.createHttpServer()
        .requestHandler(router)
        .listen(port, x -> {
              if (x.succeeded()) {
                server = x.result();
              }
              promise.handle(x.mapEmpty());
            });
    return promise.future();
  }

  public Future<Void> stop() {
    if (server != null) {
      server.close();
    }
    return Future.succeededFuture();
  }
}
