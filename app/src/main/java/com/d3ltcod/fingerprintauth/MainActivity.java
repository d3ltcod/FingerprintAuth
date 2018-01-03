package com.d3ltcod.fingerprintauth;

import android.Manifest;
import android.app.KeyguardManager;
import android.content.pm.PackageManager;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;

public class MainActivity extends AppCompatActivity {

    // String variable for the key that we are going to use in our fingerprint auth
    private static final String KEY_NAME = "fingerprintKey";
    private TextView textView;
    private FingerprintManager fingerprintManager;
    private KeyguardManager keyguardManager;
    private KeyStore keyStore;
    private KeyGenerator keyGenerator;
    private Cipher cipher;
    private FingerprintManager.CryptoObject cryptoObject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // We need to verify that the device is running Marshmallow or higher before executing any fingerprint-related code
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //Get an instance of KeyguardManager and FingerprintManager//
            keyguardManager =
                    (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            fingerprintManager =
                    (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);

            textView = (TextView) findViewById(R.id.textview);

            //Check if device has a fingerprint sensor.
            if (!fingerprintManager.isHardwareDetected()) {
                // If a fingerprint sensor isn’t available, then inform the user.
                textView.setText("Your device doesn't support fingerprint authentication");
            }
            //Check if user has granted your app the USE_FINGERPRINT permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.USE_FINGERPRINT) != PackageManager.PERMISSION_GRANTED) {
                // If your app doesn't have this permission, then display information message.
                textView.setText("Please enable the fingerprint permission");
            }

            //Check if user has registered at least one fingerprint
            if (!fingerprintManager.hasEnrolledFingerprints()) {
                // If the user hasn’t configured any fingerprints, then display the following message.
                textView.setText("No fingerprint configured. Please register at least one fingerprint in your device's Settings");
            }

            //Check that the lockscreen is secured
            if (!keyguardManager.isKeyguardSecure()) {
                // If the user hasn’t secured their lockscreen with a PIN password or pattern, then display the following text.
                textView.setText("Please enable lockscreen security in your device's Settings");
            } else {
                try {
                    generateKey();
                } catch (FingerprintException e) {
                    e.printStackTrace();
                }

                if (generateCipher()) {
                    //If the cipher is initialized successfully, then create a CryptoObject instance
                    cryptoObject = new FingerprintManager.CryptoObject(cipher);

                    // Starting the authentication process and processing the authentication process events.
                    FingerprintHandler helper = new FingerprintHandler(this);
                    helper.startFingerprintAuth(fingerprintManager, cryptoObject);
                }
            }
        }
    }

    //Create the generateKey method that we’ll use to gain access to the Android keystore and generate the encryption key

    private void generateKey() throws FingerprintException {
        try {
            // Obtain a reference to the Keystore using the standard Android keystore container identifier (“AndroidKeystore”)
            keyStore = KeyStore.getInstance("AndroidKeyStore");

            //Generate the key
            keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            //Initialize an empty KeyStore
            keyStore.load(null);

            //Initialize the KeyGenerator
            keyGenerator.init(new

                    //Specify the operation(s) this key can be used for
                    KeyGenParameterSpec.Builder(KEY_NAME,
                    KeyProperties.PURPOSE_ENCRYPT |
                            KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)

                    //Configure this key so that the user has to confirm their identity with a fingerprint each time they want to use it
                    .setUserAuthenticationRequired(true)
                    .setEncryptionPaddings(
                            KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());

            //Generate the key
            keyGenerator.generateKey();

        } catch (KeyStoreException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | InvalidAlgorithmParameterException
                | CertificateException
                | IOException exc) {
            exc.printStackTrace();
            throw new FingerprintException(exc);
        }
    }

    //Create generateCipher method that we’ll use to initialize our cipher
    public boolean generateCipher() {
        try {
            //Obtain a cipher instance and configure it with the properties required for fingerprint authentication.
            cipher = Cipher.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES + "/"
                            + KeyProperties.BLOCK_MODE_CBC + "/"
                            + KeyProperties.ENCRYPTION_PADDING_PKCS7);
        } catch (NoSuchAlgorithmException |
                NoSuchPaddingException e) {
            throw new RuntimeException("Failed to get Cipher", e);
        }

        try {
            keyStore.load(null);
            SecretKey key = (SecretKey) keyStore.getKey(KEY_NAME,
                    null);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            //Return true if the cipher has been initialized successfully.
            return true;
        } catch (KeyPermanentlyInvalidatedException e) {

            //Return false if cipher initialization failed.
            return false;
        } catch (KeyStoreException | CertificateException
                | UnrecoverableKeyException | IOException
                | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to init Cipher", e);
        }
    }

    // FingerprintException class
    private class FingerprintException extends Exception {
        public FingerprintException(Exception e) {
            super(e);
        }
    }
}