package com.gigya.android.sample.social;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gigya.android.sdk.GigyaLogger;
import com.gigya.android.sdk.providers.external.IProviderWrapper;
import com.gigya.android.sdk.providers.external.IProviderWrapperCallback;
import com.gigya.android.sdk.ui.HostActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

import java.util.HashMap;
import java.util.Map;

public class GoogleProviderWrapper implements IProviderWrapper {

    final String _clientId = "";
    private static final int RC_SIGN_IN = 0;
    private GoogleSignInClient _googleClient;

    final Context context;

    GoogleProviderWrapper(Context context) {
        this.context = context;
    }

    @Override
    public void login(Context context, final Map<String, Object> params, final IProviderWrapperCallback callback) {
        if (_clientId == null) {
            callback.onFailed("Missing server client id. Check manifest implementation");
            return;
        }
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestServerAuthCode(_clientId)
                .requestEmail()
                .build();
        _googleClient = GoogleSignIn.getClient(context, gso);

        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(context);
        if (account != null) {
            // This option should not happen theoretically because we logout out explicitly.
            final Map<String, Object> loginMap = new HashMap<>();
            loginMap.put("code", account.getServerAuthCode());
            callback.onLogin(loginMap);
            return;
        }

        HostActivity.present(context, new HostActivity.HostActivityLifecycleCallbacks() {

            @Override
            public void onCreate(AppCompatActivity activity, @Nullable Bundle savedInstanceState) {
                Intent signInIntent = _googleClient.getSignInIntent();
                activity.startActivityForResult(signInIntent, RC_SIGN_IN);
            }

            @Override
            public void onActivityResult(AppCompatActivity activity, int requestCode, int resultCode, @Nullable Intent data) {
                if (requestCode == RC_SIGN_IN) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
                    handleSignInResult(params, activity, task, callback);
                }
            }
        });
    }

    private void handleSignInResult(final Map<String, Object> loginParams, AppCompatActivity activity, Task<GoogleSignInAccount> completedTask, final IProviderWrapperCallback callback) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account == null) {
                callback.onFailed("Account unavailable");
            } else {
                /* Fetch server auth code */
                final String authCode = account.getServerAuthCode();
                if (authCode == null) {
                    callback.onFailed("Id token no available");
                } else {
                    final Map<String, Object> loginMap = new HashMap<>();
                    loginMap.put("code", authCode);
                    callback.onLogin(loginMap);
                }
            }
            activity.finish();
        } catch (ApiException e) {
            final int exceptionStatusCode = e.getStatusCode();
            switch (exceptionStatusCode) {
                case GoogleSignInStatusCodes.SIGN_IN_CANCELLED:
                    callback.onCanceled();
                    break;
                case GoogleSignInStatusCodes.SIGN_IN_FAILED:
                default:
                    callback.onFailed(GoogleSignInStatusCodes.getStatusCodeString(exceptionStatusCode));
                    break;
            }
            activity.finish();
        }
    }

    @Override
    public void logout() {
        if (_googleClient == null) {
            if (_clientId == null) {
                GigyaLogger.error("GoogleLoginProvider", "provider client id unavailable for logout");
                return;
            }
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestServerAuthCode(_clientId)
                    .requestEmail()
                    .build();
            _googleClient = GoogleSignIn.getClient(context, gso);
        }
        _googleClient.signOut();
    }
}
