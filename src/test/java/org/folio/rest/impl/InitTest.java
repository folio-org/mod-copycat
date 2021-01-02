package org.folio.rest.impl;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(VertxExtension.class)
public class InitTest {
  @Test
  void testEmptyProfile(Vertx vertx, VertxTestContext context) {
    Init init = new Init();

    init.init(vertx, vertx.getOrCreateContext(), context.succeeding(res -> context.verify(() -> {
      assertThat(res).isTrue();
      context.completeNow();
    })));
  }
}
