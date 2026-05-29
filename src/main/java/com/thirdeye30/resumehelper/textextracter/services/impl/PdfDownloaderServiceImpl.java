package com.thirdeye30.resumehelper.textextracter.services.impl;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;


import com.thirdeye30.resumehelper.textextracter.services.PdfDownloaderService;
import com.thirdeye30.resumehelper.textextracter.utils.CryptoUtils;

import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfDownloaderServiceImpl implements PdfDownloaderService {
	
	private final S3Template s3Template;
	
    @Value("${thirdeye.bucket.name}")
    private String bucketName;
    
    @Override
    public byte[] downloadAndDecrypt(UUID fileKey, String encryptionkey) {
        log.info("Request to download and decrypt file: {}", fileKey);
        
        try {
            Resource resource = s3Template.download(bucketName, fileKey.toString() + ".pdf");
            byte[] encryptedData = StreamUtils.copyToByteArray(resource.getInputStream());
            byte[] decryptedData = CryptoUtils.decrypt(encryptedData, encryptionkey);       
            return decryptedData;
        } catch (Exception e) {
            log.error("Download/Decryption failed for file: {}", fileKey, e);
            return null;
        }
    }
}
