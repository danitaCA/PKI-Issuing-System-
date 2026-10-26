package com.pki.ca.service;

import com.pki.ca.dto.internal.ParsedCsrDto;
import com.pki.ca.exception.BadRequestException;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.pkcs.Attribute;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class CsrParserService {

    private static final Logger log = LoggerFactory.getLogger(CsrParserService.class);
    private static final int MAX_CSR_SIZE_BYTES = 16384; // 16 KB

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Parses a PEM-encoded PKCS#10 CSR, cryptographically verifies its Proof of Possession (PoP),
     * and extracts subject, SANs, key algorithm, bit length, and SHA-256 fingerprint.
     */
    public ParsedCsrDto parseAndVerifyCsr(String csrPem) {
        if (csrPem == null || csrPem.trim().isEmpty()) {
            throw new BadRequestException("CSR PEM content cannot be null or empty", "PKI-40011");
        }

        byte[] rawBytes = csrPem.getBytes(StandardCharsets.UTF_8);
        if (rawBytes.length > MAX_CSR_SIZE_BYTES) {
            throw new BadRequestException("CSR exceeds maximum allowed size of 16KB", "PKI-40011");
        }

        PKCS10CertificationRequest csr = parseCsr(csrPem);
        PublicKey publicKey = extractPublicKey(csr);
        verifyProofOfPossession(csr, publicKey);

        ParsedCsrDto parsedDto = new ParsedCsrDto();
        parsedDto.setPublicKey(publicKey);
        extractKeyDetails(publicKey, parsedDto);
        extractSubjectAndSans(csr, parsedDto);
        computeFingerprint(csr, parsedDto);

        return parsedDto;
    }

    private PKCS10CertificationRequest parseCsr(String csrPem) {
        try (PEMParser pemParser = new PEMParser(new StringReader(csrPem))) {
            Object parsedObject = pemParser.readObject();
            if (parsedObject == null) {
                throw new BadRequestException("No valid PKCS#10 CSR found in input", "PKI-40011");
            }
            if (!(parsedObject instanceof PKCS10CertificationRequest)) {
                throw new BadRequestException("Supplied PEM object is not a PKCS#10 Certificate Request", "PKI-40011");
            }
            return (PKCS10CertificationRequest) parsedObject;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Failed to parse CSR PEM content: {}", e.getMessage());
            throw new BadRequestException("Malformed or unparseable PKCS#10 CSR: " + e.getMessage(), "PKI-40011");
        }
    }

    private PublicKey extractPublicKey(PKCS10CertificationRequest csr) {
        try {
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            return converter.getPublicKey(csr.getSubjectPublicKeyInfo());
        } catch (Exception e) {
            log.warn("Failed to extract public key from CSR: {}", e.getMessage());
            throw new BadRequestException("Unable to extract public key from CSR: " + e.getMessage(), "PKI-40011");
        }
    }

    private void verifyProofOfPossession(PKCS10CertificationRequest csr, PublicKey publicKey) {
        try {
            ContentVerifierProvider verifierProvider = new JcaContentVerifierProviderBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(publicKey);

            if (!csr.isSignatureValid(verifierProvider)) {
                log.warn("CSR signature verification failed for subject: {}", csr.getSubject());
                throw new BadRequestException("Proof-of-possession signature verification failed. Cryptographic signature does not match embedded public key.", "PKI-40012");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Error during CSR signature verification: {}", e.getMessage());
            throw new BadRequestException("CSR signature verification failed (Proof of Possession invalid): " + e.getMessage(), "PKI-40012");
        }
    }

    private void extractKeyDetails(PublicKey publicKey, ParsedCsrDto parsedDto) {
        if (publicKey instanceof RSAPublicKey rsaKey) {
            int bitLength = rsaKey.getModulus().bitLength();
            parsedDto.setPublicKeyAlgorithm("RSA");
            parsedDto.setKeyLength(bitLength);
            parsedDto.setKeySpec("RSA-" + bitLength);
        } else if (publicKey instanceof ECPublicKey ecKey) {
            int fieldSize = ecKey.getParams().getCurve().getField().getFieldSize();
            parsedDto.setPublicKeyAlgorithm("ECDSA");
            parsedDto.setKeyLength(fieldSize);
            parsedDto.setKeySpec("ECDSA-P" + fieldSize);
        } else {
            parsedDto.setPublicKeyAlgorithm(publicKey.getAlgorithm());
            parsedDto.setKeyLength(0);
            parsedDto.setKeySpec(publicKey.getAlgorithm());
        }
    }

    private void extractSubjectAndSans(PKCS10CertificationRequest csr, ParsedCsrDto parsedDto) {
        X500Name subject = csr.getSubject();
        if (subject == null || subject.toString().trim().isEmpty()) {
            throw new BadRequestException("CSR Subject DN cannot be empty", "PKI-40013");
        }
        parsedDto.setSubjectDn(subject.toString());

        List<String> sanList = new ArrayList<>();
        Attribute[] attributes = csr.getAttributes();
        if (attributes != null) {
            for (Attribute attr : attributes) {
                if (attr.getAttrType().equals(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest)) {
                    for (ASN1Encodable encodable : attr.getAttrValues()) {
                        Extensions extensions = Extensions.getInstance(encodable);
                        Extension sanExt = extensions.getExtension(Extension.subjectAlternativeName);
                        if (sanExt != null) {
                            GeneralNames generalNames = GeneralNames.getInstance(sanExt.getParsedValue());
                            for (GeneralName gn : generalNames.getNames()) {
                                int tag = gn.getTagNo();
                                if (tag == GeneralName.dNSName) {
                                    sanList.add("DNS:" + gn.getName().toString());
                                } else if (tag == GeneralName.iPAddress) {
                                    try {
                                        byte[] ipOctets = ((ASN1OctetString) gn.getName()).getOctets();
                                        InetAddress ipAddr = InetAddress.getByAddress(ipOctets);
                                        sanList.add("IP:" + ipAddr.getHostAddress());
                                    } catch (Exception ex) {
                                        sanList.add("IP:" + gn.getName().toString());
                                    }
                                } else if (tag == GeneralName.rfc822Name) {
                                    sanList.add("EMAIL:" + gn.getName().toString());
                                } else if (tag == GeneralName.uniformResourceIdentifier) {
                                    sanList.add("URI:" + gn.getName().toString());
                                }
                            }
                        }
                    }
                }
            }
        }
        parsedDto.setSanEntries(sanList);
    }

    private void computeFingerprint(PKCS10CertificationRequest csr, ParsedCsrDto parsedDto) {
        try {
            byte[] spkiBytes = csr.getSubjectPublicKeyInfo().getEncoded();
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(spkiBytes);
            parsedDto.setPublicKeyFingerprint(HexFormat.of().formatHex(digest));
        } catch (Exception e) {
            log.error("Failed to compute public key fingerprint", e);
            throw new RuntimeException("Failed to compute public key fingerprint: " + e.getMessage(), e);
        }
    }
}
