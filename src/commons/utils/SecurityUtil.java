package commons.utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.EncryptedPrivateKeyInfo;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import commons.model.Authentication;
import commons.model.Device;

public class SecurityUtil {

	private static final String CIPHER_ALGORITHM = "PBEWithSHA1AndDESede";

	private static final String TEMP_DIRECTION_NAME = "certificates";

	private static final String SSL_KEYSTORE_SECRET = "hkRPusjglo";

	public static SSLSocketFactory getSSLSocketFactory(Device device, Authentication authentication)
	throws GeneralSecurityException, IOException {
	// Given a Device object instance, and an Authentication (a Secret, a combined Private Key + Certificate PEM, and a Password)
	// 1. Extract the Certificate string from the combined PEM
	// 2. Extract the Private Key string from the combined PEM
	// 3. Create the Key Manager/KeyStore by passing the Device object instance, Certificate (PEM), Private Key (PEM) and Secret to 'getKeyManagers' (local method)
	// 4. Create the Trust Manager/TrustStore (by default, a dumb Trust Manager with *no* Truststore whatsover who trusts whatever certificate is provided!) 
		String secret = authentication.getSecret();
		String pem = authentication.getPem();

		String pemCertificate = pem.substring(
			pem.indexOf("-----BEGIN CERTIFICATE-----\n") + "-----BEGIN CERTIFICATE-----\n".length(),
			pem.indexOf("\n-----END CERTIFICATE-----\n"));

		String pemPrivateKey = pem.substring(
			pem.indexOf("-----BEGIN ENCRYPTED PRIVATE KEY-----\n") +
				"-----BEGIN ENCRYPTED PRIVATE KEY-----\n".length(),
			pem.indexOf("\n-----END ENCRYPTED PRIVATE KEY-----\n"));

		KeyManager[] keyManagers = getKeyManagers(device, pemCertificate, pemPrivateKey, secret);
		TrustManager[] trustManagers = getTrustManagers();

		return getSSLSocketFactory(keyManagers, trustManagers);
	}

	private static SSLSocketFactory getSSLSocketFactory(KeyManager[] keyManagers,
		TrustManager[] trustManagers)
	throws GeneralSecurityException, IOException {
	//Given a 'Key Manager' and a 'Trust Manager'...
	//Create an SSL client context (no connection yet) using the Key Manager and Trust Manager)

		SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
		sslContext.init(keyManagers, trustManagers, new java.security.SecureRandom());

		return sslContext.getSocketFactory();
	}

	private static KeyManager[] getKeyManagers(Device device, String pem,
		String encryptedPrivateKey, String secret)
	throws GeneralSecurityException, IOException {
	//Given the 'Device' java object instance, the Certificate (PEM string), the Encrypted Private Key (PEM string) and the Secret (string)...
	// 1. Decrypt the Encrypted Private Key (PEM string) using the Secret (string)
	// 2. Convert (Base64 decode) the Base64 encoded Certificate into its native form (byte stream)
	// 3. Generate an X509 Certificate from the original Base64 encoded Certificate (now in its native form)

		// 1. Decrypt the Encrypted Private Key (PEM string) using the Secret (string)
		PrivateKey privateKey = decryptPrivateKey(encryptedPrivateKey, secret);

		// 2. Convert (Base64 decode) the Base64 encoded Certificate into its native form (byte stream)
		ByteArrayInputStream is = new ByteArrayInputStream(
			Base64.getMimeDecoder().decode(pem.getBytes(Constants.DEFAULT_ENCODING)));

		// 3. Generate an X509 Certificate from the original Base64 encoded Certificate (now in its native form)
		// Close the byte stream (not a file, BTW)
		Certificate certificate;
		try {
			CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
			certificate = certificateFactory.generateCertificate(is);
		}
		finally {
			FileUtil.closeStream(is);
		}

		// 4. Create a directory to store the PKCS12 Keystore
		//   By default, will create a directory in the temp folder
		//   My notes on persisting the keystore:
		//	if you substitute with this line, and run within Eclipse then the directory (named "certifcates" above)
		//	will be created within the Eclipse Workspace Project's folder
		// 	e.g.  C:\Users\i817399\eclipse-workspace\jIoTSamples\certificates
			
		//		Path keystorePath = FileSystems.getDefault().getPath(KEYSTORE_DIRECTORY_NAME);
		//		//Simply print out the absolute path of the Keystpre directory
	        //		Path absolutePath = keystorePath.toAbsolutePath();
	        //		Console.printText("Path to p12 trust store: " + absolutePath.normalize().toString());

		
		Path destination = null;
		try {
			destination = Files.createTempDirectory(TEMP_DIRECTION_NAME);
		}
		catch (IllegalArgumentException | SecurityException | IOException e) {
			throw new IOException("Unable to initialize a destination to store PEM", e);
		}

		//create a new file (named of the Device's Alternate ID, with a .p12 extension) within the directory
		// e.g. "7e76ed6981d436e4.p12"
		File p12KeyStore = new File(destination.toFile(),
			device.getAlternateId().replaceAll(":", "") + ".p12");

		KeyStore keyStore;
		try {
			// Set the new KeyStore type (an empty instance, only residing in memory for now) will be of "PKCS12"
			keyStore = KeyStore.getInstance("PKCS12");
			// Create a new (PKCS12) KeyStore type (an empty instance, only residing in memory for now)
			// (You create an empty keystore by using the 'load' method but pass null instead of an InputStream argument)
			keyStore.load(null, secret.toCharArray());
			try (FileOutputStream p12KeyStoreStream = new FileOutputStream(p12KeyStore)) {

				//POTENTIAL ERROR?  This line is a duplicate of the one two commands from now! (Did he store it too early?)
				//TODO fix
				keyStore.store(p12KeyStoreStream, SSL_KEYSTORE_SECRET.toCharArray());

				// alias, private key, certificate
				keyStore.setKeyEntry("private", privateKey, SSL_KEYSTORE_SECRET.toCharArray(),
					new Certificate[] { certificate });

				//Now...you can write it to disk
				keyStore.store(p12KeyStoreStream, SSL_KEYSTORE_SECRET.toCharArray());
			}

		}
		catch (GeneralSecurityException | IOException e) {
			FileUtil.deletePath(destination);

			throw new KeyManagementException("Unable to initialize P12 key store", e);
		}

		try {
			KeyManagerFactory keyManagerFactory = KeyManagerFactory
				.getInstance(KeyManagerFactory.getDefaultAlgorithm());
			keyManagerFactory.init(keyStore, SSL_KEYSTORE_SECRET.toCharArray());

			return keyManagerFactory.getKeyManagers();
		}
		finally {
			//TODO remove this line to keep the keystore
			FileUtil.deletePath(destination);
		}
	}

	private static PrivateKey decryptPrivateKey(String encryptedPrivateKey, String secret)
	throws GeneralSecurityException, IOException {

		byte[] encodedPrivateKey = Base64.getMimeDecoder()
			.decode(encryptedPrivateKey.getBytes(Constants.DEFAULT_ENCODING));

		EncryptedPrivateKeyInfo encryptPKInfo = new EncryptedPrivateKeyInfo(encodedPrivateKey);
		Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
		PBEKeySpec pbeKeySpec = new PBEKeySpec(secret.toCharArray());
		SecretKeyFactory secretFactory = SecretKeyFactory.getInstance(CIPHER_ALGORITHM);
		Key pbeKey = secretFactory.generateSecret(pbeKeySpec);
		AlgorithmParameters algorithmParameters = encryptPKInfo.getAlgParameters();
		cipher.init(Cipher.DECRYPT_MODE, pbeKey, algorithmParameters);
		KeySpec pkcsKeySpec = encryptPKInfo.getKeySpec(cipher);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");

		return keyFactory.generatePrivate(pkcsKeySpec);
	}

	/*
	 * Do not use in production! This trust manager trusts whatever certificate is provided.
	 * 
	 * When connecting through wss with a broker which uses a self-signed certificate or a
	 * certificate that is not trusted by default, there are two options.
	 * 
	 * 1. Disable host verification. This should only be used for testing. It is not recommended in
	 * productive environments.
	 * 
	 * options.setSocketFactory(getTrustManagers()); // will trust all certificates
	 * 
	 * 2. Add the certificate to your keystore. The default keystore is located in the JRE in <jre
	 * home>/lib/security/cacerts. The certificate can be added with
	 * 
	 * "keytool -import -alias my.broker.com -keystore cacerts -file my.broker.com.pem".
	 * 
	 * It is also possible to point to a custom keystore:
	 * 
	 * Properties properties = new Properties();
	 * properties.setProperty("com.ibm.ssl.trustStore","my.cacerts");
	 * options.setSSLProperties(properties);
	 */
	private static TrustManager[] getTrustManagers() {
		return new TrustManager[] { new X509TrustManager() {
	//Explanation of comments above: The TrustManager is supposed to keep a list of all the server certificates
	//that *this* client trusts.  So, really the IoTCoreService's certificate (actually, its CA) should reside in here
	//But to keep things simple, this trust manager trusts whatever certificate is provided by the IoT Service's server

			@Override
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			@Override
			public void checkClientTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
			throws java.security.cert.CertificateException {
				// empty implementation
			}

			@Override
			public void checkServerTrusted(java.security.cert.X509Certificate[] arg0, String arg1)
			throws java.security.cert.CertificateException {
				// empty implementation
			}

		} };
	}

}
