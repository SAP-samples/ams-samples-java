package customer.cap_java_vh;

import com.sap.cloud.security.ams.api.AttributesProcessor;
import com.sap.cloud.security.ams.api.AuthorizationMode;
import com.sap.cloud.security.ams.api.PolicyAssignmentBuilder;
import com.sap.cloud.security.ams.api.PolicyAssignments;
import com.sap.cloud.security.ams.api.Principal;
import com.sap.cloud.security.spring.token.authentication.AuthenticationToken;
import com.sap.cloud.security.token.Token;
import com.sap.cloud.security.token.TokenClaims;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class App2appAttributesProcessor implements AttributesProcessor {

  private static final Logger logger = LoggerFactory.getLogger(App2appAttributesProcessor.class);

  Set<String> technicalUserApis = Set.of("AMS_ValueHelp");
  Set<String> principalPropagationApis = Set.of("AMS_ValueHelp", "Reader");

  private PolicyAssignments mapApisToPolicyAssignments() {
      PolicyAssignmentBuilder pab = PolicyAssignmentBuilder.create();

      for(String api : technicalUserApis) {
          pab.addPolicyAssignment(
              AuthorizationMode.API_TECHNICAL,
              api,
              "internal." + api);
      }

      for(String api : principalPropagationApis) {
          pab.addPolicyAssignment(
              AuthorizationMode.API_USER,
              api,
              "internal." + api);
      }

      return pab.build();
  }

  @Override
  public void processAttributes(Principal principal) {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      if (authentication instanceof AuthenticationToken) {
          Token token = (Token) authentication.getPrincipal();
          List<String> ias_apis = token.getClaimAsStringList(TokenClaims.IAS_APIS);
          if (ias_apis != null) {
              PolicyAssignments pas = this.mapApisToPolicyAssignments(); // see example above
              principal.setPolicyAssignments(pas);
          }
      }
  }
}