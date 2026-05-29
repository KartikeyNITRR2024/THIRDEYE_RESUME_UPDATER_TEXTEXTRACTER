package com.thirdeye30.resumehelper.textextracter.services;

import java.util.UUID;

public interface PdfDownloaderService {

	byte[] downloadAndDecrypt(UUID fileKey, String encryptionkey);
	
	

}
