package com.sap.cloud.security.samples;

import static com.sap.cloud.security.ams.factory.AmsPolicyDecisionPointFactory.DEFAULT;

import com.sap.cloud.security.ams.dcl.client.pdp.PolicyDecisionPoint;
import com.sap.cloud.security.samples.ztis.mtls.X509SourceSingletonWrapper;
import io.spiffe.exception.BundleNotFoundException;
import io.spiffe.exception.SocketEndpointAddressException;
import io.spiffe.exception.X509SourceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

@WebServlet(ZtisSvidServlet.ENDPOINT)
public class ZtisSvidServlet extends HttpServlet {
  static final long serialVersionUID = 1L;
  static final String ENDPOINT = "/svid";
  final PolicyDecisionPoint policyDecisionPoint;
  final HttpClient httpClient;

  public ZtisSvidServlet()
      throws GeneralSecurityException,
          IOException,
          SocketEndpointAddressException,
          X509SourceException {
    httpClient =
        HttpClient.newBuilder()
            .sslContext(X509SourceSingletonWrapper.getInstance().getSslContextInstance())
            .build();
    policyDecisionPoint = PolicyDecisionPoint.create(DEFAULT, "httpClient", httpClient);
  }

  /**
   * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
   */
  @Override
  protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
      throws IOException {
    response.setContentType("text/plain");
    try {
      writeLine(response, "Certificate chain certificate");
      writeLine(response, "");
      for (final X509Certificate cert :
          X509SourceSingletonWrapper.getInstance().getCertificateChainArray()) {
        writeLine(response, cert.toString());
        if (cert.getSubjectAlternativeNames() != null) {
          writeLine(response, "  subjectAlternativeNames: " + cert.getSubjectAlternativeNames());
        }
        writeLine(response, "");
      }
      writeLine(response, "---");
      writeLine(response, "");
      writeLine(response, "Trust bundle");
      writeLine(response, "");
      writeLine(
          response, X509SourceSingletonWrapper.getInstance().getBundleForTrustDomain().toString());
    } catch (final SocketEndpointAddressException
        | X509SourceException
        | GeneralSecurityException
        | BundleNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  private void writeLine(final HttpServletResponse response, final String string)
      throws IOException {
    response.getWriter().append(string);
    response.getWriter().append("\n");
  }
}
