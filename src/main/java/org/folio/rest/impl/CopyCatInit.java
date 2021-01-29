package org.folio.rest.impl;

import io.vertx.core.Context;
import io.vertx.core.Future;
import java.util.Map;
import org.folio.rest.jaxrs.model.TenantAttributes;
import org.folio.rest.tools.utils.TenantLoading;

public class CopyCatInit extends TenantAPI {

  @Override
  Future<Integer> loadData(TenantAttributes attributes, String tenantId,
                           Map<String, String> headers, Context vertxContext) {
    return super.loadData(attributes, tenantId, headers, vertxContext).compose(
        num -> new TenantLoading()
            .withKey("loadReference")
            .withLead("reference-data")
            .withPostOnly() // only install this reference data once
            .withAcceptStatus(400) // 400 is returned if id already exists
            .add("profiles", "copycat/profiles")
            .perform(attributes, headers, vertxContext, num));
  }
}
