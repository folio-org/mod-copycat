package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.resource.interfaces.InitAPI;
import org.yaz4j.Connection;

public class Init implements InitAPI {
  private static Logger log = LogManager.getLogger(Init.class);

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    // Only doing this to check that we can load yaz4j shared object
    log.info("Loading yaz4j");
    Connection con = new Connection("localhost:9999", 0);
    con.close();
    handler.handle(Future.succeededFuture(true));
  }
}
