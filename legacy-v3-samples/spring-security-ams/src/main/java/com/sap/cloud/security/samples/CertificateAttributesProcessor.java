package com.sap.cloud.security.samples;

import com.sap.cloud.security.ams.api.AttributesProcessor;
import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.x509.Certificate;
import com.sap.cloud.security.x509.X509Certificate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.sap.cloud.security.x509.X509Constants.FWD_CLIENT_CERT_HEADER;

public class CertificateAttributesProcessor implements AttributesProcessor {

  @Override
  public void processAttributes(Principal principal) {
    if (principal.getId() == null) {
      HttpServletRequest request =
          ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
      List planPolicies =
          determinePoliciesForServicePlan(request.getHeader(FWD_CLIENT_CERT_HEADER));
      if (planPolicies.isEmpty()) {
        // There should be always a policy per plan given
        // otherwise make sure that no access with default policy permission happens
        throw new InsufficientAuthenticationException(
            "Can't find policies for calling client."); // 401
      }
      principal
          .getAttributes()
          .setPolicies(planPolicies); // sets 0..n policies [AND the default policies]
    }
  }

  private List determinePoliciesForServicePlan(String clientCertHeader) {
    // Instead, get proof tokens with "withPlanDetails=true"
    Certificate clientCert = X509Certificate.newCertificate(clientCertHeader);
    List policies = new ArrayList();
    if (clientCert != null) {

      Map<String, String> subjectDNs = clientCert.getSubjectDNMap();
      if (subjectDNs.containsKey("OU")
          && subjectDNs.get("OU").contains("SAP Cloud Platform Clients")) {
        policies.add("consumerclient.defaultPlan");
      }
    }
    return policies;
  }
}
