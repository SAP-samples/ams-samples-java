package com.sap.cloud.security.samples.ztis.config;

import com.sap.cloud.security.config.ClientCertificate;
import com.sap.cloud.security.config.ClientIdentity;

public class SvidClientIdentity extends ClientCertificate {

  /**
   * Represents certificate based client identity that is meant to use an SVID.
   *
   * <p>It always returns true for 'isCertificateBased' independent of the information (key,
   * certificate) available in the environment variables.
   *
   * <p>Additionally, be aware that getCertificate() and getKey() will return null as neither the
   * certificate nor the key are read from the environment variable, but sre handled as part of the
   * SSLContext set in the SvidHttpClientFactory.
   *
   * @param clientIdentity
   */
  public SvidClientIdentity(ClientIdentity clientIdentity) {
    super(clientIdentity.getCertificate(), clientIdentity.getKey(), clientIdentity.getId());
  }

  @Override
  public boolean isCertificateBased() {
    return true;
  }
}
