package edu.vanderbilt.cs285.secure_sms;

import java.math.BigInteger;
import java.security.spec.RSAPrivateKeySpec;

//import org.apache.commons.codec.binary.Base64;
import android.os.RemoteException;
import android.util.Base64;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

/**
 * Created by Jay on 12/2/2014.
 */
class SMSHandler {


        private final String TAG = SMSHandler.class.getName();

    private final String SYMMETRIC_KEY = "SymmetricKey";


    public boolean handleMessage(String message, String sender, Context context, Intent i) {
            if(SMSTypeDecoder.isSymmetricKey(message)) {
                handleSymmetricKeyMsg(message, sender, context, i);
            }
            else if(SMSTypeDecoder.isInitKeyExchange(message)) {
                Toast.makeText(context, "received initial key exchange "
                        , Toast.LENGTH_LONG).show();;
                handleKeyExchangeMsg(message, sender, context, i);
            }
            else if(SMSTypeDecoder.isEncryptedMessage(message)) {
                Toast.makeText(context, "received secure text "
                        , Toast.LENGTH_LONG).show();
                handleEncryptedMsg(message, sender, context);
                return true;
            }
            else if(SMSTypeDecoder.isReplyKeyExchange(message)){
                Toast.makeText(context, "received key in return "
                        , Toast.LENGTH_LONG).show();
                handleReplyKeyExchangeMsg(message, sender, context);
                return true;
            }
            else if(SMSTypeDecoder.isEndSession(message)) {
            }
            else {
                Toast.makeText(context, "unrecognized message "
                        , Toast.LENGTH_LONG).show();
            }
        return false;
        }

    private void handleReplyKeyExchangeMsg(String message, String sender, Context context) {
        String contactNum = sender;
        String[] parts = message.substring(SMSTypeDecoder.PREFIX_SIZE).split(" "); // expected structure of the key
        // exchange message:
        if (parts.length == 2) {
            String recipientPubModBase64Str = parts[1];
            String recipientPubExpBase64Str = parts[2];



            SharedPreferences prefs = context.getSharedPreferences(contactNum,
                    Context.MODE_PRIVATE);
            SharedPreferences.Editor prefsEditor = prefs.edit();

            String PREF_PUBLIC_MOD = "PublicModulus";
            prefsEditor.putString(PREF_PUBLIC_MOD, recipientPubModBase64Str);
            String PREF_PUBLIC_EXP = "PublicExponent";
            prefsEditor.putString(PREF_PUBLIC_EXP, recipientPubExpBase64Str);
            prefsEditor.apply();

            try {
                SMSSender smsSender = new SMSSender(sender);
                smsSender.sendSymmetricKey(context);

            } catch (Exception e) {
                // handle exception
            }
        }
    }


    private void handleSymmetricKeyMsg(String message, String sender, Context context, Intent i) {
            String symmetricKeyEncrypted = message.substring(SMSTypeDecoder.PREFIX_SIZE);
            String PREFS = "MyKeys";
            SharedPreferences prefs = context.getSharedPreferences(PREFS,
                    Context.MODE_PRIVATE);

            String DEFAULT_PREF = "";
            String PREF_PRIVATE_MOD = "PrivateModulus";
            String privateMod = prefs.getString(PREF_PRIVATE_MOD, DEFAULT_PREF);
            String PREF_PRIVATE_EXP = "PrivateExponent";
            String privateExp = prefs.getString(PREF_PRIVATE_EXP, DEFAULT_PREF);
            if (!privateMod.equals(DEFAULT_PREF)
                    && !privateExp.equals(DEFAULT_PREF)) {
                byte[] recipientPrivateModBA = Base64.decode(privateMod,
                        Base64.NO_WRAP);
                byte[] recipientPrivateExpBA = Base64.decode(privateExp,
                        Base64.NO_WRAP);
                BigInteger recipientPrivateMod = new BigInteger(
                        recipientPrivateModBA);
                BigInteger recipientPrivateExp = new BigInteger(
                        recipientPrivateExpBA);
                RSAPrivateKeySpec recipientPrivateKeySpec = new RSAPrivateKeySpec(
                        recipientPrivateMod, recipientPrivateExp);
                try {
                    String symmetricKey = EncryptionHelper.decryptBody(symmetricKeyEncrypted, recipientPrivateKeySpec);
                    SymmetricEncryptor.saveSymmetricKey(symmetricKey,context);
                } catch (Exception e) {
                }
            }


        }
    //initiate key exchange
        private void handleKeyExchangeMsg(String message, String sender,
                                          Context context, Intent i) {

            String contactNum = sender;
            String[] parts = message.substring(SMSTypeDecoder.PREFIX_SIZE).split(" "); // expected structure of the key
            // exchange message:
            if (parts.length == 2) {
                String recipientPubModBase64Str = parts[0];
                String recipientPubExpBase64Str = parts[1];



                SharedPreferences prefs = context.getSharedPreferences(contactNum,
                        Context.MODE_PRIVATE);
                SharedPreferences.Editor prefsEditor = prefs.edit();

                String PREF_PUBLIC_MOD = "PublicModulus";
                prefsEditor.putString(PREF_PUBLIC_MOD, recipientPubModBase64Str);
                String PREF_PUBLIC_EXP = "PublicExponent";
                prefsEditor.putString(PREF_PUBLIC_EXP, recipientPubExpBase64Str);
                prefsEditor.apply();


                //reply public key
                try {
                    String pubMod = AsymmetricEncrpytor.getPubMod(AsymmetricEncrpytor.PREFS_MY_KEYS, context);
                    String pubExp = AsymmetricEncrpytor.getPubExp(AsymmetricEncrpytor.PREFS_MY_KEYS, context);
                    SMSSender smsSender = new SMSSender(sender);
                    smsSender.sendReplyKeyExchangeSMS(sender, pubMod + " " + pubExp, context);

                } catch (Exception e) {
                    // handle exception
                }
            }
        }

        private void handleEncryptedMsg(String message, String sender,
                                        Context context) {
            String messageEncrypted = message.substring(SMSTypeDecoder.PREFIX_SIZE);
            String decrypted = "";
            String symmetricKey = SymmetricEncryptor.getRecipientsSymmetricKey(sender, context);
            if(symmetricKey != null)
            {
                try {
                    decrypted = EncryptionHelper.decryptBody(messageEncrypted, symmetricKey);

                    Message thisMsg = new Message(sender, "me", decrypted, true);
                    android.os.Message actMessage = android.os.Message.obtain(null, 1);
                    actMessage.obj = thisMsg;
                    SMSBroadcastReceiver.myMessenger.send(actMessage);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }
    }


