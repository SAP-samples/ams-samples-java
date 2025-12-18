/*
 * SPDX-FileCopyrightText: 2020
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.ams.dcl.client.pdp.Attributes;
import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.config.Environments;
import com.sap.cloud.security.config.ServiceConstants;
import com.sap.cloud.security.json.DefaultJsonObject;
import com.sap.cloud.security.json.JsonObject;
import com.sap.cloud.security.token.Token;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Map;

import static com.sap.cloud.security.token.TokenClaims.*;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@RestController
public class BasicController {

  @Autowired PolicyDecisionPoint policyDecisionPoint;

  @GetMapping(value = "/health")
  public String healthCheck() {
    if (policyDecisionPoint.isReachable()) {
      return "{\"status\":\"UP\"}";
    }
    throw new HttpServerErrorException(SERVICE_UNAVAILABLE, "Policy engine is not reachable.");
  }

  @GetMapping(value = "/authenticate")
  public String secured(@AuthenticationPrincipal Token token) {
    String name =
        token.hasClaim(USER_NAME)
            ? token.getClaimAsString(USER_NAME)
            : token.getClaimAsString(EMAIL);
    return "Congratulation, "
        + name
        + " - You are an authenticated user.<br>"
        + "<br>user_uuid: "
        + token.getClaimAsString(SAP_GLOBAL_USER_ID)
        + "<br>app_tid: "
        + token.getClaimAsString(SAP_GLOBAL_APP_TID);
  }

  @GetMapping(value = "/read")
  public String authorizedRead() {
    Principal principal = Principal.create();
    Attributes attributes = principal.getAttributes().setAction("read");
    boolean isReadAllowed = policyDecisionPoint.allow(attributes);

    if (isReadAllowed) {
      return "Read-protected method called!";
    }
    throw new AccessDeniedException("Principal (" + principal + "') has no permission.");
  }

  // required for the UI
  @GetMapping(value = "/uiurl")
  public String getAmsUiUrl() {
    String iasUrl = Environments.getCurrent().getIasConfiguration().getUrl().toString();
    return iasUrl + "/admin";
  }

  @GetMapping(value = "/authorized")
  public String authorizedView() {
    return "Endpoint access allowed for action 'view'";
  }

  @GetMapping(value = "/technical-communication")
  public String authorizedSystemUser() {
    Attributes attributes =
        Principal.create().getAttributes().setAction("read").setResource("system");
    if (policyDecisionPoint.allow(attributes)) {
      return "Technical user accessed 'system' resources!";
    }
    throw new AccessDeniedException(
        "Technical user isn't allowed to access 'system' resources"); // 403
  }
}
