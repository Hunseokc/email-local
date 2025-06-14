package com.dorazibe02.imap.Auth;

import com.dorazibe02.imap.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAuthCredentialService {
    private final AuthRepository authRepository;
    private final CryptoUtil cryptoUtil;

    public Auth createAuthDetail(String email, String password) throws Exception {
        String encryptPw = cryptoUtil.encrypt(password);

        Auth auth = new Auth();
        auth.setEmail(email);
        auth.setEncryptpw(encryptPw);
        authRepository.save(auth);

        return authRepository.findByEmail(email).get();
    }

    public String getDecryptedPassword(long id) throws Exception {
        Auth auth = authRepository.findById(id).orElseThrow();
        return cryptoUtil.decrypt(auth.getEncryptpw());
    }

    public Auth getById(long id) throws Exception {
        Auth auth = authRepository.findById(id).orElseThrow();
        return auth;
    }
}
