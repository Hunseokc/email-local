package com.dorazibe02.imap;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class CryptoUtil {

    @Value("${spring.security.encryption.key}")
    private String encryptionKey;

    @Value("${spring.security.encryption.vector}")
    private String initVector;

    private static String staticEncryptionKey;
    private static String staticInitVector;
    private static final String ALGORITHM = "AES/CBC/PKCS5PADDING";

    @PostConstruct
    public void init() {
        if (encryptionKey == null || initVector == null) {
            throw new IllegalStateException("Encryption key or IV could not be loaded from properties.");
        }
        staticEncryptionKey = encryptionKey;
        staticInitVector = initVector;
        System.out.println("Encryption keys loaded from properties successfully.");
    }

    public static String encrypt(String value) throws Exception {
        IvParameterSpec iv = new IvParameterSpec(staticInitVector.getBytes("UTF-8"));
        SecretKeySpec skeySpec = new SecretKeySpec(staticEncryptionKey.getBytes("UTF-8"), "AES");

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec, iv);

        byte[] encrypted = cipher.doFinal(value.getBytes());
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encrypted) throws Exception {
        IvParameterSpec iv = new IvParameterSpec(staticInitVector.getBytes("UTF-8"));
        SecretKeySpec skeySpec = new SecretKeySpec(staticEncryptionKey.getBytes("UTF-8"), "AES");

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, skeySpec, iv);

        byte[] original = cipher.doFinal(Base64.getDecoder().decode(encrypted));
        return new String(original);
    }
}
