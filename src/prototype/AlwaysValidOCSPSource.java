package prototype;

import eu.europa.ec.markt.dss.DSSUtils;
import eu.europa.ec.markt.dss.exception.DSSException;
import eu.europa.ec.markt.dss.validation102853.CertificateToken;
import eu.europa.ec.markt.dss.validation102853.CommonCertificateSource;
import eu.europa.ec.markt.dss.validation102853.ocsp.OCSPSource;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.ocsp.*;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Date;


public class AlwaysValidOCSPSource implements OCSPSource {

  private static final Logger LOG = LoggerFactory.getLogger(AlwaysValidOCSPSource.class);

  private final PrivateKey privateKey;

  private final X509Certificate signingCert;
  private Date ocspDate = new Date();

  private CertificateStatus expectedResponse = CertificateStatus.GOOD;

  static {

    try {

      Security.addProvider(new BouncyCastleProvider());
    } catch (Throwable e) {
      LOG.error(e.getMessage(), e);
    }
  }

  /**
   * The default constructor for MockConfigurableOCSPSource using "src/test/resources/ocsp.p12" file as OCSP responses source.
   */
  public AlwaysValidOCSPSource() {

    this("ocsp.p12", "password");
  }

  /**
   * The default constructor for MockOCSPSource.
   *
   * @param signerPkcs12Name
   * @param password
   * @throws Exception
   */
  public AlwaysValidOCSPSource(final String signerPkcs12Name, final String password) {

    try {

      final KeyStore keyStore = KeyStore.getInstance("PKCS12");
      final FileInputStream fileInputStream = new FileInputStream(signerPkcs12Name);
      keyStore.load(fileInputStream, password.toCharArray());
      final String alias = keyStore.aliases().nextElement();
      signingCert = (X509Certificate) keyStore.getCertificate(alias);
      privateKey = (PrivateKey) keyStore.getKey(alias, password.toCharArray());
      if (LOG.isTraceEnabled()) {

        final CommonCertificateSource certificateSource = new CommonCertificateSource();
        final CertificateToken certificateToken = certificateSource.addCertificate(signingCert);
        LOG.trace("OCSP mockup with signing certificate:\n" + certificateToken);
      }
    } catch (Exception e) {

      throw new DSSException(e);
    }
  }

  public CertificateStatus getExpectedResponse() {

    return expectedResponse;
  }

  /**
   * This method allows to set the status of the cert to GOOD.
   */
  public void setGoodStatus() {

    this.expectedResponse = CertificateStatus.GOOD;
  }

  /**
   * This method allows to set the status of the cert to UNKNOWN.
   */
  public void setUnknownStatus() {

    this.expectedResponse = new UnknownStatus();
  }

  /**
   * This method allows to set the status of the cert to REVOKED.
   * <p/>
   * unspecified = 0; keyCompromise = 1; cACompromise = 2; affiliationChanged = 3; superseded = 4; cessationOfOperation
   * = 5; certificateHold = 6; // 7 -> unknown removeFromCRL = 8; privilegeWithdrawn = 9; aACompromise = 10;
   *
   * @param revocationDate
   * @param revocationReasonId
   */
  public void setRevokedStatus(final Date revocationDate, final int revocationReasonId) {

    this.expectedResponse = new RevokedStatus(revocationDate, revocationReasonId);
  }

  @Override
  public BasicOCSPResp getOCSPResponse(final X509Certificate cert, final X509Certificate issuerCert) {
    try {

      final BigInteger serialNumber = cert.getSerialNumber();
      final OCSPReq ocspReq = generateOCSPRequest(issuerCert, serialNumber);

      final DigestCalculator digestCalculator = DSSUtils.getSHA1DigestCalculator();
      final BasicOCSPRespBuilder basicOCSPRespBuilder = new JcaBasicOCSPRespBuilder(issuerCert.getPublicKey(), digestCalculator);
      final Extension extension = ocspReq.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
      if (extension != null) {

        basicOCSPRespBuilder.setResponseExtensions(new Extensions(new Extension[]{extension}));
      }
      final Req[] requests = ocspReq.getRequestList();
      for (int ii = 0; ii != requests.length; ii++) {

        final Req req = requests[ii];
        final CertificateID certID = req.getCertID();

        boolean isOK = true;

        if (isOK) {

          basicOCSPRespBuilder.addResponse(certID, CertificateStatus.GOOD, ocspDate, null, null);
        } else {

          Date revocationDate = DSSUtils.getDate(ocspDate, -1);
          basicOCSPRespBuilder.addResponse(certID, new RevokedStatus(revocationDate, CRLReason.privilegeWithdrawn));
        }
      }

      final ContentSigner contentSigner = new JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build(privateKey);
      final X509CertificateHolder x509CertificateHolder = new X509CertificateHolder(issuerCert.getEncoded());
      final X509CertificateHolder[] chain = {x509CertificateHolder};
      BasicOCSPResp basicResp = basicOCSPRespBuilder.build(contentSigner, chain, ocspDate);
      //final OCSPResp ocspResp = new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basicResp);
      return basicResp;
    } catch (OCSPException e) {
      throw new DSSException(e);
    } catch (IOException e) {
      throw new DSSException(e);
    } catch (CertificateEncodingException e) {
      throw new DSSException(e);
    } catch (OperatorCreationException e) {
      throw new DSSException(e);
    }
  }

  public OCSPReq generateOCSPRequest(X509Certificate issuerCert, BigInteger serialNumber) throws DSSException {

    try {

      final DigestCalculator digestCalculator = DSSUtils.getSHA1DigestCalculator();
      // Generate the getFileId for the certificate we are looking for
      CertificateID id = new CertificateID(digestCalculator, new X509CertificateHolder(issuerCert.getEncoded()), serialNumber);

      // basic request generation with nonce
      OCSPReqBuilder ocspGen = new OCSPReqBuilder();

      ocspGen.addRequest(id);

      // create details for nonce extension
      BigInteger nonce = BigInteger.valueOf(ocspDate.getTime());

      Extension ext = new Extension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce, true, new DEROctetString(nonce.toByteArray()));
      ocspGen.setRequestExtensions(new Extensions(new Extension[]{ext}));

      return ocspGen.build();
    } catch (OCSPException e) {
      throw new DSSException(e);
    } catch (IOException e) {
      throw new DSSException(e);
    } catch (CertificateEncodingException e) {
      throw new DSSException(e);
    }
  }

  public void setOcspDate(Date ocspDate) {
    this.ocspDate = ocspDate;
  }
}