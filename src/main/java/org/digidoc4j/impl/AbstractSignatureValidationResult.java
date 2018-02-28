package org.digidoc4j.impl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.digidoc4j.SignatureValidationResult;
import org.digidoc4j.impl.asic.report.SignatureValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.validation.SignatureQualification;
import eu.europa.esig.dss.validation.policy.rules.Indication;
import eu.europa.esig.dss.validation.policy.rules.SubIndication;
import eu.europa.esig.dss.validation.reports.SimpleReport;

/**
 * Created by Janar Rahumeel (CGI Estonia)
 */
public abstract class AbstractSignatureValidationResult extends AbstractValidationResult implements
    SignatureValidationResult {

  protected List<SignatureValidationReport> reports = new ArrayList<>();
  protected List<SimpleReport> simpleReports = new ArrayList<>();
  protected String report;
  private final Logger log = LoggerFactory.getLogger(AbstractSignatureValidationResult.class);

  /*
   * ACCESSORS
   */

  @Override
  public List<SignatureValidationReport> getReports() { //TODO ASIC specific
    return this.reports;
  }

  @Override
  public Indication getIndication(String signatureId) {
    this.log.info(this.getNotSupportedMessage());
    return null;
  }

  @Override
  public SubIndication getSubIndication(String signatureId) {
    this.log.info(this.getNotSupportedMessage());
    return null;
  }

  @Override
  public SignatureQualification getSignatureQualification(String signatureId) {
    this.log.info(this.getNotSupportedMessage());
    return null;
  }

  @Override
  public void saveXmlReports(Path directory) {
    this.log.info(this.getNotSupportedMessage());
  }

  /*
   * RESTRICTED METHODS
   */

  protected String getNotSupportedMessage() {
    return String.format("Not supported for <%s>", this.getValidationName());
  }

  /*
   * ACCESSORS
   */

  @Override
  public List<SimpleReport> getSimpleReports() {
    return this.simpleReports;
  }

  @Override
  public String getReport() {
    return report;
  }

  public void setReport(String report) {
    this.report = report;
  }

}
