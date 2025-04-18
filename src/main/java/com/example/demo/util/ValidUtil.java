package com.example.demo.util;

import com.example.demo.entity.valid.CustomPrivateKey;
import com.example.demo.entity.valid.CustomPublicKey;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.KeyGenerationParameters;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.HexFormat;

@Slf4j
public class ValidUtil {
    public static boolean verifySignature(String signatureHex, String timestamp, byte[] httpBody, byte[] publicKeyBytes) {
        try {
            // 解码签名
            byte[] sig = HexFormat.of().parseHex(signatureHex);
            // 检查签名长度和格式
            if (sig.length != 64 || (sig[63] & 0xE0) != 0) {
                log.warn("Invalid signature format");
                return false;
            }
            // 组成签名体
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            msg.write(timestamp.getBytes(StandardCharsets.UTF_8));
            msg.write(httpBody);
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(publicKeyBytes, 0));
            verifier.update(msg.toByteArray(), 0, msg.size());
            return verifier.verifySignature(sig);
        } catch (Exception e) {
            log.error("验证签名报错：", e);
            return false;
        }
    }

    public static String prepareSeed(String seed) {
        if (seed.length() < 32) seed = seed.repeat(2);
        return seed.substring(0, 32);
    }

    public static KeyPair generateEd25519KeyPair(byte[] seed) {
        Ed25519KeyPairGenerator generator = new Ed25519KeyPairGenerator();
        generator.init(new KeyGenerationParameters(null, 32));
        // 使用种子初始化私钥参数
        Ed25519PrivateKeyParameters privateKeyParams = new Ed25519PrivateKeyParameters(seed, 0);
        // 从私钥参数中提取公钥参数
        Ed25519PublicKeyParameters publicKeyParams = privateKeyParams.generatePublicKey();
        // 将参数转换为字节数组
        byte[] privateKeyBytes = privateKeyParams.getEncoded();
        byte[] publicKeyBytes = publicKeyParams.getEncoded();
        return new KeyPair(
                new CustomPublicKey(publicKeyBytes),
                new CustomPrivateKey(privateKeyBytes)
        );
    }

    public static byte[] signMessage(PrivateKey privateKey, byte[] message) {
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, new Ed25519PrivateKeyParameters(privateKey.getEncoded(), 0));
        signer.update(message, 0, message.length);
        return signer.generateSignature();
    }
}
